package com.zhixing.controller;

import com.zhixing.dto.ConsentRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.ConsentService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/consents")
public class ConsentController {
    private final SessionService sessions;
    private final ConsentService consents;

    public ConsentController(SessionService sessions, ConsentService consents) {
        this.sessions = sessions;
        this.consents = consents;
    }

    @PostMapping
    public Map<String, Object> record(@RequestHeader(value = "Authorization", required = false) String authorization,
                                      @Valid @RequestBody ConsentRequest request) {
        AuthenticatedSession session = sessions.requireUser(authorization);
        return consents.record(session.getUserId(), request);
    }
}
