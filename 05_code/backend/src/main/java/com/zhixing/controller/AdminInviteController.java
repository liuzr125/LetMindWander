package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.CreateInviteRequest;
import com.zhixing.dto.UpdateAdmissionRequest;
import com.zhixing.service.AdminInviteService;
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

import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminInviteController {
    private final AdminInviteService invites;
    private final AppProperties properties;

    public AdminInviteController(AdminInviteService invites, AppProperties properties) {
        this.invites = invites;
        this.properties = properties;
    }

    @PostMapping("/invites")
    public Map<String, Object> create(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @Valid @RequestBody CreateInviteRequest request) {
        requireAdmin(token);
        return invites.create(request);
    }

    @GetMapping("/invites")
    public Map<String, Object> list(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                    @RequestParam(defaultValue = "all") String status,
                                    @RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size) {
        requireAdmin(token);
        return invites.list(status, page, size);
    }

    @PostMapping("/invites/{id}/revoke")
    public Map<String, Object> revoke(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @PathVariable String id) {
        requireAdmin(token);
        return invites.revoke(id);
    }

    @DeleteMapping("/invites/{id}")
    public Map<String, Object> delete(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                      @PathVariable String id) {
        requireAdmin(token);
        return invites.delete(id);
    }

    @GetMapping("/admission")
    public Map<String, Object> admission(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAdmin(token);
        return invites.admission();
    }

    @PutMapping("/admission")
    public Map<String, Object> updateAdmission(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                                @Valid @RequestBody UpdateAdmissionRequest request) {
        requireAdmin(token);
        return invites.updateAdmission(request.getInvitedLimit());
    }

    @GetMapping("/invite-stats")
    public Map<String, Object> stats(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        requireAdmin(token);
        return invites.stats();
    }

    private void requireAdmin(String token) {
        if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
        }
    }
}
