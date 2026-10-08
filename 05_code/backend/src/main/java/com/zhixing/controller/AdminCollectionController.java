package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.ArticleGenerationRunView;
import com.zhixing.model.ArticleGenerationStatusView;
import com.zhixing.model.CollectionRunView;
import com.zhixing.model.CollectionScheduleView;
import com.zhixing.service.AiOfficialPricingService;
import com.zhixing.service.ContentCollectionService;
import com.zhixing.service.DailyEnglishArticleService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/collection")
public class AdminCollectionController {
  private final ContentCollectionService collection;
  private final DailyEnglishArticleService articles;
  private final AiOfficialPricingService pricing;
  private final AppProperties properties;

  public AdminCollectionController(
      ContentCollectionService collection,
      DailyEnglishArticleService articles,
      AiOfficialPricingService pricing,
      AppProperties properties) {
    this.collection = collection;
    this.articles = articles;
    this.pricing = pricing;
    this.properties = properties;
  }

  /** 读取官方 RSS 采集任务配置及下次运行时间。 */
  @GetMapping("/schedule")
  public CollectionScheduleView schedule(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    CollectionScheduleView schedule = collection.schedule();
    if (schedule == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "COLLECTION_SCHEDULE_MISSING", "请先执行数据库定时采集脚本");
    return schedule;
  }

  /** 列出官方 RSS 采集运行账本。 */
  @GetMapping("/runs")
  public List<CollectionRunView> runs(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(value = "limit", defaultValue = "15") int limit) {
    require(token);
    return collection.runs(limit);
  }

  /** 读取一次采集的结果及实现中的采集规则。 */
  @GetMapping("/runs/{runId}/detail")
  public Map<String, Object> runDetail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String runId) {
    require(token);
    try {
      return collection.runDetail(runId);
    } catch (IllegalArgumentException exception) {
      throw new ApiException(
          HttpStatus.NOT_FOUND, "COLLECTION_RUN_NOT_FOUND", exception.getMessage());
    } catch (IllegalStateException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_UNAVAILABLE", exception.getMessage());
    }
  }

  /** 手动执行官方白名单 RSS 采集，新增内容保留待审核状态。 */
  @PostMapping("/run")
  public CollectionRunView run(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    try {
      return collection.runManually();
    } catch (IllegalStateException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_UNAVAILABLE", exception.getMessage());
    }
  }

  /** 启用或暂停官方 RSS 定时采集。 */
  @PutMapping("/schedule/state")
  public CollectionScheduleView scheduleState(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    require(token);
    Object raw = body == null ? null : body.get("enabled");
    if (!(raw instanceof Boolean))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COLLECTION_STATE", "任务状态必须是布尔值");
    try {
      return collection.updateEnabled((Boolean) raw);
    } catch (IllegalStateException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "COLLECTION_UNAVAILABLE", exception.getMessage());
    }
  }

  /** 读取每日英语文章生成配置和覆盖状态。 */
  @GetMapping("/article-generation/status")
  public ArticleGenerationStatusView articleStatus(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    return articles.status();
  }

  /** 列出英语文章生成运行账本。 */
  @GetMapping("/article-generation/runs")
  public List<ArticleGenerationRunView> articleRuns(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(value = "limit", defaultValue = "12") int limit) {
    require(token);
    return articles.runs(limit);
  }

  /** 读取文章生成运行详情及模型调用统计。 */
  @GetMapping("/article-generation/runs/{runId}/detail")
  public Map<String, Object> articleRunDetail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String runId) {
    require(token);
    return articles.runDetail(runId);
  }

  /** 调整每本词书每次生成的文章数量。 */
  @PutMapping("/article-generation/settings")
  public ArticleGenerationStatusView articleSettings(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    require(token);
    Object raw = body == null ? null : body.get("perBookCount");
    try {
      return articles.updatePerBookCount(Integer.parseInt(String.valueOf(raw)));
    } catch (NumberFormatException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTICLE_COUNT", "请输入 1 至 20 的整数");
    }
  }

  /** 启用或暂停每日英语文章生成。 */
  @PutMapping("/article-generation/state")
  public ArticleGenerationStatusView articleState(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    require(token);
    Object raw = body == null ? null : body.get("enabled");
    if (!(raw instanceof Boolean))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTICLE_STATE", "任务状态必须是布尔值");
    return articles.updateEnabled((Boolean) raw);
  }

  /** 手动生成当天缺失的词书英语文章。 */
  @PostMapping("/article-generation/run")
  public ArticleGenerationRunView articleRun(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    try {
      return articles.runManually();
    } catch (IllegalStateException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT, "ARTICLE_GENERATION_UNAVAILABLE", exception.getMessage());
    }
  }

  /** 补齐英语文章难度覆盖缺口。 */
  @PostMapping("/article-generation/fill")
  public Map<String, Object> articleFill(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    try {
      return articles.fillCoverage();
    } catch (IllegalStateException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT, "ARTICLE_GENERATION_UNAVAILABLE", exception.getMessage());
    }
  }

  /** 读取官方模型价格同步运行记录。 */
  @GetMapping("/pricing/runs")
  public List<Map<String, Object>> pricingRuns(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(value = "limit", defaultValue = "12") int limit) {
    require(token);
    return pricing.runs(limit);
  }

  /** 启用或暂停官方模型价格同步任务。 */
  @PutMapping("/pricing/state")
  public Map<String, Object> pricingState(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody Map<String, Object> body) {
    require(token);
    Object raw = body == null ? null : body.get("enabled");
    if (!(raw instanceof Boolean))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PRICING_STATE", "任务状态必须是布尔值");
    return pricing.updateEnabled((Boolean) raw);
  }

  /** 手动同步官方模型价格并记录运行结果。 */
  @PostMapping("/pricing/run")
  public Map<String, Object> pricingRun(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    require(token);
    try {
      return pricing.runManually();
    } catch (IllegalStateException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT, "PRICING_SYNC_UNAVAILABLE", exception.getMessage());
    }
  }

  private void require(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
