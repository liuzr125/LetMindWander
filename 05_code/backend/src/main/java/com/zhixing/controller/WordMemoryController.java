package com.zhixing.controller;

import com.zhixing.dto.*;
import com.zhixing.model.*;
import com.zhixing.service.SessionService;
import com.zhixing.service.WordMemoryService;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/word-memory")
public class WordMemoryController {
    private final SessionService sessions;
    private final WordMemoryService memory;
    public WordMemoryController(SessionService sessions,WordMemoryService memory){this.sessions=sessions;this.memory=memory;}

    @GetMapping("/config")
    public Map<String,Boolean> config(@RequestHeader(value="Authorization",required=false)String auth){user(auth);return memory.config();}

    @GetMapping("/hints")
    public List<WordMemoryHintView> hints(@RequestHeader(value="Authorization",required=false)String auth,
                                          @RequestParam String contentId,@RequestParam(required=false)String senseId){user(auth);return memory.hints(contentId,senseId);}

    @GetMapping("/sources")
    public WordMemorySourceSummaryView sources(@RequestHeader(value="Authorization",required=false)String auth){return memory.sources(user(auth));}

    @PostMapping("/sessions")
    public WordMemorySessionView create(@RequestHeader(value="Authorization",required=false)String auth,
                                         @RequestHeader(value="Idempotency-Key",required=false)String idempotencyKey,
                                         @RequestBody CreateWordMemorySessionRequest request){return memory.create(user(auth),idempotencyKey,request);}

    @GetMapping("/sessions/{id}")
    public WordMemorySessionView get(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return memory.get(user(auth),id);}

    @PostMapping("/sessions/{sessionId}/episodes/{episodeId}/hint")
    public WordMemoryHintRevealView hint(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String sessionId,
                                         @PathVariable String episodeId,@RequestBody WordMemoryHintRequest request){return memory.revealHint(user(auth),sessionId,episodeId,request);}

    @PostMapping("/sessions/{sessionId}/episodes/{episodeId}/attempts")
    public WordMemoryAttemptView attempt(@RequestHeader(value="Authorization",required=false)String auth,
                                          @RequestHeader(value="Idempotency-Key",required=false)String idempotencyKey,
                                          @PathVariable String sessionId,@PathVariable String episodeId,
                                          @RequestBody WordMemoryAttemptRequest request){return memory.attempt(user(auth),sessionId,episodeId,idempotencyKey,request);}

    @PostMapping("/sessions/{id}/finish")
    public WordMemoryResultView finish(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,
                                       @RequestBody FinishWordMemorySessionRequest request){return memory.finish(user(auth),id,request);}

    @GetMapping("/sessions/{id}/result")
    public WordMemoryResultView result(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return memory.result(user(auth),id);}

    private String user(String auth){return sessions.requireUser(auth).getUserId();}
}
