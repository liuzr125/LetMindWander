package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminFeedbackPageView;
import com.zhixing.model.AdminFeedbackView;
import com.zhixing.service.AdminFeedbackService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 管理端「用户反馈」：查看待处理事项的内容与详情，并可标记处理状态。 */
@RestController
@RequestMapping("/api/admin/feedback")
public class AdminFeedbackController {
    private final AdminFeedbackService feedback;private final AppProperties properties;
    public AdminFeedbackController(AdminFeedbackService feedback,AppProperties properties){this.feedback=feedback;this.properties=properties;}
    @GetMapping public AdminFeedbackPageView page(@RequestHeader(value="X-Admin-Token",required=false)String token,@RequestParam(defaultValue="1")Integer page,@RequestParam(defaultValue="20")Integer pageSize,@RequestParam(defaultValue="all")String state,@RequestParam(required=false)String keyword){admin(token);return feedback.page(page,pageSize,state,keyword);}
    @GetMapping("/{id}") public AdminFeedbackView detail(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String id){admin(token);return feedback.detail(id);}
    @PutMapping("/{id}/state") public AdminFeedbackView state(@RequestHeader(value="X-Admin-Token",required=false)String token,@PathVariable String id,@RequestBody Map<String,Object> body){admin(token);Object state=body==null?null:body.get("state");return feedback.setState(id,state==null?null:String.valueOf(state),properties.getAdminPrincipalId());}
    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
