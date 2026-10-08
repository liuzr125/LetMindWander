package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.AdminFeedbackMapper;
import com.zhixing.model.AdminFeedbackPageView;
import com.zhixing.model.AdminFeedbackView;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端「用户反馈」：查看用户提交的问题反馈正文与详情，并跟踪待处理状态。 状态：open=待处理（概览页「待处理事项」统计的就是它），handled=已处理。 */
@Service
public class AdminFeedbackService {
  private static final List<String> CATEGORIES =
      Arrays.asList("bug", "suggestion", "content_error", "copyright");
  private static final int EXCERPT_LENGTH = 80;
  private final AdminFeedbackMapper mapper;

  public AdminFeedbackService(AdminFeedbackMapper mapper) {
    this.mapper = mapper;
  }

  public AdminFeedbackPageView page(
      Integer rawPage, Integer rawPageSize, String rawState, String rawKeyword) {
    int page = rawPage == null ? 1 : rawPage, pageSize = rawPageSize == null ? 20 : rawPageSize;
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始，每页可显示 1 至 100 条反馈");
    String state = rawState == null ? "all" : rawState.trim(),
        keyword = rawKeyword == null ? "" : rawKeyword.trim();
    if (!"all".equals(state) && !"open".equals(state) && !"handled".equals(state))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_STATE", "反馈状态不正确");
    if (keyword.length() > 80)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD", "搜索词最多 80 个字符");
    int total = mapper.countFilteredFeedback(state, keyword);
    List<AdminFeedbackView> items = new ArrayList<AdminFeedbackView>();
    for (Map<String, Object> row :
        mapper.selectFeedbackPage(state, keyword, (page - 1) * pageSize, pageSize))
      items.add(view(row, true));
    AdminFeedbackPageView out = new AdminFeedbackPageView();
    out.setTotal(total);
    out.setPage(page);
    out.setPageSize(pageSize);
    out.setTotalPages(total == 0 ? 0 : (total + pageSize - 1) / pageSize);
    out.setState(state);
    out.setKeyword(keyword);
    out.setItems(items);
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("open", count("open"));
    summary.put("handled", count("handled"));
    summary.put("total", count(null));
    out.setSummary(summary);
    return out;
  }

  public AdminFeedbackView detail(String rawId) {
    String id = clean(rawId);
    if (id == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_ID", "反馈 ID 不能为空");
    List<Map<String, Object>> rows = mapper.selectFeedbackById(id);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_FEEDBACK_NOT_FOUND", "反馈不存在或已被删除");
    return view(rows.get(0), false);
  }

  /** 标记已处理 / 重新打开；不引入新的回复字段，仅推进状态并留审计。 */
  @Transactional
  public AdminFeedbackView setState(String rawId, String rawState, String adminId) {
    String id = clean(rawId), state = clean(rawState);
    if (id == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_ID", "反馈 ID 不能为空");
    if (state == null || !("open".equals(state) || "handled".equals(state)))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_FEEDBACK_STATE", "反馈状态只能为 open 或 handled");
    int updated = mapper.updateFeedbackState(state, id);
    if (updated == 0)
      throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_FEEDBACK_NOT_FOUND", "反馈不存在或已被删除");
    mapper.insertFeedbackAudit(
        CryptoUtils.randomId(),
        adminId,
        "user_feedback_state_change",
        "user_feedback",
        id,
        "{\"state\":\"" + state + "\"}",
        java.sql.Timestamp.from(Instant.now().plus(180, ChronoUnit.DAYS)));
    return detail(id);
  }

  private int count(String state) {
    return mapper.countFeedbackByState(state);
  }

  private AdminFeedbackView view(Map<String, Object> row, boolean listMode) {
    AdminFeedbackView out = new AdminFeedbackView();
    String body = text(row, "body");
    out.setId(text(row, "id"));
    out.setOwnerId(text(row, "owner_id"));
    out.setNickname(text(row, "nickname"));
    out.setShortId(text(row, "short_id"));
    out.setMobile(maskMobile(text(row, "mobile")));
    String category = text(row, "category");
    out.setCategory(category);
    out.setCategoryLabel(categoryLabel(category));
    out.setState(text(row, "state"));
    out.setStateLabel(stateLabel(text(row, "state")));
    out.setBody(listMode ? null : body);
    out.setExcerpt(excerpt(body));
    out.setContentId(text(row, "content_id"));
    out.setContentTitle(text(row, "content_title"));
    out.setRequestId(text(row, "request_id"));
    out.setCreatedAt(instant(row, "created_at"));
    out.setUpdatedAt(instant(row, "updated_at"));
    return out;
  }

  private String excerpt(String body) {
    if (body == null) return null;
    int[] points = body.codePoints().limit(EXCERPT_LENGTH + 1).toArray();
    if (points.length <= EXCERPT_LENGTH) return body;
    return new String(points, 0, EXCERPT_LENGTH) + "…";
  }

  private String categoryLabel(String category) {
    if ("bug".equals(category)) return "功能异常";
    if ("suggestion".equals(category)) return "使用建议";
    if ("content_error".equals(category)) return "内容错误";
    if ("copyright".equals(category)) return "版权问题";
    return CATEGORIES.contains(category) ? category : "其他";
  }

  private String stateLabel(String state) {
    return "handled".equals(state) ? "已处理" : "待处理";
  }

  private String maskMobile(String mobile) {
    if (mobile == null || mobile.isEmpty()) return null;
    if (mobile.length() < 7) return "***";
    return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
  }

  private String text(Map<String, Object> row, String key) {
    Object raw = row.get(key);
    if (raw == null)
      for (Map.Entry<String, Object> entry : row.entrySet())
        if (entry.getKey().equalsIgnoreCase(key)) raw = entry.getValue();
    return raw == null ? null : String.valueOf(raw);
  }

  private Instant instant(Map<String, Object> row, String key) {
    Object raw = row.get(key);
    if (raw == null)
      for (Map.Entry<String, Object> entry : row.entrySet())
        if (entry.getKey().equalsIgnoreCase(key)) raw = entry.getValue();
    if (raw instanceof java.sql.Timestamp) return ((java.sql.Timestamp) raw).toInstant();
    if (raw instanceof java.time.LocalDateTime)
      return ((java.time.LocalDateTime) raw)
          .atZone(java.time.ZoneId.of("Asia/Shanghai"))
          .toInstant();
    return raw instanceof Instant ? (Instant) raw : null;
  }

  private String clean(String raw) {
    String value = raw == null ? null : raw.trim();
    return value == null || value.isEmpty() ? null : value;
  }
}
