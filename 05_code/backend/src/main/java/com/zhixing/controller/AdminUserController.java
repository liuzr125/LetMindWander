package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminUserPageView;
import com.zhixing.model.AdminUserLearningView;
import com.zhixing.model.AdminUserView;
import com.zhixing.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService users;private final AppProperties properties;
    public AdminUserController(AdminUserService users,AppProperties properties){this.users=users;this.properties=properties;}
    @GetMapping public AdminUserPageView users(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="all")String status){admin(token);return users.page(page,pageSize,keyword,status);}
    @GetMapping("/{userId}/learning") public AdminUserLearningView learning(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String userId,@RequestParam(defaultValue="1")Integer notebookPage,@RequestParam(defaultValue="20")Integer notebookPageSize){admin(token);return users.learning(userId,notebookPage,notebookPageSize);}
    @PutMapping("/{userId}/ai-authorization") public AdminUserView aiAuthorization(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String userId,@RequestBody Map<String,Object> body){admin(token);Object enabled=body==null?null:body.get("enabled");if(!(enabled instanceof Boolean))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_AI_AUTHORIZATION","enabled 必须为 true 或 false");return users.setAiAuthorization(userId,(Boolean)enabled,properties.getAdminPrincipalId());}
    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
