package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.mapper.AdminStudyRecordMapper;
import com.zhixing.model.AdminStudyDayView;
import com.zhixing.model.AdminStudyRecordDetailView;
import com.zhixing.model.AdminStudyRecordPageView;
import com.zhixing.model.AdminStudyRecordView;
import com.zhixing.model.AdminStudyResetView;
import com.zhixing.model.AdminStudyWordPageView;
import com.zhixing.model.AdminStudyWordView;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 管理端「词书学习记录」：按「用户 × 词书 × 轮次」查看学习记录、每日明细与词条。 一轮 = 一次选定这本词书：切走时冻结（结束时间 +
 * 已学快照），切回会新开一轮并把上一轮的已学/未学带过来。 数据来源：vocabulary_book_study_record（轮次）/
 * vocabulary_book_study_daily（每日动作） + learning_record ×
 * vocabulary_book_word（已学/掌握/学习中词数、本轮新学词数，实时统计）。
 */
@Service
public class AdminStudyRecordService {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private static final List<String> STATUSES =
      Arrays.asList("all", "learned", "mastered", "learning");
  private static final List<String> SCOPES = Arrays.asList("round", "book");
  /** 已学词数：进行中的轮次看实时，已结束的轮次看结束快照（老记录不再被改写） */
  private final AdminStudyRecordMapper mapper;

  public AdminStudyRecordService(AdminStudyRecordMapper mapper) {
    this.mapper = mapper;
  }

