package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.CreateInviteRequest;
import com.zhixing.dto.UpdateAdmissionRequest;
import com.zhixing.service.AdminInviteService;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminInviteController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminInviteController.class);
  private final AdminInviteService invites;
  private final AppProperties properties;

  public AdminInviteController(AdminInviteService invites, AppProperties properties) {
    this.invites = invites;
    this.properties = properties;
  }

  /** 批量生成邀请码，明文仅在创建响应中交付。 */
  @PostMapping("/invites")
  public Map<String, Object> create(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @Valid @RequestBody CreateInviteRequest request) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=create");
    return invites.create(request);
  }

  /** 按状态分页读取邀请码记录，过期状态按当前时间计算。 */
  @GetMapping("/invites")
  public Map<String, Object> list(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "all") String status,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=list");
    return invites.list(status, page, size);
  }

  /** 撤销仍可用且未过期的邀请码。 */
  @PostMapping("/invites/{id}/revoke")
  public Map<String, Object> revoke(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=revoke");
    return invites.revoke(id);
  }

  /** 删除未兑换的邀请码，已兑换记录受保护。 */
  @DeleteMapping("/invites/{id}")
  public Map<String, Object> delete(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String id) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=delete");
    return invites.delete(id);
  }

  /** 读取试用名额上限、使用量及剩余名额。 */
  @GetMapping("/admission")
  public Map<String, Object> admission(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=admission");
    return invites.admission();
  }

  /** 锁定名额计数后更新上限，禁止低于已使用量。 */
  @PutMapping("/admission")
  public Map<String, Object> updateAdmission(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @Valid @RequestBody UpdateAdmissionRequest request) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=updateAdmission");
    return invites.updateAdmission(request.getInvitedLimit());
  }

  /** 读取名额与各邀请码状态数量。 */
  @GetMapping("/invite-stats")
  public Map<String, Object> stats(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    requireAdmin(token);
    LOGGER.debug("admin_request controller=Invite operation=stats");
    return invites.stats();
  }

  private void requireAdmin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token)) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
    }
  }
}
