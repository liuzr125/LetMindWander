package com.zhixing.controller;

import com.zhixing.dto.ReviewFeedbackRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ReviewItemView;
import com.zhixing.service.ReviewService;
import com.zhixing.service.SessionService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {
  private final SessionService sessions;
  private final ReviewService reviews;
  private final com.zhixing.service.BookStudyService bookStudy;

  public ReviewController(
      SessionService sessions,
      ReviewService reviews,
      com.zhixing.service.BookStudyService bookStudy) {
    this.sessions = sessions;
    this.reviews = reviews;
    this.bookStudy = bookStudy;
  }

  /** 读取指定业务日期的当前用户到期复习队列。 */
  @GetMapping("/queue")
  public List<ReviewItemView> queue(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return reviews.queue(user(auth), date);
  }

  /** 提交非单词复习反馈；单词反馈必须使用单词学习接口。 */
  @PostMapping("/{id}/feedback")
  public List<ReviewItemView> feedback(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody ReviewFeedbackRequest request) {
    String owner = user(auth);
    List<ReviewItemView> items = reviews.feedback(owner, id, request);
    bookStudy.recordReview(owner, id, java.time.Instant.now());
    return items;
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
