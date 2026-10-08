package com.zhixing.service;

import com.zhixing.mapper.AdminOverviewMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminOverviewService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private final AdminOverviewMapper mapper;
  private final AiOfficialPricingService pricing;

  public AdminOverviewService(AdminOverviewMapper mapper, AiOfficialPricingService pricing) {
    this.mapper = mapper;
    this.pricing = pricing;
  }

  public Map<String, Object> overview() {
    LocalDate today = LocalDate.now(BUSINESS_ZONE);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("businessDate", today.toString());
    result.put("activeUsers", mapper.countActiveUsers());
    result.put("publishedContent", mapper.countPublishedContent());
    result.put("publishedWords", mapper.countPublishedWords());
    result.put("publishedArticles", mapper.countPublishedArticles());
    result.put("todayTasks", mapper.countTasksForDate(today));
    result.put("todayCompleted", mapper.countCompletedTasksForDate(today));
    result.put("openFeedback", mapper.countOpenFeedback());
    result.put("availableInvites", mapper.countAvailableInvites());
    result.put("monthAiSpend", mapper.sumSpendForMonth(today.withDayOfMonth(1)));
    result.put("pricing", pricing.status());
    return result;
  }
}
