package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminUserPageView;
import com.zhixing.service.AdminUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService users;private final AppProperties properties;
    public AdminUserController(AdminUserService users,AppProperties properties){this.users=users;this.properties=properties;}
    @GetMapping public AdminUserPageView users(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="all")String status){admin(token);return users.page(page,pageSize,keyword,status);}
    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
