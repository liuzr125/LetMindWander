package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.service.AiOfficialPricingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/overview")
public class AdminOverviewController {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private final JdbcTemplate jdbc;private final AppProperties properties;private final AiOfficialPricingService pricing;
    public AdminOverviewController(JdbcTemplate jdbc,AppProperties properties,AiOfficialPricingService pricing){this.jdbc=jdbc;this.properties=properties;this.pricing=pricing;}
    @GetMapping public Map<String,Object> overview(@RequestHeader(value="X-Admin-Token",required=false)String token){
        admin(token);LocalDate today=LocalDate.now(BUSINESS_ZONE);Map<String,Object> result=new LinkedHashMap<String,Object>();
        result.put("businessDate",today.toString());result.put("activeUsers",count("SELECT COUNT(*) FROM app_user WHERE status='active'"));
        result.put("publishedContent",count("SELECT COUNT(*) FROM learning_content WHERE state='published'"));
        result.put("publishedWords",count("SELECT COUNT(*) FROM learning_content WHERE state='published' AND content_type='word'"));
        result.put("publishedArticles",count("SELECT COUNT(*) FROM learning_content WHERE state='published' AND content_type='english_article'"));
        result.put("todayTasks",count("SELECT COUNT(*) FROM daily_task dt JOIN daily_package dp ON dp.id=dt.package_id WHERE dp.business_date=?",today));
        result.put("todayCompleted",count("SELECT COUNT(*) FROM daily_task dt JOIN daily_package dp ON dp.id=dt.package_id WHERE dp.business_date=? AND dt.status='DONE'",today));
        result.put("openFeedback",count("SELECT COUNT(*) FROM user_feedback WHERE state='open'"));
        result.put("availableInvites",count("SELECT COUNT(*) FROM invite_code WHERE status='available'"));
        result.put("monthAiSpend",amount("SELECT COALESCE(SUM(spent_amount),0) FROM ai_month_budget WHERE month_start=?",today.withDayOfMonth(1)));
        result.put("pricing",pricing.status());return result;
    }
    private int count(String sql,Object...args){Integer value=jdbc.queryForObject(sql,Integer.class,args);return value==null?0:value;}
    private BigDecimal amount(String sql,Object...args){BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class,args);return value==null?BigDecimal.ZERO:value;}
    private void admin(String token){if(!CryptoUtils.constantTimeEquals(properties.getAdminToken(),token))throw new ApiException(HttpStatus.UNAUTHORIZED,"ADMIN_UNAUTHORIZED","管理员访问令牌无效");}
}
