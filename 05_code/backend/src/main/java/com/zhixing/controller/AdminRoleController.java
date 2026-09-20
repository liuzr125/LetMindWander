package com.zhixing.controller;

import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminAuthService;
import com.zhixing.service.AdminPrincipal;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminRoleController {
    private final AdminAuthService auth; public AdminRoleController(AdminAuthService auth){this.auth=auth;}
    @GetMapping("/roles") public Map<String,Object> administration(){return auth.administration();}
    @PostMapping("/roles") public Map<String,Object> role(@RequestBody Map<String,Object> body,HttpServletRequest request){return auth.createRole(value(body,"name"),value(body,"description"),codes(body==null?null:body.get("menuCodes")),principal(request));}
    @PutMapping("/roles/{roleId}/menus") public Map<String,Object> menus(@PathVariable String roleId,@RequestBody Map<String,Object> body,HttpServletRequest request){return auth.setRoleMenus(roleId,codes(body==null?null:body.get("menuCodes")),principal(request));}
    @PostMapping("/accounts") public Map<String,Object> account(@RequestBody Map<String,Object> body,HttpServletRequest request){return auth.createAccount(value(body,"username"),value(body,"password"),value(body,"roleId"),principal(request));}
    @PutMapping("/accounts/{accountId}/password") public Map<String,Object> password(@PathVariable String accountId,@RequestBody Map<String,Object> body,HttpServletRequest request){return auth.resetAccountPassword(accountId,value(body,"password"),principal(request));}
    private AdminPrincipal principal(HttpServletRequest request){return (AdminPrincipal)request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);} private String value(Map<String,Object> body,String key){Object value=body==null?null:body.get(key);return value==null?null:String.valueOf(value);} private List<String> codes(Object raw){if(!(raw instanceof List))return Collections.emptyList();List<String> values=new ArrayList<String>();for(Object value:(List<?>)raw)if(value!=null)values.add(String.valueOf(value));return values;}
}
