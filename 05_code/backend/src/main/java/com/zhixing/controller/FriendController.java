package com.zhixing.controller;

import com.zhixing.dto.*;
import com.zhixing.model.*;
import com.zhixing.service.*;
import java.util.*;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
  private final SessionService sessions;
  private final FriendService friends;

  public FriendController(SessionService s, FriendService f) {
    sessions = s;
    friends = f;
  }

  private String user(String a) {
    return sessions.requireUser(a).getUserId();
  }

  /** 读取当前用户的有效好友关系。 */
  @GetMapping
  public List<FriendView> list(@RequestHeader(value = "Authorization", required = false) String a) {
    return friends.list(user(a));
  }

  /** 按指定类型查找可添加的用户，服务层限制可返回信息。 */
  @GetMapping("/search")
  public List<FriendSearchView> search(
      @RequestHeader(value = "Authorization", required = false) String a,
      @RequestParam String type,
      @RequestParam String query) {
    return friends.search(user(a), type, query);
  }

  /** 读取本人发出或收到的好友申请。 */
  @GetMapping("/requests")
  public List<FriendRequestView> requests(
      @RequestHeader(value = "Authorization", required = false) String a,
      @RequestParam(required = false) String direction) {
    return friends.requests(user(a), direction);
  }

  /** 以当前用户身份发送好友申请。 */
  @PostMapping("/requests")
  public Map<String, Object> send(
      @RequestHeader(value = "Authorization", required = false) String a,
      @Valid @RequestBody CreateFriendRequest r) {
    return friends.send(user(a), r);
  }

  /** 处理指定好友申请，服务层验证接收者权限与当前状态。 */
  @PostMapping("/requests/{id}/decision")
  public FriendRequestView decide(
      @RequestHeader(value = "Authorization", required = false) String a,
      @PathVariable String id,
      @RequestBody DecideFriendRequest r) {
    return friends.decide(user(a), id, r);
  }

  /** 读取指定好友允许共享的知识内容。 */
  @GetMapping("/{id}/shared")
  public List<SharedKnowledgeView> shared(
      @RequestHeader(value = "Authorization", required = false) String a, @PathVariable String id) {
    return friends.shared(user(a), id);
  }

  /** 解除本人和指定用户的好友关系。 */
  @DeleteMapping("/{id}")
  public Map<String, Object> remove(
      @RequestHeader(value = "Authorization", required = false) String a, @PathVariable String id) {
    friends.remove(user(a), id);
    return Collections.<String, Object>singletonMap("removed", true);
  }
}
