package com.zhixing.controller;

import com.zhixing.dto.ConsentRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.ConsentService;
import com.zhixing.service.SessionService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/consents")
public class ConsentController {
  private final SessionService sessions;
  private final ConsentService consents;

  public ConsentController(SessionService sessions, ConsentService consents) {
    this.sessions = sessions;
    this.consents = consents;
  }

  /** 为当前登录用户记录授权或撤回决定及对应文档版本。 */
  @PostMapping
  public Map<String, Object> record(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody ConsentRequest request) {
    AuthenticatedSession session = sessions.requireUser(authorization);
    return consents.record(session.getUserId(), request);
  }
}
