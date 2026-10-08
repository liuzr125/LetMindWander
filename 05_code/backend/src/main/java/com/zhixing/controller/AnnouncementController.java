package com.zhixing.controller;

import com.zhixing.service.AnnouncementService;
import com.zhixing.service.SessionService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
  private final AnnouncementService service;
  private final SessionService sessions;

  public AnnouncementController(AnnouncementService service, SessionService sessions) {
    this.service = service;
    this.sessions = sessions;
  }

  /** 已登录用户分页查看已发布且已到发布时间的公告，最新优先。 */
  @GetMapping
  public List<Map<String, Object>> list(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    sessions.requireUser(auth);
    return service.list(false, page, pageSize);
  }

  /** 返回用户可见公告正文；草稿、撤回与未来公告统一返回不存在。 */
  @GetMapping("/{id}")
  public Map<String, Object> detail(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    sessions.requireUser(auth);
    return service.detail(id, false);
  }
}
