package com.zhixing.controller;

import com.zhixing.model.AuthenticatedSession;
import com.zhixing.service.SessionService;
import com.zhixing.service.WordStudyPreferenceService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/learning/word-study-preferences")
public class WordStudyPreferenceController {
    private final SessionService sessions;
    private final WordStudyPreferenceService preferences;
    public WordStudyPreferenceController(SessionService sessions,WordStudyPreferenceService preferences){this.sessions=sessions;this.preferences=preferences;}
    @GetMapping public Map<String,Object> get(@RequestHeader(value="Authorization",required=false)String auth){return preferences.preferences(user(auth));}
    @PutMapping public Map<String,Object> update(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> request){return preferences.update(user(auth),request);}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
