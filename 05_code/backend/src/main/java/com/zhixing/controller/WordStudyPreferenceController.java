package com.zhixing.controller;

import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.SessionService;
import com.zhixing.service.WordStudyPreferenceService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/word-study-preferences")
public class WordStudyPreferenceController {
  private final SessionService sessions;
  private final WordStudyPreferenceService preferences;

  public WordStudyPreferenceController(
      SessionService sessions, WordStudyPreferenceService preferences) {
    this.sessions = sessions;
    this.preferences = preferences;
  }

  /** 读取当前用户语音偏好及管理员配置的全局间隔模式。 */
  @GetMapping
  public Map<String, Object> get(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return preferences.preferences(user(auth));
  }

  /** 保存当前用户的语音偏好；拒绝用户修改全局间隔模式。 */
  @PutMapping
  public Map<String, Object> update(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestBody Map<String, Object> request) {
    return preferences.update(user(auth), request);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
