package com.zhixing.books;

import com.zhixing.config.AdminActors;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/english-books")
public class BookAdminController {
  private final BookService books;
  private final BookStore store;
  private final BookJobs jobs;
  private final BookSources sources;

  public BookAdminController(
      BookService books, BookStore store, BookJobs jobs, BookSources sources) {
    this.books = books;
    this.store = store;
    this.jobs = jobs;
    this.sources = sources;
  }

  public static class Edit {
    public int revision;
    public BookDocument document;
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestParam(defaultValue = "") String keyword, @RequestParam(defaultValue = "1") int page) {
    return store.list(false, keyword, page);
  }

  @GetMapping("/options")
  public Map<String, Object> options() {
    return jobs.options();
  }

  @GetMapping("/search")
  public BookSources.SearchResult search(@RequestParam String title) {
    return sources.search(title);
  }

  @PostMapping
  public Map<String, Object> create(@RequestBody BookDocument doc, HttpServletRequest r) {
    return books.create(doc, AdminActors.requireId(r));
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable String id) {
    return store.detail(id);
  }

  @GetMapping("/{id}/tasks")
  public Map<String, Object> tasks(@PathVariable String id) {
    store.row(id, false);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("jobs", store.persistence.jobs(id));
    out.put("unresolved", store.persistence.unresolved(id));
    return out;
  }

  @PutMapping("/{id}")
  public Map<String, Object> save(
      @PathVariable String id, @RequestBody Edit edit, HttpServletRequest r) {
    return books.save(id, edit.document, edit.revision, AdminActors.requireId(r));
  }

  @PostMapping("/{id}/import")
  public Map<String, Object> importFile(
      @PathVariable String id, @RequestParam MultipartFile file, HttpServletRequest r)
      throws Exception {
    return books.importFile(
        id, file.getOriginalFilename(), file.getBytes(), AdminActors.requireId(r));
  }

  @PostMapping("/{id}/chapters/{cid}/pictures")
  public Map<String, Object> picture(
      @PathVariable String id,
      @PathVariable String cid,
      @RequestParam MultipartFile file,
      @RequestParam(defaultValue = "") String caption,
      HttpServletRequest r)
      throws Exception {
    return books.uploadImage(id, cid, file.getBytes(), caption, AdminActors.requireId(r));
  }

  @PostMapping("/{id}/generation/{key}/recover")
  public Map<String, Object> recover(
      @PathVariable String id, @PathVariable String key, @RequestParam MultipartFile file)
      throws Exception {
    byte[] bytes = file.getBytes();
    if (bytes.length == 0 || bytes.length > 5000000) throw BookText.bad("恢复结果不能为空或超过 5MB");
    return store.transaction(
        () -> {
          store.row(id, true);
          store.noActiveJob(id);
          if (store.persistence.unresolved(id).stream()
              .noneMatch(r -> key.equals(r.get("step_key")))) throw BookText.bad("没有待核对的对应请求");
          store.persistence.generated(key, bytes, store.now());
          return store.detail(id);
        });
  }

  @PostMapping("/{id}/generation/{key}/reset")
  public Map<String, Object> reset(
      @PathVariable String id, @PathVariable String key, @RequestBody Map<String, Object> body) {
    if (!Boolean.TRUE.equals(body.get("confirmedNoResult")))
      throw BookText.bad("需要明确确认供应商未产生结果，才能重新提交付费请求");
    return store.transaction(
        () -> {
          store.row(id, true);
          store.noActiveJob(id);
          store.persistence.resetUnresolved(key, id);
          return store.detail(id);
        });
  }

  @GetMapping("/{id}/checks")
  public Map<String, Object> checks(@PathVariable String id) {
    return Collections.singletonMap("errors", books.checks(id, store.draft(id)));
  }

  @PostMapping("/{id}/publish")
  public Map<String, Object> publish(
      @PathVariable String id, @RequestBody Edit edit, HttpServletRequest r) {
    return books.publish(id, edit.revision, AdminActors.requireId(r));
  }

  @PostMapping("/{id}/unpublish")
  public Map<String, Object> unpublish(@PathVariable String id, HttpServletRequest r) {
    return books.unpublish(id, AdminActors.requireId(r));
  }

  @PostMapping("/{id}/jobs/{kind}")
  public Map<String, Object> generate(
      @PathVariable String id,
      @PathVariable String kind,
      @RequestBody Map<String, Object> opts,
      HttpServletRequest r) {
    return jobs.enqueue(id, kind, opts, AdminActors.requireId(r));
  }

  @PostMapping("/{id}/jobs/{jid}/{action}")
  public Map<String, Object> control(
      @PathVariable String id, @PathVariable String jid, @PathVariable String action) {
    return jobs.control(id, jid, action);
  }

  @GetMapping("/{id}/media/{aid}")
  public Map<String, Object> asset(@PathVariable String id, @PathVariable String aid) {
    return Collections.singletonMap("url", books.assetUrl(id, aid, true));
  }
}
