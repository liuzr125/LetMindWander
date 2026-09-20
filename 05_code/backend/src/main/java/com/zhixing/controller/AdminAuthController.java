package com.zhixing.controller;

import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {
    private final AdminAuthService auth; public AdminAuthController(AdminAuthService auth){this.auth=auth;}
    @PostMapping("/login") public Map<String,Object> login(@RequestBody Map<String,Object> body){return auth.login(value(body,"username"),value(body,"password"));}
    @GetMapping("/me") public Map<String,Object> me(HttpServletRequest request){return auth.me(principal(request));}
    @PostMapping("/logout") public Map<String,Object> logout(@RequestHeader(value="X-Admin-Token",required=false)String token){auth.logout(token);return java.util.Collections.<String,Object>singletonMap("loggedOut",true);}
    private AdminPrincipal principal(HttpServletRequest request){return (AdminPrincipal)request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);} private String value(Map<String,Object> body,String key){Object value=body==null?null:body.get(key);return value==null?null:String.valueOf(value);}
}
