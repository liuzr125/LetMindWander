package com.zhixing.controller;

import com.zhixing.dto.SelectVocabularyBookRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.VocabularyBookProgressView;
import com.zhixing.model.VocabularyBookResetView;
import com.zhixing.model.VocabularyBookView;
import com.zhixing.service.DailyTaskService;
import com.zhixing.service.SessionService;
import com.zhixing.service.VocabularyBookService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/vocabulary-books")
public class VocabularyBookController {
  private final SessionService sessions;
  private final VocabularyBookService books;
  private final DailyTaskService daily;

  public VocabularyBookController(
      SessionService sessions, VocabularyBookService books, DailyTaskService daily) {
    this.sessions = sessions;
    this.books = books;
    this.daily = daily;
  }

  /** 列出当前用户可选词书。 */
  @GetMapping
  public List<VocabularyBookView> list(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return books.list(user(auth));
  }

  /** 读取当前选定的词书及每日新词额度。 */
  @GetMapping("/current")
  public VocabularyBookView current(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return books.current(user(auth));
  }

  /** 分页查看当前词书的有效词条、已学数及完成比例。 */
  @GetMapping("/current/progress")
  public VocabularyBookProgressView progress(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(required = false, defaultValue = "all") String status,
      @RequestParam(required = false, defaultValue = "1") Integer page,
      @RequestParam(required = false, defaultValue = "20") Integer pageSize) {
    return books.progress(user(auth), status, page, pageSize);
  }

  /** 切换词书或更新新词额度，并刷新当天尚未开始的词任务。 */
  @PutMapping("/current")
  public VocabularyBookView select(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @Valid @RequestBody SelectVocabularyBookRequest request) {
    String owner = user(auth);
    VocabularyBookView selected = books.select(owner, request);
    daily.refreshToday(owner);
    return selected;
  }
  /** 「重新学习」：把当前词书已学的词条划回未学（今日已排任务不变，下次排新词时会重新候选）。 */
  /** 重置当前词书学习状态并暂停相关复习，保留历史轮次记录。 */
  @PostMapping("/current/reset-learned")
  public VocabularyBookResetView resetLearned(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return books.resetLearned(user(auth));
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
