package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AdminTopicService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import javax.servlet.http.HttpServletRequest;
import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminPrincipal;

@RestController
@RequestMapping("/api/admin/topics")
public class AdminTopicController {
    private final AdminTopicService topics;private final AppProperties properties;
    public AdminTopicController(AdminTopicService topics,AppProperties properties){this.topics=topics;this.properties=properties;}
    @GetMapping public List<Map<String,Object>> list(@RequestHeader(value="X-Admin-Token",required=false)String token){require(token);return topics.list();}
    @PostMapping public Map<String,Object> create(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestBody Map<String,String> body,HttpServletRequest request){require(token);topics.save(actor(request),null,body.get("name"),body.get("topicType"));return Collections.singletonMap("message","主题已创建");}
    @PutMapping("/{id}") public Map<String,Object> update(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String id,@RequestBody Map<String,String> body,HttpServletRequest request){require(token);topics.save(actor(request),id,body.get("name"),body.get("topicType"));return Collections.singletonMap("message","主题已保存");}
    @DeleteMapping("/{id}") public Map<String,Object> delete(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String id,HttpServletRequest request){require(token);topics.delete(actor(request),id);return Collections.singletonMap("message","主题已删除");}
    private String actor(HttpServletRequest request){Object value=request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE);return value instanceof AdminPrincipal?((AdminPrincipal)value).getAccountId():properties.getAdminPrincipalId();}
    private void require(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_REQUIRED","需要管理员登录");}
}
