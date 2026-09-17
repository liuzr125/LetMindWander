package com.zhixing.controller;
import com.zhixing.dto.ResourceScheduleRequest;import com.zhixing.model.*;import com.zhixing.service.*;import org.springframework.web.bind.annotation.*;import javax.validation.Valid;import java.util.*;
@RestController @RequestMapping("/api/resource-schedules")
public class ResourceScheduleController {
 private final SessionService sessions;private final ResourceScheduleService schedules;public ResourceScheduleController(SessionService s,ResourceScheduleService r){sessions=s;schedules=r;}private String user(String a){return sessions.requireUser(a).getUserId();}
 @GetMapping public List<ResourceScheduleView> list(@RequestHeader(value="Authorization",required=false)String a,@RequestParam(required=false)String state){return schedules.list(user(a),state);}
 @GetMapping("/{id}") public ResourceScheduleView get(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id){return schedules.get(user(a),id);}
 @GetMapping("/sources/available") public List<ResourceSourceView> sources(@RequestHeader(value="Authorization",required=false)String a){user(a);return schedules.sources();}
 @PostMapping public ResourceScheduleView create(@RequestHeader(value="Authorization",required=false)String a,@Valid @RequestBody ResourceScheduleRequest r){return schedules.save(user(a),null,r);}
 @PutMapping("/{id}") public ResourceScheduleView update(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id,@Valid @RequestBody ResourceScheduleRequest r){return schedules.save(user(a),id,r);}
 @PostMapping("/{id}/state") public ResourceScheduleView state(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id,@RequestBody Map<String,Object> r){return schedules.state(user(a),id,String.valueOf(r.get("state")),r.get("expectedVersion")==null?null:Integer.valueOf(String.valueOf(r.get("expectedVersion"))));}
}
