package com.zhixing.controller;

import com.zhixing.dto.*;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.KnowledgeDetailView;
import com.zhixing.model.KnowledgeFriendView;
import com.zhixing.model.KnowledgeListItemView;
import com.zhixing.service.KnowledgeService;
import com.zhixing.service.SessionService;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
  private final SessionService sessions;
  private final KnowledgeService knowledge;

  public KnowledgeController(SessionService sessions, KnowledgeService knowledge) {
    this.sessions = sessions;
    this.knowledge = knowledge;
  }

  /** 按筛选条件列出当前用户可访问的知识条目。 */
  @GetMapping
  public List<KnowledgeListItemView> list(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(required = false, defaultValue = "all") String type,
      @RequestParam(required = false) String query,
      @RequestParam(required = false) String learningStatus,
      @RequestParam(required = false) String verificationStatus,
      @RequestParam(required = false, defaultValue = "20") Integer limit) {
    return knowledge.list(user(auth), type, query, learningStatus, verificationStatus, limit);
  }

  /** 读取当前用户知识条目可用标签。 */
  @GetMapping("/tags")
  public List<String> tags(@RequestHeader(value = "Authorization", required = false) String auth) {
    return knowledge.tags(user(auth));
  }

  /** 读取知识共享可选的好友列表。 */
  @GetMapping("/friends")
  public List<KnowledgeFriendView> friends(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return knowledge.friends(user(auth));
  }

  /** 读取当前用户可访问的知识条目详情。 */
  @GetMapping("/{id}")
  public KnowledgeDetailView get(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    return knowledge.get(user(auth), id);
  }

  /** 创建当前用户的知识条目。 */
  @PostMapping
  public KnowledgeDetailView create(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestBody KnowledgeSaveRequest request) {
    return knowledge.create(user(auth), request);
  }

  /** 更新当前用户拥有的知识条目。 */
  @PutMapping("/{id}")
  public KnowledgeDetailView update(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody KnowledgeSaveRequest request) {
    return knowledge.update(user(auth), id, request);
  }

  /** 更新当前用户知识条目的验证状态。 */
  @PutMapping("/{id}/verification")
  public KnowledgeDetailView verify(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody KnowledgeVerificationRequest request) {
    return knowledge.verify(user(auth), id, request);
  }

  /** 更新知识条目的共享可见范围。 */
  @PutMapping("/{id}/visibility")
  public KnowledgeDetailView visibility(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody KnowledgeVisibilityRequest request) {
    return knowledge.visibility(user(auth), id, request);
  }

  /** 设置当前用户知识条目的复习计划。 */
  @PostMapping("/{id}/review")
  public KnowledgeDetailView review(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody(required = false) KnowledgeReviewRequest request) {
    return knowledge.review(user(auth), id, request);
  }

  /** 删除当前用户拥有的知识条目。 */
  @DeleteMapping("/{id}")
  public void delete(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestParam Integer expectedVersion) {
    knowledge.delete(user(auth), id, expectedVersion);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
