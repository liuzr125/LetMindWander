package com.zhixing.controller;

import com.zhixing.dto.ResourceScheduleRequest;
import com.zhixing.model.*;
import com.zhixing.service.*;
import java.util.*;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resource-schedules")
public class ResourceScheduleController {
  private final SessionService sessions;
  private final ResourceScheduleService schedules;

  public ResourceScheduleController(SessionService s, ResourceScheduleService r) {
    sessions = s;
    schedules = r;
  }

  private String user(String a) {
    return sessions.requireUser(a).getUserId();
  }

  /** 按状态读取当前用户的资源学习计划。 */
  @GetMapping
  public List<ResourceScheduleView> list(
      @RequestHeader(value = "Authorization", required = false) String a,
      @RequestParam(required = false) String state) {
    return schedules.list(user(a), state);
  }

  /** 读取本人指定资源计划及进度。 */
  @GetMapping("/{id}")
  public ResourceScheduleView get(
      @RequestHeader(value = "Authorization", required = false) String a, @PathVariable String id) {
    return schedules.get(user(a), id);
  }

  /** 登录后列出可用于资源计划的来源。 */
  @GetMapping("/sources/available")
  public List<ResourceSourceView> sources(
      @RequestHeader(value = "Authorization", required = false) String a) {
    user(a);
    return schedules.sources();
  }

  /** 为当前用户创建资源学习计划。 */
  @PostMapping
  public ResourceScheduleView create(
      @RequestHeader(value = "Authorization", required = false) String a,
      @Valid @RequestBody ResourceScheduleRequest r) {
    return schedules.save(user(a), null, r);
  }

  /** 更新本人资源计划，服务层验证归属及参数。 */
  @PutMapping("/{id}")
  public ResourceScheduleView update(
      @RequestHeader(value = "Authorization", required = false) String a,
      @PathVariable String id,
      @Valid @RequestBody ResourceScheduleRequest r) {
    return schedules.save(user(a), id, r);
  }

  /** 切换本人资源计划状态，服务层约束合法状态转换。 */
  @PostMapping("/{id}/state")
  public ResourceScheduleView state(
      @RequestHeader(value = "Authorization", required = false) String a,
      @PathVariable String id,
      @RequestBody Map<String, Object> r) {
    return schedules.state(
        user(a),
        id,
        String.valueOf(r.get("state")),
        r.get("expectedVersion") == null
            ? null
            : Integer.valueOf(String.valueOf(r.get("expectedVersion"))));
  }
}
