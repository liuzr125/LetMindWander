package com.zhixing.controller;
import com.zhixing.dto.*;import com.zhixing.model.*;import com.zhixing.service.*;
import org.springframework.web.bind.annotation.*;import javax.validation.Valid;import java.util.*;
@RestController @RequestMapping("/api/friends")
public class FriendController {
 private final SessionService sessions;private final FriendService friends;public FriendController(SessionService s,FriendService f){sessions=s;friends=f;}private String user(String a){return sessions.requireUser(a).getUserId();}
 @GetMapping public List<FriendView> list(@RequestHeader(value="Authorization",required=false)String a){return friends.list(user(a));}
 @GetMapping("/search") public List<FriendSearchView> search(@RequestHeader(value="Authorization",required=false)String a,@RequestParam String type,@RequestParam String query){return friends.search(user(a),type,query);}
 @GetMapping("/requests") public List<FriendRequestView> requests(@RequestHeader(value="Authorization",required=false)String a,@RequestParam(required=false)String direction){return friends.requests(user(a),direction);}
 @PostMapping("/requests") public Map<String,Object> send(@RequestHeader(value="Authorization",required=false)String a,@Valid @RequestBody CreateFriendRequest r){return friends.send(user(a),r);}
 @PostMapping("/requests/{id}/decision") public FriendRequestView decide(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id,@RequestBody DecideFriendRequest r){return friends.decide(user(a),id,r);}
 @GetMapping("/{id}/shared") public List<SharedKnowledgeView> shared(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id){return friends.shared(user(a),id);}
 @DeleteMapping("/{id}") public Map<String,Object> remove(@RequestHeader(value="Authorization",required=false)String a,@PathVariable String id){friends.remove(user(a),id);return Collections.<String,Object>singletonMap("removed",true);}
}