  public AdminStudyRecordPageView page(
      Integer rawPage,
      Integer rawPageSize,
      String rawKeyword,
      String rawBookId,
      String rawDateFrom,
      String rawDateTo) {
    int page = rawPage == null ? 1 : rawPage, pageSize = rawPageSize == null ? 20 : rawPageSize;
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始，每页可显示 1 至 100 条记录");
    String keyword = rawKeyword == null ? "" : rawKeyword.trim(), bookId = clean(rawBookId);
    if (keyword.length() > 60)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD", "搜索词最多 60 个字符");
    LocalDate from = date(rawDateFrom), to = date(rawDateTo);
    if (from != null && to != null && from.isAfter(to))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
    Map<String, Object> filter = new LinkedHashMap<String, Object>();
    filter.put("bookId", bookId);
    filter.put("keyword", keyword);
    filter.put("from", from == null ? null : Timestamp.valueOf(from.atStartOfDay()));
    filter.put("until", to == null ? null : Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
    int total = mapper.countFilteredRecords(filter);
    List<AdminStudyRecordView> items = new ArrayList<AdminStudyRecordView>();
    for (Map<String, Object> row : mapper.selectRecordPage(filter, (page - 1) * pageSize, pageSize))
      items.add(view(row));
    AdminStudyRecordPageView out = new AdminStudyRecordPageView();
    out.setTotal(total);
    out.setPage(page);
    out.setPageSize(pageSize);
    out.setTotalPages(total == 0 ? 0 : (total + pageSize - 1) / pageSize);
    out.setKeyword(keyword);
    out.setBookId(bookId);
    out.setDateFrom(from == null ? null : from.toString());
    out.setDateTo(to == null ? null : to.toString());
    out.setItems(items);
    Map<String, Object> summary = new LinkedHashMap<String, Object>();
    summary.put("records", total);
    summary.put("users", mapper.countFilteredOwners(filter));
    summary.put("books", mapper.countFilteredBooks(filter));
    summary.put("ongoing", mapper.countFilteredOngoingRecords(filter));
    summary.put(
        "todayStudied",
        mapper.countStudiedSince(Timestamp.valueOf(LocalDate.now(BUSINESS_ZONE).atStartOfDay())));
    out.setSummary(summary);
    return out;
  }

  public AdminStudyRecordDetailView detail(String rawRecordId) {
    String recordId = clean(rawRecordId);
    if (recordId == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDY_RECORD_ID", "学习记录 ID 不能为空");
    List<Map<String, Object>> rows = mapper.selectRecordById(recordId);
    if (rows.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_STUDY_RECORD_NOT_FOUND", "学习记录不存在或已被删除");
    AdminStudyRecordDetailView out = new AdminStudyRecordDetailView();
    AdminStudyRecordView record = view(rows.get(0));
    out.setRecord(record);
    out.setActiveDays(record.getStudyDayCount());
    out.setDays(days(record));
    out.setRoundNewWords(roundNewWords(record));
    out.setResets(resets(record.getId()));
    return out;
  }

  public AdminStudyWordPageView words(
      String rawRecordId, Integer rawPage, Integer rawPageSize, String rawStatus, String rawScope) {
    String recordId = clean(rawRecordId);
    if (recordId == null)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STUDY_RECORD_ID", "学习记录 ID 不能为空");
    int page = rawPage == null ? 1 : rawPage, pageSize = rawPageSize == null ? 20 : rawPageSize;
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始，每页可显示 1 至 100 条词条");
    String status = rawStatus == null ? "all" : rawStatus.trim(),
        scope = rawScope == null ? "round" : rawScope.trim();
    if (!STATUSES.contains(status))
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "INVALID_STUDY_WORD_STATUS",
          "词条状态仅支持 all、learned、mastered 或 learning");
    if (!SCOPES.contains(scope))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_STUDY_WORD_SCOPE", "词条范围仅支持 round（本轮新学）或 book（这本书全部）");
    List<Map<String, Object>> rounds = mapper.selectRecordById(recordId);
    if (rounds.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "ADMIN_STUDY_RECORD_NOT_FOUND", "学习记录不存在或已被删除");
    AdminStudyRecordView record = view(rounds.get(0));
    Map<String, Object> filter = roundFilter(record);
    filter.put("scope", scope);
    filter.put("status", status);
    int total = mapper.countRecordWords(filter);
    List<AdminStudyWordView> items = new ArrayList<AdminStudyWordView>();
    for (Map<String, Object> row :
        mapper.selectRecordWords(filter, (page - 1) * pageSize, pageSize)) items.add(word(row));
    AdminStudyWordPageView out = new AdminStudyWordPageView();
    out.setTotal(total);
    out.setPage(page);
    out.setPageSize(pageSize);
    out.setTotalPages(total == 0 ? 0 : (total + pageSize - 1) / pageSize);
    out.setStatus(status);
    out.setScope(scope);
    out.setItems(items);
    return out;
  }

  /** 过滤下拉用的词书列表（只给本书记录页用，避免依赖「英语单词」菜单权限）。 */
  public List<Map<String, Object>> books() {
    List<Map<String, Object>> out = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> row : mapper.selectActiveBooks()) {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", text(row, "id"));
      item.put("name", text(row, "book_name"));
      item.put("levelLabel", levelLabel(text(row, "level_code")));
      out.add(item);
    }
    return out;
  }

  /** 本轮新学词数：学习记录完成时间落在本轮窗口内的词条数。 */
  private Integer roundNewWords(AdminStudyRecordView record) {
    if (record.getSelectedAt() == null) return 0;
    return mapper.countRoundNewWords(roundFilter(record));
  }

  /** 「重新学习」记录：这一轮里每次重置的时间、词数与取消的复习排期数。 */
  private List<AdminStudyResetView> resets(String roundId) {
    List<AdminStudyResetView> out = new ArrayList<AdminStudyResetView>();
    for (Map<String, Object> row : mapper.selectRoundResets(roundId)) {
      AdminStudyResetView item = new AdminStudyResetView();
      item.setResetAt(instant(row, "reset_at"));
      item.setResetCount(number(row, "reset_count"));
      item.setPausedReviewCount(number(row, "paused_review_count"));
      out.add(item);
    }
    return out;
  }

  /** 每日明细：本轮窗口内的「当天新学」（按学习记录实时统计）+ 记录下来的学习/复习动作次数。 */
  private List<AdminStudyDayView> days(AdminStudyRecordView record) {
    List<AdminStudyDayView> out = new ArrayList<AdminStudyDayView>();
    Map<String, AdminStudyDayView> merged = new LinkedHashMap<String, AdminStudyDayView>();
    Map<String, Object> filter = roundFilter(record);
    if (record.getSelectedAt() == null) filter.put("selectedAt", Timestamp.from(Instant.now()));
    for (Map<String, Object> row : mapper.selectDailyNewWords(filter)) {
      String day = String.valueOf(raw(row, "business_day"));
      AdminStudyDayView item = new AdminStudyDayView();
      item.setBusinessDate(day);
      item.setNewWordCount(number(row, "new_words"));
      item.setStudyCount(0);
      item.setReviewedCount(0);
      merged.put(day, item);
    }
    for (Map<String, Object> row : mapper.selectRoundDailyActions(record.getId())) {
      String day = String.valueOf(raw(row, "business_date"));
      AdminStudyDayView item = merged.get(day);
      if (item == null) {
        item = new AdminStudyDayView();
        item.setBusinessDate(day);
        item.setNewWordCount(0);
        merged.put(day, item);
      }
      item.setStudyCount(number(row, "study_count"));
      item.setReviewedCount(number(row, "reviewed_count"));
    }
    List<String> keys = new ArrayList<String>(merged.keySet());
    keys.sort((a, b) -> b.compareTo(a));
    int limit = Math.min(keys.size(), 90);
    for (int i = 0; i < limit; i++) out.add(merged.get(keys.get(i)));
    return out;
  }

  private AdminStudyRecordView view(Map<String, Object> row) {
    AdminStudyRecordView out = new AdminStudyRecordView();
    out.setId(text(row, "id"));
    out.setOwnerId(text(row, "owner_id"));
    out.setNickname(text(row, "nickname"));
    out.setShortId(text(row, "short_id"));
    out.setMobile(maskMobile(text(row, "mobile")));
    out.setBookId(text(row, "book_id"));
    out.setBookName(text(row, "book_name"));
    out.setBookType(text(row, "book_type"));
    String level = text(row, "level_code");
    out.setLevelCode(level);
    out.setLevelLabel(levelLabel(level));
    out.setRoundNo(number(row, "round_no"));
    out.setSelectedAt(instant(row, "selected_at"));
    out.setEndedAt(instant(row, "ended_at"));
    out.setCarriedLearnedCount(number(row, "carried_learned_count"));
    out.setResetTimes(number(row, "reset_times"));
    out.setResetWordTotal(number(row, "reset_word_total"));
    out.setLastResetAt(instant(row, "last_reset_at"));
    int total = number(row, "total_words") == null ? 0 : number(row, "total_words");
    int learned = number(row, "learned_words") == null ? 0 : number(row, "learned_words");
    out.setTotalWords(total);
    out.setLearnedWords(learned);
    out.setMasteredWords(number(row, "mastered_words"));
    out.setLearningWords(number(row, "learning_words"));
    out.setCompletionPercent(total == 0 ? 0 : (int) Math.round(learned * 100.0 / total));
    out.setStudyDayCount(number(row, "study_day_count"));
    out.setStudyCount(number(row, "study_count"));
    out.setReviewedCount(number(row, "reviewed_count"));
    out.setOngoing(Boolean.TRUE.equals(bool(row, "current_book")));
    out.setCurrentBook(Boolean.TRUE.equals(bool(row, "current_book")));
    out.setCurrentRound(Boolean.TRUE.equals(bool(row, "current_book")) && out.getEndedAt() == null);
    out.setFirstStudiedAt(instant(row, "first_studied_at"));
    out.setLastStudiedAt(instant(row, "last_studied_at"));
    return out;
  }

  private AdminStudyWordView word(Map<String, Object> row) {
    AdminStudyWordView out = new AdminStudyWordView();
    out.setContentId(text(row, "content_id"));
    out.setWord(text(row, "word"));
    out.setMeaning(text(row, "meaning"));
    out.setPhonetic(text(row, "phonetic"));
    String difficulty = text(row, "difficulty");
    out.setDifficulty(difficulty);
    out.setDifficultyLabel("advanced".equals(difficulty) ? "进阶" : "入门");
    String status = text(row, "learning_status");
    out.setLearningStatus(status);
    out.setStatusLabel(statusLabel(status));
    out.setFamiliarityPercent(number(row, "familiarity_percent"));
    out.setInNotebook(Boolean.TRUE.equals(bool(row, "in_notebook")));
    out.setFirstCompletedAt(instant(row, "first_completed_at"));
    out.setLastFeedbackAt(instant(row, "last_feedback_at"));
    return out;
  }

  private String statusLabel(String status) {
    if ("mastered".equals(status)) return "已掌握";
    if ("understood".equals(status)) return "已学会";
    if ("learning".equals(status)) return "学习中";
    return "未学习";
  }

  private String levelLabel(String level) {
    if (level == null) return "未分级";
    if ("primary".equals(level)) return "小学";
    if ("junior".equals(level)) return "初中";
    if ("senior".equals(level)) return "高中";
    if ("cet4".equals(level)) return "大学英语四级";
    if ("cet6".equals(level)) return "大学英语六级";
    if ("kaoyan".equals(level) || "postgraduate".equals(level) || "postgrad".equals(level))
      return "考研";
    if ("ielts".equals(level)) return "雅思";
    if ("toefl".equals(level)) return "托福";
    if ("gre".equals(level)) return "GRE";
    if ("oxford3000".equals(level)) return "Oxford 3000";
    if ("oxford5000".equals(level)) return "Oxford 5000";
    if ("computer".equals(level)) return "计算机英语";
    if ("unclassified".equals(level)) return "未分级";
    return level;
  }

  private Map<String, Object> roundFilter(AdminStudyRecordView record) {
    Map<String, Object> filter = new LinkedHashMap<String, Object>();
    filter.put("ownerId", record.getOwnerId());
    filter.put("bookId", record.getBookId());
    filter.put(
        "selectedAt",
        record.getSelectedAt() == null ? null : Timestamp.from(record.getSelectedAt()));
    filter.put("endedAt", record.getEndedAt() == null ? null : Timestamp.from(record.getEndedAt()));
    return filter;
  }

  private LocalDate date(String raw) {
    String value = clean(raw);
    if (value == null) return null;
    try {
      return LocalDate.parse(value);
    } catch (Exception e) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE", "日期格式必须为 YYYY-MM-DD");
    }
  }

