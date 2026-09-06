package com.zhixing.controller;

import com.zhixing.dto.RegisterRequest;
import com.zhixing.dto.PhoneAuthorizationRequest;
import com.zhixing.dto.SendSmsCodeRequest;
import com.zhixing.dto.WechatLoginRequest;
import com.zhixing.model.AuthResult;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.AuthService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final SessionService sessions;

    public AuthController(AuthService authService, SessionService sessions) {
        this.authService = authService;
        this.sessions = sessions;
    }

    @PostMapping("/wechat")
    public Object wechat(@Valid @RequestBody WechatLoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    public AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/phone")
    public Map<String, Object> phone(@Valid @RequestBody PhoneAuthorizationRequest request) {
        return authService.authorizePhone(request);
    }

    @PostMapping("/sms-code")
    public Object sendSmsCode(@Valid @RequestBody SendSmsCodeRequest request) {
        return authService.sendSmsCode(request);
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AuthenticatedSession session = sessions.requireUser(authorization);
        sessions.revoke(session.getSessionId());
        return Collections.singletonMap("revoked", true);
    }
}
