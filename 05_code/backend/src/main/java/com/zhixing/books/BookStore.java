package com.zhixing.books;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.EnglishBookRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookStore {
  final EnglishBookRepository persistence;
  final ObjectMapper json;
  final TransactionTemplate tx;

  public BookStore(
      EnglishBookRepository persistence, ObjectMapper json, PlatformTransactionManager manager) {
    this.persistence = persistence;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
  }

  public <T> T transaction(Supplier<T> action) {
    return tx.execute(s -> action.get());
  }

  public Timestamp now() {
    return Timestamp.from(Instant.now());
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw BookText.bad("数据格式错误");
    }
  }

  public BookDocument decode(String value) {
    try {
      return json.readValue(value, BookDocument.class);
    } catch (Exception e) {
      throw BookText.bad("书籍格式错误");
    }
  }

  public Map<String, Object> row(String id, boolean lock) {
    List<Map<String, Object>> rows = persistence.book(id, lock);
    if (rows.isEmpty()) throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "书籍不存在");
    return rows.get(0);
  }

  public BookDocument draft(String id) {
    return decode((String) row(id, false).get("draft_json"));
  }

  public BookDocument published(String id) {
    Map<String, Object> r = row(id, false);
    if (!"published".equals(r.get("state")) || r.get("published_json") == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_AVAILABLE", "书籍未发布或已下架");
    return decode((String) r.get("published_json"));
  }

  public void noActiveJob(String id) {
    if (persistence.activeJobs(id) > 0)
      throw new ApiException(HttpStatus.CONFLICT, "BOOK_JOB_ACTIVE", "书籍正在处理，请先暂停任务");
  }

  public String create(BookDocument doc, String actor) {
    String id = CryptoUtils.randomId();
    persistence.insertBook(id, doc.title, encode(doc), actor, doc.difficulty);
    return id;
  }

  public void saveDraft(String id, BookDocument doc, String actor, int expected) {
    if (persistence.saveDraft(id, doc.title, encode(doc), actor, expected, now(), doc.difficulty)
        != 1) throw conflict();
  }

  public void jobSave(String id, BookDocument doc) {
    persistence.jobSave(id, doc.title, encode(doc), now(), doc.difficulty);
  }

  public ApiException conflict() {
    return new ApiException(HttpStatus.CONFLICT, "BOOK_VERSION_CONFLICT", "内容或进度已更新，请刷新后重试");
  }

  public Map<String, Object> detail(String id) {
    Map<String, Object> r = row(id, false), out = new LinkedHashMap<>();
    out.put("id", id);
    out.put("revision", r.get("revision"));
    out.put("publishedRevision", r.get("published_revision"));
    out.put("state", r.get("state"));
    out.put("document", decode((String) r.get("draft_json")));
    out.put("jobs", persistence.jobs(id));
    out.put("unresolved", persistence.unresolved(id));
    return out;
  }

  public Map<String, Object> list(boolean published, String query, int page) {
    return list(published, query, page, "");
  }

  public Map<String, Object> list(boolean published, String query, int page, String difficulty) {
    if (!Arrays.asList("", "intro", "intermediate", "advanced").contains(difficulty))
      throw BookText.bad("难度错误");
    int offset = (Math.max(1, page) - 1) * 20;

    String keyword = "%" + (query == null ? "" : query.trim()) + "%";
    List<Map<String, Object>> rows = persistence.books(published, keyword, offset, difficulty);
    List<Map<String, Object>> items = new ArrayList<>();
    boolean more = rows.size() > 20;
    for (Map<String, Object> r : rows.subList(0, Math.min(rows.size(), 20))) {
      BookDocument d = decode((String) r.get(published ? "published_json" : "draft_json"));
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("id", r.get("id"));
      item.put("title", d.title);
      item.put("author", d.author);
      item.put("kind", d.kind);
      item.put(
          "coverAssetId",
          d.chapters.stream()
              .flatMap(c -> c.pictures.stream())
              .map(p -> p.assetId)
              .findFirst()
              .orElse(""));
      item.put("difficulty", d.difficulty);
      item.put("state", r.get("state"));
      item.put("chapterCount", d.chapters.size());
      item.put("pictureCount", d.chapters.stream().mapToInt(c -> c.pictures.size()).sum());
      item.put("audioCount", d.chapters.stream().mapToInt(c -> c.audio.size()).sum());
      items.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", items);
    out.put("hasMore", more);
    out.put("page", Math.max(1, page));
    return out;
  }
}
