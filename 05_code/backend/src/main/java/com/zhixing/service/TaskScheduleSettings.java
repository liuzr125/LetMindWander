package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import com.zhixing.mapper.CollectionScheduleSettingsMapper;
import com.zhixing.mapper.ContentCollectionMapper;
import com.zhixing.model.CollectionScheduleView;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

/** Database-backed plans; all task dates and cron calculations use Beijing time. */
@Service
public class TaskScheduleSettings {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private final AppParameterService parameters;
  private final AppProperties properties;
  private final ContentCollectionMapper collections;
  private final CollectionScheduleSettingsMapper schedules;
  private final String pricingDefault;

  public TaskScheduleSettings(
      AppParameterService parameters,
      AppProperties properties,
      ContentCollectionMapper collections,
      CollectionScheduleSettingsMapper schedules,
      @Value("${app.ai.pricing-sync-cron:0 0 22 * * *}") String pricingDefault) {
    this.parameters = parameters;
    this.properties = properties;
    this.collections = collections;
    this.schedules = schedules;
    this.pricingDefault = pricingDefault;
  }

  public String cron(String code) {
    String value;
    switch (code) {
      case "collection":
        CollectionScheduleView row = collections.selectSchedule();
        value = row == null ? "0 0 22 * * *" : row.getCronExpression();
        break;
      case "article":
        value =
            parameters.optionalStored(
                "article_generation.cron", properties.getArticleGeneration().getCron());
        break;
      case "pricing":
        value = parameters.optionalStored("ai.pricing.deepseek.cron", pricingDefault);
        break;
      default:
        throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "定时任务不存在");
    }
    return validate(value);
  }

  public String validate(String value) {
    String cron = value == null ? "" : value.trim().replaceAll("\\s+", " ");
    if (cron.length() > 80 || cron.split(" ").length != 6) throw invalid("请输入六段 Cron：秒 分 时 日 月 周");
    try {
      CronExpression expression = CronExpression.parse(cron);
      if (expression.next(ZonedDateTime.now(ZONE)) == null) throw invalid("Cron 执行计划没有可用的下次运行时间");
    } catch (IllegalArgumentException exception) {
      throw invalid("Cron 表达式无效，请检查秒、分、时、日、月、周");
    }
    return cron;
  }

  public String save(String code, String value) {
    String cron = validate(value);
    switch (code) {
      case "collection":
        if (schedules.updateCron(cron, nextFromCron(cron, Instant.now())) != 1)
          throw new ApiException(
              HttpStatus.CONFLICT, "COLLECTION_SCHEDULE_MISSING", "请先导入采集任务的数据库迁移");
        break;
      case "article":
        parameters.saveValue("article_generation.cron", cron, "每日分词书英语短文执行计划（六段 Cron，北京时间）");
        break;
      case "pricing":
        parameters.saveValue("ai.pricing.deepseek.cron", cron, "AI 价格同步执行计划（六段 Cron，北京时间）");
        break;
      default:
        throw new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "定时任务不存在");
    }
    return cron;
  }

  public Instant next(String code, Instant from) {
    return nextFromCron(cron(code), from);
  }

  private Instant nextFromCron(String cron, Instant from) {
    ZonedDateTime next = CronExpression.parse(cron).next(from.atZone(ZONE));
    return next == null ? null : next.toInstant();
  }

  private ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_CRON", message);
  }
}
