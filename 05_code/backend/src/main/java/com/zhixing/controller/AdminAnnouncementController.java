package com.zhixing.controller;

import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.service.AnnouncementService;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/announcements")
public class AdminAnnouncementController {
  private final AnnouncementService service;

  public AdminAnnouncementController(AnnouncementService service) {
    this.service = service;
  }

  /** 管理员分页查看全部公告状态；菜单权限由 AdminSessionFilter 校验。 */
  @GetMapping
  public List<Map<String, Object>> list(
      @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int pageSize) {
    return service.list(true, page, pageSize);
  }

  /** 管理员读取完整公告，包含草稿与尚未到发布时间的内容。 */
  @GetMapping("/{id}")
  public Map<String, Object> detail(@PathVariable String id) {
    return service.detail(id, true);
  }

  /** 创建公告并记录操作者审计；发布内容由状态和发布时间共同控制。 */
  @PostMapping
  public Map<String, Object> create(
      @RequestBody Map<String, String> body, HttpServletRequest request) {
    return service.save(actor(request), null, body);
  }

  /** 更新标题、正文、发布时间与发布状态，保存及审计在同一事务完成。 */
  @PutMapping("/{id}")
  public Map<String, Object> update(
      @PathVariable String id, @RequestBody Map<String, String> body, HttpServletRequest request) {
    return service.save(actor(request), id, body);
  }

  private String actor(HttpServletRequest request) {
    return ((AdminPrincipal) request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE))
        .getAccountId();
  }
}
