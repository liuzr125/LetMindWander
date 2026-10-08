package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminStudyRecordDetailView;
import com.zhixing.model.AdminStudyRecordPageView;
import com.zhixing.model.AdminStudyWordPageView;
import com.zhixing.service.AdminStudyRecordService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 管理端「词书学习记录」：按「用户 × 词书 × 轮次」查看学习记录、每日明细与词条。 一轮 = 一次选定这本词书；切走即冻结，切回同一本书会新开一轮（带入上一轮的已学/未学）。 */
@RestController
@RequestMapping("/api/admin/study-records")
public class AdminStudyRecordController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminStudyRecordController.class);
  private final AdminStudyRecordService records;
  private final AppProperties properties;

  public AdminStudyRecordController(AdminStudyRecordService records, AppProperties properties) {
    this.records = records;
    this.properties = properties;
  }

  /** 按用户、词书及轮次起始日期分页读取学习记录和统计。 */
  @GetMapping
  public AdminStudyRecordPageView page(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String bookId,
      @RequestParam(required = false) String dateFrom,
      @RequestParam(required = false) String dateTo) {
    admin(token);
    LOGGER.debug("admin_request controller=StudyRecord operation=page");
    return records.page(page, pageSize, keyword, bookId, dateFrom, dateTo);
  }

  /** 读取学习记录页的词书筛选项，不依赖单词管理菜单。 */
  @GetMapping("/books")
  public List<Map<String, Object>> books(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    LOGGER.debug("admin_request controller=StudyRecord operation=books");
    return records.books();
  }

  /** 读取指定学习轮次及每日动作、新学词和重置明细。 */
  @GetMapping("/{recordId}")
  public AdminStudyRecordDetailView detail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String recordId) {
    admin(token);
    LOGGER.debug("admin_request controller=StudyRecord operation=detail");
    return records.detail(recordId);
  }

  /** 按本轮或整书范围及学习状态分页读取词条。 */
  @GetMapping("/{recordId}/words")
  public AdminStudyWordPageView words(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String recordId,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize,
      @RequestParam(defaultValue = "all") String status,
      @RequestParam(defaultValue = "round") String scope) {
    admin(token);
    LOGGER.debug("admin_request controller=StudyRecord operation=words");
    return records.words(recordId, page, pageSize, status, scope);
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
