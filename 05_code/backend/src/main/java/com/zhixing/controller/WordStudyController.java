package com.zhixing.controller;
import com.zhixing.service.SessionService;
import com.zhixing.service.WordStudyService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/word-study")
public class WordStudyController {
 private final SessionService sessions;private final WordStudyService study;
 public WordStudyController(SessionService sessions,WordStudyService study){this.sessions=sessions;this.study=study;}
 @PostMapping("/queue") public Map<String,Object> queue(@RequestHeader(value="Authorization",required=false)String auth,@RequestBody Map<String,Object> request){return study.queue(sessions.requireUser(auth).getUserId(),String.valueOf(request.getOrDefault("mode","all")),(String)request.get("contentId"));}
 @PostMapping("/{id}/feedback") public Map<String,Object> feedback(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id,@RequestBody Map<String,Object> request){return study.feedback(sessions.requireUser(auth).getUserId(),id,request);}
}
