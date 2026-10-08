package com.zhixing.controller;

import com.zhixing.dto.PhoneAuthorizationRequest;
import com.zhixing.dto.RegisterRequest;
import com.zhixing.dto.SendSmsCodeRequest;
import com.zhixing.dto.WechatLoginRequest;
import com.zhixing.model.AuthResult;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.AuthService;
import com.zhixing.service.SessionService;
import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService authService;
  private final SessionService sessions;

  public AuthController(AuthService authService, SessionService sessions) {
    this.authService = authService;
    this.sessions = sessions;
  }

  /** 用微信临时凭证登录；新用户返回受邀注册流程。 */
  @PostMapping("/wechat")
  public Object wechat(@Valid @RequestBody WechatLoginRequest request) {
    return authService.login(request);
  }

  /** 验证注册票据、手机号和同意版本后完成注册。 */
  @PostMapping("/register")
  public AuthResult register(@Valid @RequestBody RegisterRequest request) {
    return authService.register(request);
  }

  /** 为待注册流程验证微信手机号授权。 */
  @PostMapping("/phone")
  public Map<String, Object> phone(@Valid @RequestBody PhoneAuthorizationRequest request) {
    return authService.authorizePhone(request);
  }

  /** 为有效注册票据发送短信验证码，频率限制由服务层执行。 */
  @PostMapping("/sms-code")
  public Object sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
    return authService.sendSmsCode(request);
  }

  /** 撤销当前登录会话，不接收客户端指定的会话归属。 */
  @PostMapping("/logout")
  public Map<String, Boolean> logout(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    AuthenticatedSession session = sessions.requireUser(authorization);
    sessions.revoke(session.getSessionId());
    return Collections.singletonMap("revoked", true);
  }
}
