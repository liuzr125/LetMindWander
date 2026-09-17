package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.ContentCoverageView;
import com.zhixing.model.AdminTechnicalContentPageView;
import com.zhixing.model.AdminTechnicalContentView;
import com.zhixing.service.AdminContentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {
    private final AdminContentService contents; private final AppProperties properties;
    public AdminContentController(AdminContentService contents,AppProperties properties){this.contents=contents;this.properties=properties;}
    @GetMapping("/coverage") public ContentCoverageView coverage(@RequestHeader(value="X-Admin-Token",required=false)String token){admin(token);return contents.coverage();}
    @GetMapping("/technical") public AdminTechnicalContentPageView technical(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(required=false)String topic,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize){admin(token);return contents.technical(topic,keyword,page,pageSize);}
    @GetMapping("/technical/{contentId}") public AdminTechnicalContentView technicalDetail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String contentId){admin(token);return contents.technicalDetail(contentId);}
    @GetMapping("/articles") public AdminTechnicalContentPageView articles(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(required=false)String difficulty,@RequestParam(required=false)String keyword,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize){admin(token);return contents.articles(difficulty,keyword,page,pageSize);}
    @GetMapping("/articles/{contentId}") public AdminTechnicalContentView articleDetail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String contentId){admin(token);return contents.articleDetail(contentId);}
    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
