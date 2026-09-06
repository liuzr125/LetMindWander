package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.ContentCoverageView;
import com.zhixing.service.AdminContentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {
    private final AdminContentService contents; private final AppProperties properties;
    public AdminContentController(AdminContentService contents,AppProperties properties){this.contents=contents;this.properties=properties;}
    @GetMapping("/coverage") public ContentCoverageView coverage(@RequestHeader(value="X-Admin-Token",required=false)String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");return contents.coverage();}
}
