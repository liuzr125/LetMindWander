package com.zhixing.controller;

import com.zhixing.dto.AiAskRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.AiService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final SessionService sessions;private final AiService ai;
    public AiController(SessionService sessions,AiService ai){this.sessions=sessions;this.ai=ai;}
    @GetMapping("/models") public Map<String,Object> models(@RequestHeader(value="Authorization",required=false)String auth){return ai.models(user(auth));}
    @PostMapping("/ask") public Map<String,Object> ask(@RequestHeader(value="Authorization",required=false)String auth,@RequestHeader(value="Idempotency-Key",required=false)String requestKey,@Valid @RequestBody AiAskRequest request){return ai.ask(user(auth),request,requestKey);}
    @GetMapping("/ask/history") public Map<String,Object> history(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(defaultValue="20")int size){return ai.history(user(auth),size);}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
