package com.zhixing.controller;
import com.zhixing.service.AnnouncementService;
import com.zhixing.service.SessionService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/announcements")
public class AnnouncementController {
 private final AnnouncementService service;private final SessionService sessions;
 public AnnouncementController(AnnouncementService service,SessionService sessions){this.service=service;this.sessions=sessions;}
 @GetMapping public List<Map<String,Object>> list(@RequestHeader(value="Authorization",required=false)String auth,@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize){sessions.requireUser(auth);return service.list(false,page,pageSize);}
 @GetMapping("/{id}")public Map<String,Object> detail(@RequestHeader(value="Authorization",required=false)String auth,@PathVariable String id){sessions.requireUser(auth);return service.detail(id,false);}
}
