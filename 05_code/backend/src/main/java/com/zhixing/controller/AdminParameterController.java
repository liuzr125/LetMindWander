package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AppParameterService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/** 系统运行参数仅向已验证的管理端开放；敏感参数永不通过列表接口返回原文。 */
@RestController
@RequestMapping("/api/admin/parameters")
public class AdminParameterController {
    private final AppParameterService parameters; private final AppProperties properties;
    public AdminParameterController(AppParameterService parameters,AppProperties properties){this.parameters=parameters;this.properties=properties;}

    @GetMapping public Map<String,Object> list(@RequestHeader(value="X-Admin-Token",required=false) String token,@RequestParam(value="keyword",required=false) String keyword,@RequestParam(defaultValue="1") Integer page,@RequestParam(defaultValue="10") Integer pageSize){admin(token);return parameters.adminPage(keyword,page,pageSize);}
    @PostMapping public Map<String,Object> create(@RequestHeader(value="X-Admin-Token",required=false) String token,@RequestBody Map<String,Object> body){admin(token);return parameters.adminCreate(body==null?Collections.<String,Object>emptyMap():body);}
    @PutMapping("/{id}") public Map<String,Object> update(@RequestHeader(value="X-Admin-Token",required=false) String token,@PathVariable String id,@RequestBody Map<String,Object> body){admin(token);return parameters.adminUpdate(id,body==null?Collections.<String,Object>emptyMap():body);}
    @DeleteMapping("/{id}") public Map<String,Object> delete(@RequestHeader(value="X-Admin-Token",required=false) String token,@PathVariable String id){admin(token);parameters.adminSoftDelete(id);return Collections.<String,Object>singletonMap("deleted",true);}

    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
