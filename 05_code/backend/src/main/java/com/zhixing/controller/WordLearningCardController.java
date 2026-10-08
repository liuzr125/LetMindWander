package com.zhixing.controller;

import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.SessionService;
import com.zhixing.service.WordLearningCardPreferenceService;
import com.zhixing.service.WordLearningCardService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning")
public class WordLearningCardController {
  private final SessionService sessions;
  private final WordLearningCardService cards;
  private final WordLearningCardPreferenceService preferences;

  public WordLearningCardController(
      SessionService sessions,
      WordLearningCardService cards,
      WordLearningCardPreferenceService preferences) {
    this.sessions = sessions;
    this.cards = cards;
    this.preferences = preferences;
  }

  /** 按当前用户展示偏好读取通过发布门禁的辅助卡片，不改变学习掌握状态。 */
  @GetMapping("/contents/{id}/learning-cards")
  public List<Map<String, Object>> list(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    String userId = user(auth);
    return cards.cards(id, preferences.enabledTypes(userId));
  }

  /** 读取当前用户各类辅助卡片的展示开关，未配置类型默认展示。 */
  @GetMapping("/card-preferences")
  public Map<String, Object> preferences(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return preferences.preferences(user(auth));
  }

  /** 保存允许展示的卡片类型集合；只影响个人展示，不修改卡片内容。 */
  @PutMapping("/card-preferences")
  public Map<String, Object> updatePreferences(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestBody Map<String, Object> request) {
    return preferences.update(user(auth), request);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
