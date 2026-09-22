package com.zhixing.controller;

import com.zhixing.dto.ContentActionRequest;
import com.zhixing.dto.FamiliarityRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ContentDetailView;
import com.zhixing.service.ContentService;
import com.zhixing.service.SessionService;
import com.zhixing.service.AliyunTtsService;
import com.zhixing.service.FollowRecordingService;
import com.zhixing.service.ArticleWordGlossaryService;
import com.zhixing.service.ArticleParagraphExplanationService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/learning/contents")
public class ContentController {
    private final SessionService sessions;
    private final ContentService contents;
    private final AliyunTtsService tts;
    private final FollowRecordingService followRecordings;
    private final ArticleWordGlossaryService articleWords;
    private final ArticleParagraphExplanationService paragraphExplanations;

    public ContentController(SessionService sessions, ContentService contents,AliyunTtsService tts, FollowRecordingService followRecordings, ArticleWordGlossaryService articleWords, ArticleParagraphExplanationService paragraphExplanations) {
        this.sessions = sessions;
        this.contents = contents;
        this.tts = tts;
        this.followRecordings = followRecordings;
        this.articleWords = articleWords;
        this.paragraphExplanations = paragraphExplanations;
    }

    @GetMapping("/{id}")
    public ContentDetailView get(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id) {
        return contents.get(user(auth), id);
    }

    @PostMapping("/{id}/understood")
    public ContentDetailView understood(@RequestHeader(value = "Authorization", required = false) String auth, @PathVariable String id, @RequestBody(required = false) ContentActionRequest r) {
        return contents.understood(user(auth), id, r);
    }

    @PostMapping("/{id}/feedback")
    public ContentDetailView feedback(@RequestHeader(value = "Authorization", required = false) String auth,@PathVariable String id,
                                      @RequestBody ContentActionRequest r) {
        return contents.wordFeedback(user(auth),id,r);
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

    @PostMapping("/{id}/words/{term}/speech")
    public java.util.Map<String,Object> articleWordSpeech(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@PathVariable String term){return tts.speechArticleWord(user(auth),id,term);}

    @PostMapping("/{id}/words/{term}/lookup")
    public Map<String,Object> articleWordLookup(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@PathVariable String term){return articleWords.ensure(user(auth),id,term);}

    @PostMapping("/{id}/paragraphs/{paragraphId}/explanation")
    public Map<String,Object> paragraphExplanation(@RequestHeader(value="Authorization",required=false) String auth,
                                                    @PathVariable String id,@PathVariable String paragraphId) {
        return paragraphExplanations.explain(user(auth),id,paragraphId);
    }

    @GetMapping("/{id}/follow-recording")
    public Map<String,Object> followRecording(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){
        return followRecordings.latest(user(auth),id);
    }

    @PostMapping(value="/{id}/follow-recording", consumes="multipart/form-data")
    public Map<String,Object> uploadFollowRecording(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,
                                                     @RequestPart("file") MultipartFile file,@RequestParam("durationMs") int durationMs) throws IOException {
        return followRecordings.upload(user(auth),id,durationMs,file.getInputStream());
    }

    @DeleteMapping("/{id}/follow-recording")
    public Map<String,Object> deleteFollowRecording(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){
        return followRecordings.delete(user(auth),id);
    }

    private String user(String auth) {
        AuthenticatedSession session = sessions.requireUser(auth);
        return session.getUserId();
    }
}
