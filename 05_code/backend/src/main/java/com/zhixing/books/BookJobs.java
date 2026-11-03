package com.zhixing.books;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixing.common.*;
import com.zhixing.service.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Durable queue; paid responses are checkpointed before upload. No automatic retry of ambiguous
 * calls.
 */
@Service
public class BookJobs {
  private final BookStore store;
  private final BookService books;
  private final BookSources sources;
  private final AliyunTtsService tts;
  private final MediaService media;
  private final AiService ai;
  private final BookRemote remote;
  private final AppParameterService parameters;

  @Value("${app.books.worker-enabled:false}")
  private boolean enabled;

  @Value("${app.books.image-endpoint:}")
  private String imageEndpoint;

  @Value("${app.books.image-model:}")
  private String imageModel;

  @Value("${app.books.max-job-cost:30}")
  private BigDecimal maxCost;

  private final String workerId = CryptoUtils.randomId();
  private final AtomicBoolean busy = new AtomicBoolean();
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "english-book-worker");
            t.setDaemon(true);
            return t;
          });

  public BookJobs(
      BookStore store,
      BookService books,
      BookSources sources,
      AliyunTtsService tts,
      MediaService media,
      AiService ai,
      BookRemote remote,
      AppParameterService parameters) {
    this.store = store;
    this.books = books;
    this.sources = sources;
    this.tts = tts;
    this.media = media;
    this.ai = ai;
    this.remote = remote;
    this.parameters = parameters;
  }

  private boolean workerEnabled() {
    return Boolean.parseBoolean(
        BookSettings.value(parameters, "books.worker.enabled", String.valueOf(enabled)));
  }

  private String currentImageEndpoint() {
    return BookSettings.value(parameters, "books.image.endpoint", imageEndpoint);
  }

  private String currentImageModel() {
    return BookSettings.value(parameters, "books.image.model", imageModel);
  }

  private BigDecimal currentBudget() {
    try {
      return new BigDecimal(
          BookSettings.value(parameters, "books.max_job_cost", maxCost.toPlainString()));
    } catch (Exception e) {
      throw BookText.bad("书籍任务预算配置错误");
    }
  }

  private String runtimeConfig() {
    return BookText.hash(
        currentImageEndpoint()
            + "\n"
            + currentImageModel()
            + "\n"
            + sources.configurationFingerprint());
  }

  public Map<String, Object> options() {
    Map<String, Object> out = new LinkedHashMap<>(sources.status());
    out.put("workerEnabled", workerEnabled());
    out.put("voices", tts.vocabularyVoices());
    out.put(
        "imageReady",
        !currentImageEndpoint().isEmpty()
            && !currentImageModel().isEmpty()
            && parameters.configured("BOOK_IMAGE_API_KEY"));
    out.put("ttsReady", tts.configured());
    out.put("maxJobCost", currentBudget());
    return out;
  }

  private BigDecimal price(String key) {
    try {
      BigDecimal p = new BigDecimal(BookSettings.value(parameters, key, "0"));
      if (p.signum() <= 0) throw new IllegalArgumentException();
      return p;
    } catch (Exception e) {
      throw BookText.bad("请在系统参数配置正数单价：" + key + "（人民币），用于预算检查");
    }
  }

  public Map<String, Object> enqueue(
      String id, String kind, Map<String, Object> options, String actor) {
    if (!Arrays.asList("collect", "audio", "pictures", "original").contains(kind))
      throw BookText.bad("任务类型错误");
    if (!workerEnabled())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE, "BOOK_WORKER_DISABLED", "书籍任务尚未启用；请先执行数据库迁移并配置书源和生成服务");
    return store.transaction(
        () -> {
          store.row(id, true);
          store.noActiveJob(id);
          BookDocument d = store.draft(id);
          if (!d.rightsConfirmed
              || d.licenseNote.trim().isEmpty()
              || d.rightsScope.trim().isEmpty()) throw BookText.bad("请先保存来源与适用范围");
          Map<String, Object> safe = new LinkedHashMap<>();
          safe.put("runtimeConfig", runtimeConfig());
          safe.put("force", Boolean.TRUE.equals(options.get("force")));
          if (options.get("chapterId") instanceof String) {
            books.chapter(d, (String) options.get("chapterId"));
            safe.put("chapterId", options.get("chapterId"));
          }
          if ("collect".equals(kind)) {
            if (!d.chapters.isEmpty()) throw BookText.bad("采集会替换正文，请新建版本草稿后采集");
            int source = number(options.get("sourceId"), 0);
            if (source < 1) throw BookText.bad("请选择书源编号");
            safe.put("sourceId", source);
            safe.put(
                "provider", "licensed".equals(options.get("provider")) ? "licensed" : "gutenberg");
          }
          if ("original".equals(kind)) {
            if (!"ai_original".equals(d.kind)) throw BookText.bad("原创生成必须建立独立的 AI 原创书籍");
            safe.put(
                "chapterCount", Math.min(20, Math.max(1, number(options.get("chapterCount"), 5))));
            safe.put("theme", d.summary);
          }
          BigDecimal estimate = BigDecimal.ZERO;
          if ("pictures".equals(kind)) {
            if (!d.imagesRightsConfirmed) throw BookText.bad("请确认配图使用依据");
            if (currentImageEndpoint().isEmpty()
                || currentImageModel().isEmpty()
                || !parameters.configured("BOOK_IMAGE_API_KEY"))
              throw BookText.bad("请配置图像服务地址、模型及 BOOK_IMAGE_API_KEY");
            int images =
                (int)
                    d.chapters.stream()
                        .filter(
                            c ->
                                selected(c, safe)
                                    && (Boolean.TRUE.equals(safe.get("force"))
                                        || c.pictures.isEmpty()))
                        .count();
            if (images == 0) throw BookText.bad("没有需要生成配图的章节");
            BigDecimal unit = price("books.image.unit_price");
            estimate = unit.multiply(BigDecimal.valueOf(images));
            safe.put("unitPrice", unit);
            safe.put("estimatedUnits", images);
          }
          if ("audio".equals(kind)) {
            if (!d.audioRightsConfirmed) throw BookText.bad("请确认语音合成及分发范围");
            if (!tts.configured()) throw BookText.bad("请先配置可用的 TTS 凭据");
            if (d.chapters.isEmpty()) throw BookText.bad("请先导入或生成正文");
            tts.vocabularyVoice(d.voice, null, "luca");
            long calls =
                d.chapters.stream()
                    .filter(c -> selected(c, safe))
                    .flatMap(c -> BookService.clips(d, c).stream())
                    .mapToLong(c -> BookNlsBilling.calls(c.text))
                    .sum();
            BigDecimal unit = price("books.tts.price_per_thousand_calls");
            estimate = unit.multiply(BigDecimal.valueOf(calls)).divide(BigDecimal.valueOf(1000));
            safe.put("unitPrice", unit);
            safe.put("estimatedUnits", calls);
            safe.put("priceUnit", "per_thousand_billing_calls");
          }
          if (Boolean.TRUE.equals(safe.get("force"))) {
            for (BookDocument.Chapter c : d.chapters)
              if (selected(c, safe)) {
                if ("audio".equals(kind)) {
                  c.audioGeneration = CryptoUtils.randomId();
                  c.audio.clear();
                }
                if ("pictures".equals(kind)) c.imageGeneration = CryptoUtils.randomId();
              }
            store.jobSave(id, d);
          }
          safe.put("draftHash", BookText.hash(store.encode(d)));
          safe.put("estimatedCost", estimate);
          safe.put("currency", "CNY");
          safe.put("spentUnits", 0);
          if (estimate.compareTo(currentBudget()) > 0)
            throw BookText.bad("预计费用超过单任务预算，请选择单章处理或调整预算");
          String job = CryptoUtils.randomId();
          store.persistence.insertJob(job, id, kind, store.encode(safe), actor);
          return store.detail(id);
        });
  }

  private static int number(Object value, int fallback) {
    return value instanceof Number ? ((Number) value).intValue() : fallback;
  }

  private static boolean selected(BookDocument.Chapter c, Map<String, Object> options) {
    return options.get("chapterId") == null || c.id.equals(options.get("chapterId"));
  }

  public Map<String, Object> control(String id, String jobId, String action) {
    return store.transaction(
        () -> {
          store.row(id, true);
          List<Map<String, Object>> jobs = store.persistence.job(jobId, id);
          if (jobs.isEmpty()) throw BookText.bad("任务不存在");
          Map<String, Object> job = jobs.get(0);
          String state = (String) job.get("state");
          if ("pause".equals(action)) {
            if ("queued".equals(state)) store.persistence.pauseQueued(jobId);
            else if ("running".equals(state)) store.persistence.pauseRunning(jobId);
          } else if ("cancel".equals(action)) {
            store.persistence.cancel(jobId, store.now());
          } else if ("resume".equals(action)) {
            store.noActiveJob(id);
            if (!Arrays.asList("paused", "failed").contains(state)) throw BookText.bad("当前任务无法继续");
            store.persistence.resume(jobId, store.now());
          } else throw BookText.bad("任务操作错误");
          return store.detail(id);
        });
  }

  @Scheduled(fixedDelay = 3000)
  public void poll() {
    if (!workerEnabled()) return;
    // Heartbeats protect a healthy worker while a provider request is in flight.
    store.persistence.heartbeat(
        workerId, java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(1800)));
    if (!busy.compareAndSet(false, true)) return;
    executor.submit(
        () -> {
          try {
            recoverExpired();
            runNext();
          } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(BookJobs.class)
                .warn("Book worker failed: {}", e.getClass().getSimpleName());
          } finally {
            busy.set(false);
          }
        });
  }

  void recoverExpired() {
    store.persistence.recover(store.now());
  }

  public void runNext() {
    List<Map<String, Object>> queued = store.persistence.nextJob();
    if (queued.isEmpty()) return;
    Map<String, Object> job = queued.get(0);
    String jid = (String) job.get("id"),
        bid = (String) job.get("book_id"),
        kind = (String) job.get("kind");
    if (store.persistence.claim(
            jid,
            workerId,
            java.sql.Timestamp.from(java.time.Instant.now().plusSeconds(1800)),
            store.now())
        != 1) return;
    try {
      Map<String, Object> opts = store.json.readValue((String) job.get("options_json"), Map.class);
      if ("audio".equals(kind) && !"per_thousand_billing_calls".equals(opts.get("priceUnit")))
        throw BookText.bad("TTS 计费单位已更新，请新建音频任务；成功资源会复用");
      BookDocument d = store.draft(bid);
      if (opts.get("runtimeConfig") != null && !opts.get("runtimeConfig").equals(runtimeConfig()))
        throw BookText.bad("生成服务或书源配置已变更，请新建任务；已有资源仍保留");
      if (opts.get("draftHash") != null
          && !opts.get("draftHash").equals(BookText.hash(store.encode(d))))
        throw BookText.bad("草稿已在任务暂停期间修改，请新建任务；已有资源会复用");
      if ("collect".equals(kind)) {
        BookImporter.Result collected =
            sources.collect(String.valueOf(opts.get("provider")), number(opts.get("sourceId"), 0));
        if (!collected.images.isEmpty() && !d.imagesRightsConfirmed)
          throw BookText.bad("书源包含图片，请先确认配图使用依据后重试");
        for (BookDocument.Chapter c : collected.chapters)
          for (BookDocument.Picture p : c.pictures) {
            byte[] bytes = collected.images.get(p.assetId);
            p.assetId =
                media.storeBookAsset(
                    bid,
                    BookText.hash(Base64.getEncoder().encodeToString(bytes)),
                    bytes,
                    true,
                    d.licenseNote);
          }
        d.chapters = collected.chapters;
        d.completenessConfirmed = false;
        books.validate(d);
        checkpoint(jid, bid, d, 1, 1);
      } else if ("original".equals(kind)) original(jid, bid, d, opts);
      else generate(jid, bid, d, opts, "pictures".equals(kind));
      store.persistence.finish(jid, workerId, store.now());
    } catch (Stopped ignored) {
    } catch (Exception e) {
      String message = e instanceof ApiException ? e.getMessage() : "处理失败，请检查配置或重试；已成功资源已保留";
      store.persistence.fail(
          jid, workerId, message.substring(0, Math.min(message.length(), 480)), store.now());
    }
  }

  private void guard(String jid) {
    if (!workerEnabled()) {
      store.persistence.pauseRunning(jid);
    }

    List<Map<String, Object>> rows = store.persistence.guard(jid);
    if (rows.isEmpty() || !workerId.equals(rows.get(0).get("worker_id"))) throw new Stopped();
    String state = (String) rows.get(0).get("state");
    if ("pausing".equals(state)) {
      store.persistence.paused(jid, store.now());
      throw new Stopped();
    }
    if (!"running".equals(state)) throw new Stopped();
  }

  private void checkpoint(String jid, String bid, BookDocument doc, int done, int total) {
    store.transaction(
        () -> {
          store.row(bid, true);
          guard(jid);
          store.jobSave(bid, doc);
          store.persistence.checkpoint(jid, done, total, store.now());
          try {
            Map<String, Object> row = store.persistence.job(jid, bid).get(0);
            Map<String, Object> opts =
                store.json.readValue((String) row.get("options_json"), Map.class);
            opts.put("draftHash", BookText.hash(store.encode(doc)));
            store.persistence.jobOptions(jid, store.encode(opts));
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
          return null;
        });
  }

  private byte[] generated(String bid, String key, Supplier<byte[]> producer) {
    List<Map<String, Object>> rows = store.persistence.generation(key, bid);
    if (!rows.isEmpty()) {
      Map<String, Object> row = rows.get(0);
      if (row.get("payload") != null) return store.persistence.payload(key);
      throw new ApiException(
          HttpStatus.CONFLICT,
          "BOOK_GENERATION_UNCERTAIN",
          "存在未确认的生成请求，请核对服务商结果；可导入结果或创建新生成版本，系统不会自动重复计费");
    }
    store.persistence.requesting(key, bid);
    byte[] result;
    try {
      result = producer.get();
    } catch (Exception e) {
      // Do not guess whether an external request was billed. A credential/preflight error can be
      // safely retried after the configuration is fixed; an in-flight provider error is held.
      if (e instanceof ApiException
          && ((ApiException) e).getStatus().value() < 500
          && ((ApiException) e).getStatus() != HttpStatus.CONFLICT)
        store.persistence.forgetRejected(key);
      throw e;
    }
    store.persistence.generated(key, result, store.now());
    return result;
  }

  private String asset(
      String bid, String key, boolean image, String license, Supplier<byte[]> producer) {
    List<String> ready = store.persistence.readyAsset(key, bid);
    if (!ready.isEmpty() && media.isBookAsset(bid, ready.get(0), image)) return ready.get(0);
    byte[] payload = generated(bid, key, producer);
    if (!image) BookAudio.duration(payload);
    String asset = media.storeBookAsset(bid, key, payload, image, license);
    // Keep the response for upload-only retry if the process stops before this checkpoint.
    store.persistence.ready(key, asset, image ? 0 : BookAudio.duration(payload), store.now());
    return asset;
  }

  private void generate(
      String jid, String bid, BookDocument d, Map<String, Object> opts, boolean image) {
    List<BookDocument.Chapter> selected = new ArrayList<>();
    for (BookDocument.Chapter c : d.chapters) if (selected(c, opts)) selected.add(c);
    int
        total =
            image
                ? selected.size()
                : selected.stream().mapToInt(c -> BookService.clips(d, c).size()).sum(),
        done = 0;
    for (BookDocument.Chapter c : selected) {
      if (image) {
        if (c.pictures.isEmpty() || Boolean.TRUE.equals(opts.get("force"))) {
          guard(jid);
          String text = c.paragraphs.stream().map(p -> p.text).reduce("", (a, b) -> a + "\n" + b);
          String prompt =
              "Illustrate a scene accurately from this English book chapter. No letters or captions in the image. Style: "
                  + d.artStyle
                  + ". Consistent characters: "
                  + d.characterGuide
                  + ". Book: "
                  + d.title
                  + ". Chapter: "
                  + c.title
                  + ". Text: "
                  + text.substring(0, Math.min(10000, text.length()));
          String key =
              BookText.hash(
                  bid + ":image:" + c.imageGeneration + ":" + currentImageModel() + ":" + prompt);
          BookDocument.Picture p = new BookDocument.Picture();
          p.caption = c.title;
          p.source = "ai";
          p.paragraphId = c.paragraphs.isEmpty() ? "" : c.paragraphs.get(0).id;
          p.licenseNote = "AI 配图";
          p.assetId =
              asset(
                  bid,
                  key,
                  true,
                  p.licenseNote,
                  () -> {
                    byte[] result = image(prompt);
                    store.persistence.spend(jid, 1);
                    return result;
                  });
          if (Boolean.TRUE.equals(opts.get("force"))) c.pictures.clear();
          c.pictures.add(p);
        }
        checkpoint(jid, bid, d, ++done, total);
      } else {
        List<BookDocument.Clip> clips = BookService.clips(d, c);
        c.audio = new ArrayList<>();
        for (BookDocument.Clip clip : clips) {
          guard(jid);
          String key = BookText.hash(bid + ":" + clip.key);
          clip.assetId =
              asset(
                  bid,
                  key,
                  false,
                  d.licenseNote,
                  () -> {
                    byte[] result = tts.synthesizeBookSegment(clip.text, d.voice);
                    store.persistence.spend(jid, BookNlsBilling.calls(clip.text));
                    return result;
                  });
          clip.durationSeconds = store.persistence.duration(key);
          c.audio.add(clip);
          store.persistence.checkpoint(jid, ++done, total, store.now());
        }
        checkpoint(jid, bid, d, done, total);
      }
    }
  }

  Map<String, Object> imageRequest(String prompt) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("model", currentImageModel());
    request.put("prompt", prompt);
    if (currentImageModel().startsWith("doubao-seedream")) {
      request.put("size", "2K");
      request.put("stream", false);
      if (currentImageModel().contains("lite") || currentImageModel().contains("4-"))
        request.put("sequential_image_generation", "disabled");
    } else {
      request.put("n", 1);
      request.put("size", "1024x1024");
    }
    request.put("response_format", "b64_json");
    return request;
  }

  private byte[] image(String prompt) {
    JsonNode result =
        remote.post(
            currentImageEndpoint(),
            parameters.required("BOOK_IMAGE_API_KEY"),
            imageRequest(prompt));
    String b64 = result.path("data").path(0).path("b64_json").asText("");
    if (b64.isEmpty() || b64.length() > 7000000)
      throw new ApiException(
          HttpStatus.BAD_GATEWAY,
          "BOOK_IMAGE_RESPONSE_INVALID",
          "图像服务需要返回 data[0].b64_json，且图片不能超过 5MB");
    try {
      return Base64.getDecoder().decode(b64);
    } catch (Exception e) {
      throw BookText.bad("图像服务返回的图片编码错误");
    }
  }

  private String aiText(String jid, String bid, String key, String system, String prompt) {
    return new String(
        generated(
            bid,
            key,
            () ->
                ai.generateSystemContent(
                        system, prompt, "book:" + key, jid, "generate_english_book", "english_book")
                    .getBytes(StandardCharsets.UTF_8)),
        StandardCharsets.UTF_8);
  }

  private JsonNode parse(String text) {
    try {
      return store.json.readTree(
          text.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", ""));
    } catch (Exception e) {
      throw BookText.bad("AI 返回格式错误，结果已保存；请检查后手工整理");
    }
  }

  private void original(String jid, String bid, BookDocument d, Map<String, Object> opts) {
    int count = number(opts.get("chapterCount"), 5);
    String planKey =
        BookText.hash(bid + ":outline:" + d.title + ":" + opts.get("theme") + ":" + count);
    guard(jid);
    JsonNode outline =
        parse(
            aiText(
                jid,
                bid,
                planKey,
                "Create an original English learning story, never reproduce an existing named work. Return JSON only.",
                "Title: "
                    + d.title
                    + ". Theme: "
                    + opts.get("theme")
                    + ". Level: "
                    + d.difficulty
                    + ". Create exactly "
                    + count
                    + " chapter outlines. Return {\"characters\":\"consistent character appearance\",\"chapters\":[{\"title\":\"title\",\"outline\":\"plot\"}]}."));
    if (outline.path("chapters").size() != count) throw BookText.bad("AI 大纲章节数不符，请手工整理已保存结果");
    d.characterGuide = outline.path("characters").asText();
    for (int i = d.chapters.size(); i < count; i++) {
      guard(jid);
      JsonNode item = outline.path("chapters").get(i);
      final int index = i;
      String key = BookText.hash(planKey + ":chapter:" + index);
      String prompt =
          "Write chapter "
              + (index + 1)
              + " of an original English learning story. Level "
              + d.difficulty
              + ". 400-700 English words. Characters: "
              + d.characterGuide
              + ". Full outline: "
              + outline
              + ". Current chapter: "
              + item
              + ". Return {\"paragraphs\":[\"English paragraph\"]}.";
      JsonNode content =
          parse(
              aiText(
                  jid,
                  bid,
                  key,
                  "Write original English fiction. Follow the outline and do not copy published books. Return strict JSON.",
                  prompt));
      if (!content.path("paragraphs").isArray() || content.path("paragraphs").size() == 0)
        throw BookText.bad("AI 章节正文为空");
      BookDocument.Chapter c = new BookDocument.Chapter();
      c.title = item.path("title").asText("Chapter " + (i + 1));
      for (JsonNode p : content.path("paragraphs")) {
        if (!p.isTextual() || p.asText().trim().isEmpty()) throw BookText.bad("AI 段落格式错误");
        BookDocument.Paragraph paragraph = new BookDocument.Paragraph();
        paragraph.text = p.asText();
        c.paragraphs.add(paragraph);
      }
      d.chapters.add(c);
      d.completenessConfirmed = false;
      books.validate(d);
      checkpoint(jid, bid, d, i + 1, count);
    }
  }

  @PreDestroy
  public void close() {
    executor.shutdownNow();
  }

  private static class Stopped extends RuntimeException {}
}
