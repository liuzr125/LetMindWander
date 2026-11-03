package com.zhixing.books;

import com.zhixing.common.*;
import com.zhixing.service.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class BookService {
  final BookStore store;
  final MediaService media;
  final BookImporter importer;

  public BookService(BookStore store, MediaService media, BookImporter importer) {
    this.store = store;
    this.media = media;
    this.importer = importer;
  }

  public Map<String, Object> create(BookDocument doc, String actor) {
    validate(doc);
    return store.detail(store.create(doc, actor));
  }

  public void validate(BookDocument d) {
    if (d == null || d.title == null || d.title.trim().isEmpty() || d.title.length() > 240)
      throw BookText.bad("书名必填且不能超过 240 字符");
    for (String value :
        Arrays.asList(
            d.author,
            d.edition,
            d.summary,
            d.sourceUrl,
            d.licenseNote,
            d.rightsScope,
            d.voice,
            d.artStyle,
            d.characterGuide)) if (value == null) throw BookText.bad("书籍文字字段不能为 null");
    if (d.author.length() > 1000
        || d.edition.length() > 1000
        || d.summary.length() > 20000
        || d.sourceUrl.length() > 2048
        || d.licenseNote.length() > 1000
        || d.rightsScope.length() > 2000
        || d.voice.length() > 100
        || d.artStyle.length() > 1000
        || d.characterGuide.length() > 5000) throw BookText.bad("书籍说明字段超过长度限制");
    if (!Arrays.asList("licensed", "public_domain", "ai_original", "adapted", "excerpt")
        .contains(d.kind)) throw BookText.bad("书籍类型错误");
    if (!Arrays.asList("intro", "intermediate", "advanced").contains(d.difficulty))
      throw BookText.bad("难度错误");
    if (d.chapters == null || d.chapters.size() > 1000 || store.encode(d).length() > 6000000)
      throw BookText.bad("书籍内容过大或章节格式错误");
    Set<String> ids = new HashSet<>();
    for (BookDocument.Chapter c : d.chapters) {
      if (c == null
          || c.id == null
          || !c.id.matches("[a-f0-9]{32}")
          || !ids.add(c.id)
          || c.title == null
          || c.title.length() > 300
          || c.paragraphs == null
          || c.pictures == null
          || c.audio == null) throw BookText.bad("章节标识、标题或结构错误");
      for (BookDocument.Paragraph p : c.paragraphs)
        if (p == null
            || p.id == null
            || !p.id.matches("[a-f0-9]{32}")
            || !ids.add(p.id)
            || p.text == null
            || p.text.trim().isEmpty()
            || p.text.length() > 50000) throw BookText.bad("段落为空、过长或标识重复");
    }
  }

  public Map<String, Object> save(String id, BookDocument doc, int revision, String actor) {
    validate(doc);
    return store.transaction(
        () -> {
          Map<String, Object> row = store.row(id, true);
          store.noActiveJob(id);
          BookDocument old = store.decode((String) row.get("draft_json"));
          // Client edits never inject an audio mapping; keep only recordings matching unchanged
          // text/config.
          for (BookDocument.Chapter c : doc.chapters) {
            BookDocument.Chapter before =
                old.chapters.stream().filter(x -> x.id.equals(c.id)).findFirst().orElse(null);
            c.audio = new ArrayList<>();
            if (before != null
                && Objects.equals(old.voice, doc.voice)
                && Objects.equals(before.audioGeneration, c.audioGeneration)
                && store.encode(before.paragraphs).equals(store.encode(c.paragraphs)))
              c.audio = before.audio;
            for (BookDocument.Picture p : c.pictures)
              if (p == null || !media.isBookAsset(id, p.assetId, true))
                throw BookText.bad("图片不属于当前书籍或已失效");
          }
          if (!store
              .encode(old.chapters.stream().map(c -> c.paragraphs).toArray())
              .equals(store.encode(doc.chapters.stream().map(c -> c.paragraphs).toArray())))
            doc.completenessConfirmed = false;
          store.saveDraft(id, doc, actor, revision);
          return store.detail(id);
        });
  }

  public Map<String, Object> importFile(String id, String name, byte[] bytes, String actor) {
    BookImporter.Result imported = importer.read(name, bytes);
    return store.transaction(
        () -> {
          Map<String, Object> row = store.row(id, true);
          store.noActiveJob(id);
          BookDocument doc = store.decode((String) row.get("draft_json"));
          if (!doc.rightsConfirmed || (!imported.images.isEmpty() && !doc.imagesRightsConfirmed))
            throw BookText.bad("请先确认正文及导入图片的使用依据并保存");
          for (BookDocument.Chapter c : imported.chapters)
            for (BookDocument.Picture p : c.pictures) {
              byte[] b = imported.images.get(p.assetId);
              p.assetId =
                  media.storeBookAsset(
                      id,
                      BookText.hash("original-image:" + Base64.getEncoder().encodeToString(b)),
                      b,
                      true,
                      doc.licenseNote);
              p.licenseNote = doc.licenseNote;
            }
          doc.chapters = imported.chapters;
          doc.completenessConfirmed = false;
          validate(doc);
          store.saveDraft(id, doc, actor, ((Number) row.get("revision")).intValue());
          return store.detail(id);
        });
  }

  public Map<String, Object> uploadImage(
      String id, String chapterId, byte[] bytes, String caption, String actor) {
    return store.transaction(
        () -> {
          Map<String, Object> row = store.row(id, true);
          store.noActiveJob(id);
          BookDocument d = store.decode((String) row.get("draft_json"));
          if (!d.imagesRightsConfirmed) throw BookText.bad("请先确认配图使用依据并保存");
          BookDocument.Chapter c = chapter(d, chapterId);
          BookDocument.Picture p = new BookDocument.Picture();
          p.source = "original";
          p.assetId =
              media.storeBookAsset(
                  id,
                  BookText.hash(Base64.getEncoder().encodeToString(bytes)),
                  bytes,
                  true,
                  d.licenseNote);
          p.caption = caption == null ? "" : caption;
          p.licenseNote = d.licenseNote;
          c.pictures.add(p);
          store.saveDraft(id, d, actor, ((Number) row.get("revision")).intValue());
          return store.detail(id);
        });
  }

  public List<String> checks(String id, BookDocument d) {
    List<String> errors = new ArrayList<>();
    if (!d.rightsConfirmed || d.licenseNote.trim().isEmpty() || d.rightsScope.trim().isEmpty())
      errors.add("正文来源及投放范围待确认");
    if (!d.audioRightsConfirmed) errors.add("语音合成及分发范围待确认");
    if (!d.imagesRightsConfirmed) errors.add("配图使用依据待确认");
    if (!d.completenessConfirmed || d.chapters.isEmpty()) errors.add("全书目录及完整性待核对");
    if ("excerpt".equals(d.kind)) errors.add("节选不能发布为图文听读完整版");
    Set<String> texts = new HashSet<>();
    for (BookDocument.Chapter c : d.chapters) {
      if (c.paragraphs.isEmpty()) errors.add(c.title + "：正文为空");
      String body = store.encode(c.paragraphs.stream().map(p -> p.text).toArray());
      if (!texts.add(body)) errors.add(c.title + "：正文与其他章节重复");
      if (c.pictures.isEmpty()
          || c.pictures.stream().anyMatch(p -> !media.isBookAsset(id, p.assetId, true)))
        errors.add(c.title + "：缺少有效配图");
      List<BookDocument.Clip> expected = clips(d, c);
      if (expected.isEmpty() || expected.size() != c.audio.size()) errors.add(c.title + "：音频未齐备");
      else
        for (int i = 0; i < expected.size(); i++)
          if (!expected.get(i).key.equals(c.audio.get(i).key)
              || c.audio.get(i).durationSeconds <= 0
              || !media.isBookAsset(id, c.audio.get(i).assetId, false)) {
            errors.add(c.title + "：音频与正文不一致或资源无效");
            break;
          }
    }
    return errors;
  }

  public Map<String, Object> publish(String id, int revision, String actor) {
    return store.transaction(
        () -> {
          Map<String, Object> row = store.row(id, true);
          store.noActiveJob(id);
          if (((Number) row.get("revision")).intValue() != revision) throw store.conflict();
          BookDocument d = store.decode((String) row.get("draft_json"));
          List<String> errors = checks(id, d);
          if (!errors.isEmpty())
            throw new ApiException(
                HttpStatus.CONFLICT,
                "BOOK_NOT_READY",
                String.join("；", errors.subList(0, Math.min(10, errors.size()))));
          store.persistence.publish(id, actor, store.now());
          return store.detail(id);
        });
  }

  public Map<String, Object> unpublish(String id, String actor) {
    store.persistence.unpublish(id, actor, store.now());
    return store.detail(id);
  }

  public BookDocument.Chapter chapter(BookDocument d, String cid) {
    return d.chapters.stream()
        .filter(c -> c.id.equals(cid))
        .findFirst()
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "BOOK_CHAPTER_NOT_FOUND", "章节不存在"));
  }

  public static List<BookDocument.Clip> clips(BookDocument d, BookDocument.Chapter c) {
    List<BookDocument.Clip> out = new ArrayList<>();
    for (BookDocument.Paragraph p : c.paragraphs)
      for (String text : BookText.split(p.text)) {
        BookDocument.Clip clip = new BookDocument.Clip();
        clip.text = text;
        clip.paragraphId = p.id;
        clip.voice = d.voice;
        clip.key =
            BookText.hash("book-tts-v1:16000:0:" + c.audioGeneration + ":" + d.voice + ":" + text);
        out.add(clip);
      }
    return out;
  }

  public Map<String, Object> learningDetail(String id, String owner) {
    Map<String, Object> r = store.row(id, false);
    BookDocument d = store.published(id);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("title", d.title);
    out.put("author", d.author);
    out.put("edition", d.edition);
    out.put("summary", d.summary);
    out.put("difficulty", d.difficulty);
    out.put(
        "coverAssetId",
        d.chapters.stream()
            .flatMap(c -> c.pictures.stream())
            .map(p -> p.assetId)
            .findFirst()
            .orElse(""));
    out.put("kind", d.kind);
    out.put("sourceUrl", d.sourceUrl);
    out.put("licenseNote", d.licenseNote);
    out.put("publishedRevision", r.get("published_revision"));
    List<Map<String, Object>> chapters = new ArrayList<>();
    for (BookDocument.Chapter c : d.chapters) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", c.id);
      item.put("title", c.title);
      chapters.add(item);
    }
    out.put("chapters", chapters);
    out.put("progress", progress(id, owner));
    return out;
  }

  public BookDocument.Chapter learningChapter(String id, String cid) {
    return chapter(store.published(id), cid);
  }

  public String assetUrl(String id, String aid, boolean admin) {
    BookDocument doc = admin ? store.draft(id) : store.published(id);
    boolean used =
        doc.chapters.stream()
            .anyMatch(
                c ->
                    c.pictures.stream().anyMatch(p -> aid.equals(p.assetId))
                        || c.audio.stream().anyMatch(a -> aid.equals(a.assetId)));
    String url = used ? media.bookAssetUrl(id, aid) : null;
    if (url == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_ASSET_NOT_FOUND", "书籍资源不存在或暂不可用");
    return url;
  }

  public Map<String, Object> progress(String id, String owner) {
    List<Map<String, Object>> rows = store.persistence.progress(owner, id);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("revision", 0);
    out.put("completed", Collections.emptyList());
    out.put("reading", Collections.emptyMap());
    out.put("listening", Collections.emptyMap());
    if (!rows.isEmpty()) {
      try {
        out = store.json.readValue((String) rows.get(0).get("progress_json"), Map.class);
        out.put("revision", rows.get(0).get("revision"));
      } catch (Exception e) {
        throw BookText.bad("学习记录格式错误");
      }
    }
    Map<String, Object> bookRow = store.row(id, false);
    if (bookRow.get("published_json") != null) {
      BookDocument published = store.decode((String) bookRow.get("published_json"));
      Set<String> valid = new HashSet<>();
      for (BookDocument.Chapter c : published.chapters) valid.add(c.id);
      List<String> completed = new ArrayList<>((List<String>) out.get("completed"));
      completed.removeIf(cid -> !valid.contains(cid));
      out.put("completed", completed);
      for (String mode : Arrays.asList("reading", "listening")) {
        Map<String, Object> pos = (Map<String, Object>) out.get(mode);
        if (pos == null || pos.isEmpty()) continue;
        BookDocument.Chapter c =
            published.chapters.stream()
                .filter(x -> x.id.equals(pos.get("chapterId")))
                .findFirst()
                .orElse(null);
        boolean ok = c != null;
        if (ok && "reading".equals(mode))
          ok = c.paragraphs.stream().anyMatch(p -> p.id.equals(pos.get("paragraphId")));
        if (ok && "listening".equals(mode)) {
          int clip = pos.get("clip") instanceof Number ? ((Number) pos.get("clip")).intValue() : -1;
          ok =
              clip >= 0
                  && clip < c.audio.size()
                  && Objects.equals(pos.get("audioKey"), c.audio.get(clip).key);
        }
        if (!ok) {
          out.put(mode, Collections.emptyMap());
          out.put("positionUpdated", true);
        }
      }
    }
    return out;
  }

  public Map<String, Object> saveProgress(String id, String owner, Map<String, Object> payload) {
    return store.transaction(
        () -> {
          // Lock the book row as an insertion guard, also serializing the first progress write.
          store.row(id, true);
          BookDocument d = store.published(id);
          Map<String, Object> old = progress(id, owner);
          int revision = ((Number) old.get("revision")).intValue();
          if (!(payload.get("revision") instanceof Number)
              || ((Number) payload.get("revision")).intValue() != revision) throw store.conflict();
          for (String key : Arrays.asList("reading", "listening"))
            if (payload.get(key) instanceof Map) {
              Map<String, Object> pos = (Map<String, Object>) payload.get(key);
              BookDocument.Chapter c = chapter(d, String.valueOf(pos.get("chapterId")));
              if ("reading".equals(key)) {
                String pid = String.valueOf(pos.get("paragraphId"));
                if (c.paragraphs.stream().noneMatch(p -> p.id.equals(pid)))
                  throw BookText.bad("阅读段落不存在");
                old.put(
                    key,
                    new LinkedHashMap<String, Object>() {
                      {
                        put("chapterId", c.id);
                        put("paragraphId", pid);
                      }
                    });
              } else {
                int clip =
                    pos.get("clip") instanceof Number ? ((Number) pos.get("clip")).intValue() : -1;
                double seconds =
                    pos.get("seconds") instanceof Number
                        ? ((Number) pos.get("seconds")).doubleValue()
                        : -1;
                if (clip < 0
                    || clip >= c.audio.size()
                    || !Double.isFinite(seconds)
                    || seconds < 0
                    || seconds > 36000) throw BookText.bad("音频位置无效");
                if (!Objects.equals(pos.get("audioKey"), c.audio.get(clip).key))
                  throw store.conflict();
                Map<String, Object> safe = new LinkedHashMap<>();
                safe.put("chapterId", c.id);
                safe.put("clip", clip);
                safe.put("seconds", seconds);
                safe.put("audioKey", c.audio.get(clip).key);
                old.put(key, safe);
              }
            }
          Set<String> completed = new LinkedHashSet<>((List<String>) old.get("completed"));
          Set<String> valid = new HashSet<>();
          for (BookDocument.Chapter c : d.chapters) valid.add(c.id);
          completed.retainAll(valid);
          if (payload.get("completeChapterId") != null) {
            String cid = String.valueOf(payload.get("completeChapterId"));
            chapter(d, cid);
            completed.add(cid);
          }
          old.put("completed", new ArrayList<>(completed));
          old.put("revision", revision + 1);
          if (revision == 0) store.persistence.insertProgress(owner, id, store.encode(old));
          else if (store.persistence.saveProgress(
                  owner, id, revision, store.encode(old), store.now())
              != 1) throw store.conflict();
          return old;
        });
  }
}
