package com.zhixing.controller;

import com.zhixing.dto.ContentActionRequest;
import com.zhixing.dto.FamiliarityRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ContentDetailView;
import com.zhixing.service.ContentService;
import com.zhixing.service.SessionService;
import com.zhixing.service.AliyunTtsService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/contents")
public class ContentController {
    private final SessionService sessions;
    private final ContentService contents;
    private final AliyunTtsService tts;

    public ContentController(SessionService sessions, ContentService contents,AliyunTtsService tts) {
        this.sessions = sessions;
        this.contents = contents;
        this.tts = tts;
    }

    @GetMapping("/{id}")
    public ContentDetailView get(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        return contents.get(user(auth), id);
    }

    @PostMapping("/{id}/understood")
    public ContentDetailView understood(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @RequestBody(required = false) ContentActionRequest r) {
        return contents.understood(user(auth), id, r);
    }

    @PostMapping("/{id}/favorite")
    public ContentDetailView favorite(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @RequestBody(required = false) ContentActionRequest r) {
        return contents.favorite(user(auth), id, r);
    }

    @PostMapping("/{id}/review")
    public ContentDetailView review(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @RequestBody(required = false) ContentActionRequest r) {
        return contents.review(user(auth), id, r);
    }

    @PostMapping("/{id}/word-book")
    public ContentDetailView wordBook(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @RequestBody(required = false) ContentActionRequest r) {
        return contents.wordBook(user(auth), id, r);
    }

    @PutMapping("/{id}/familiarity")
    public ContentDetailView familiarity(@RequestHeader(value = "Authorization", required = false) String auth,@PathVariable String id,@RequestBody FamiliarityRequest r){return contents.familiarity(user(auth),id,r);}

    @PostMapping("/{id}/speech")
    public java.util.Map<String,Object> speech(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){return tts.speech(user(auth),id);}

    private String user(String auth) {
        AuthenticatedSession session = sessions.requireUser(auth);
        return session.getUserId();
    }
}
