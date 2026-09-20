package com.zhixing.controller;

import com.zhixing.service.SessionService;
import com.zhixing.service.WordLearningCardService;
import com.zhixing.service.WordLearningCardPreferenceService;
import com.zhixing.model.AuthenticatedSession;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/learning")
public class WordLearningCardController {
    private final SessionService sessions;
    private final WordLearningCardService cards;
    private final WordLearningCardPreferenceService preferences;
    public WordLearningCardController(SessionService sessions,WordLearningCardService cards,WordLearningCardPreferenceService preferences){this.sessions=sessions;this.cards=cards;this.preferences=preferences;}
    @GetMapping("/contents/{id}/learning-cards")
    public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){
        String userId=user(auth);
        return cards.cards(id,preferences.enabledTypes(userId));
    }
    @GetMapping("/card-preferences")
    public Map<String,Object> preferences(@RequestHeader(value="Authorization",required=false)String auth){return preferences.preferences(user(auth));}
    @PutMapping("/card-preferences")
    public Map<String,Object> updatePreferences(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> request){return preferences.update(user(auth),request);}
    private String user(String auth){AuthenticatedSession session=sessions.requireUser(auth);return session.getUserId();}
}
