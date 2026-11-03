package com.zhixing.books;

import com.zhixing.dto.ContentActionRequest;
import com.zhixing.model.*;
import com.zhixing.service.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/english-books")
public class BookLearningController {
  private final SessionService sessions;
  private final BookService books;
  private final BookStore store;
  private final LearningService learning;
  private final ContentService contents;

  public BookLearningController(
      SessionService sessions,
      BookService books,
      BookStore store,
      LearningService learning,
      ContentService contents) {
    this.sessions = sessions;
    this.books = books;
    this.store = store;
    this.learning = learning;
    this.contents = contents;
  }

  private String user(String auth) {
    return sessions.requireUser(auth).getUserId();
  }

  @GetMapping
  public Map<String, Object> list(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "false") boolean shelf,
      @RequestParam(defaultValue = "") String difficulty) {
    String owner = user(auth);
    Map<String, Object> result = store.list(true, keyword, page, difficulty);
    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
    for (Map<String, Object> item : items)
      item.put("progress", books.progress((String) item.get("id"), owner));
    // Shelf uses a database filter before pagination, not a filtered first page of the library.
    if (shelf) {
      List<String> ids =
          store.persistence.shelf(
              owner, "%" + keyword + "%", (Math.max(1, page) - 1) * 20, difficulty);
      List<Map<String, Object>> shelfItems = new ArrayList<>();
      for (String id : ids.subList(0, Math.min(20, ids.size()))) {
        Map<String, Object> d = books.learningDetail(id, owner);
        d.put("chapterCount", ((List<?>) d.get("chapters")).size());
        shelfItems.add(d);
      }
      result.put("items", shelfItems);
      result.put("hasMore", ids.size() > 20);
    }
    return result;
  }

  @GetMapping("/{id}")
  public Map<String, Object> detail(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    return books.learningDetail(id, user(auth));
  }

  @GetMapping("/{id}/chapters/{cid}")
  public BookDocument.Chapter chapter(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @PathVariable String cid) {
    user(auth);
    return books.learningChapter(id, cid);
  }

  @PutMapping("/{id}/progress")
  public Map<String, Object> progress(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody Map<String, Object> body) {
    return books.saveProgress(id, user(auth), body);
  }

  @GetMapping("/{id}/media/{aid}")
  public Map<String, Object> media(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @PathVariable String aid) {
    user(auth);
    return Collections.singletonMap("url", books.assetUrl(id, aid, false));
  }

  @GetMapping("/{id}/words")
  public LearningPageView words(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestParam String term) {
    String owner = user(auth);
    store.published(id);
    if (!term.matches("[A-Za-z][A-Za-z'-]{0,60}")) throw BookText.bad("词语格式错误");
    return learning.page(owner, "word", null, null, null, false, null, term, 1, 20);
  }

  @PostMapping("/{id}/word-source")
  public Map<String, Object> wordSource(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody Map<String, String> body) {
    String owner = user(auth);
    return store.transaction(
        () -> {
          store.row(id, true);
          BookDocument d = store.published(id);
          BookDocument.Chapter c = books.chapter(d, body.get("chapterId"));
          BookDocument.Paragraph p =
              c.paragraphs.stream()
                  .filter(x -> x.id.equals(body.get("paragraphId")))
                  .findFirst()
                  .orElseThrow(() -> BookText.bad("原句不存在"));
          String term = body.get("term"), contentId = body.get("contentId");
          if (term == null || !term.matches("[A-Za-z][A-Za-z'-]{0,60}"))
            throw BookText.bad("词语格式错误");
          if (!java.util.regex.Pattern.compile(
                  "(?i)(?<![A-Za-z])" + java.util.regex.Pattern.quote(term) + "(?![A-Za-z])")
              .matcher(p.text)
              .find()) throw BookText.bad("所选词不在当前段落中");
          LearningPageView words =
              learning.page(owner, "word", null, null, null, false, null, term, 1, 20);
          if (words.getItems().stream()
              .noneMatch(
                  w ->
                      w.getContentId().equals(contentId)
                          && (term.equalsIgnoreCase(w.getWordTerm())
                              || Boolean.TRUE.equals(w.getAliasMatch()))))
            throw BookText.bad("所选词条与原句不匹配");
          ContentActionRequest action = new ContentActionRequest();
          action.setActive(true);
          contents.wordBook(owner, contentId, action);
          if (!store.persistence.hasWordSource(owner, id, p.id, contentId))
            store.persistence.wordSource(
                owner,
                id,
                c.id,
                p.id,
                contentId,
                store.row(id, false).get("published_revision"),
                p.text);
          return Collections.singletonMap("saved", true);
        });
  }
}