  private String maskMobile(String mobile) {
    if (mobile == null || mobile.isEmpty()) return null;
    if (mobile.length() < 7) return "***";
    return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
  }

  private Integer number(Map<String, Object> row, String key) {
    Object value = raw(row, key);
    if (value == null) return null;
    return value instanceof Number
        ? ((Number) value).intValue()
        : Integer.valueOf(String.valueOf(value));
  }

  private Boolean bool(Map<String, Object> row, String key) {
    Object value = raw(row, key);
    if (value == null) return null;
    if (value instanceof Boolean) return (Boolean) value;
    if (value instanceof Number) return ((Number) value).intValue() != 0;
    return Boolean.valueOf(String.valueOf(value));
  }

  private String text(Map<String, Object> row, String key) {
    Object value = raw(row, key);
    return value == null ? null : String.valueOf(value);
  }

  private Instant instant(Map<String, Object> row, String key) {
    Object value = raw(row, key);
    if (value instanceof Timestamp) return ((Timestamp) value).toInstant();
    if (value instanceof java.time.LocalDateTime)
      return ((java.time.LocalDateTime) value).atZone(BUSINESS_ZONE).toInstant();
    if (value instanceof java.time.LocalDate)
      return ((java.time.LocalDate) value).atStartOfDay(BUSINESS_ZONE).toInstant();
    return value instanceof Instant ? (Instant) value : null;
  }

  private Object raw(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value == null)
      for (Map.Entry<String, Object> entry : row.entrySet())
        if (entry.getKey().equalsIgnoreCase(key)) value = entry.getValue();
    return value;
  }

  private String clean(String raw) {
    String value = raw == null ? null : raw.trim();
    return value == null || value.isEmpty() ? null : value;
  }
}
