package com.zhixing.controller;

import com.zhixing.dto.AiAskRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.AiService;
import com.zhixing.service.SessionService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
  private final SessionService sessions;
  private final AiService ai;

  public AiController(SessionService sessions, AiService ai) {
    this.sessions = sessions;
    this.ai = ai;
  }

  /** 返回当前默认模型可用性和用户授权状态，不返回供应商密钥。 */
  @GetMapping("/models")
  public Map<String, Object> models(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return ai.models(user(auth));
  }

  /** 验证当前用户后提交幂等提问，由业务层执行额度、预算和费用结算。 */
  @PostMapping("/ask")
  public Map<String, Object> ask(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestHeader(value = "Idempotency-Key", required = false) String requestKey,
      @Valid @RequestBody AiAskRequest request) {
    return ai.ask(user(auth), request, requestKey);
  }

  /** 分页读取当前用户的提问历史。 */
  @GetMapping("/ask/history")
  public Map<String, Object> history(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(defaultValue = "20") int size) {
    return ai.history(user(auth), size);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
