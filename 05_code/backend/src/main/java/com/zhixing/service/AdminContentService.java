package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.mapper.AdminContentMapper;
import com.zhixing.model.AdminTechnicalContentPageView;
import com.zhixing.model.AdminTechnicalContentView;
import com.zhixing.model.ContentCoverageView;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminContentService {
  private static final Pattern ARTICLE_NUMBER_BEFORE_COLON =
      Pattern.compile("(?<!\\d)(\\d+)(?=\\s*:)");
  private static final Pattern ANY_NUMBER = Pattern.compile("\\d+");
  private final AdminContentMapper contents;
  private final MediaService media;
  @org.springframework.beans.factory.annotation.Autowired private AppParameterService parameters;
  public static final String TECHNICAL_TARGET = "content.technical_topic_target";

  public int technicalTarget() {
    return parameters == null ? 25 : parameters.intValue(TECHNICAL_TARGET, 25, 1, 10000);
  }

  public ContentCoverageView updateTechnicalTarget(Integer value) {
    if (value == null || value < 1 || value > 10000)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_TOPIC_TARGET", "达标数量必须为 1 至 10000 的整数");
    parameters.saveValue(TECHNICAL_TARGET, String.valueOf(value), "每个系统主题已发布技术知识的达标目标数量");
    ContentCoverageView result = coverage();
    result.setTechnicalTarget(value);
    return result;
  }

  public AdminContentService(AdminContentMapper contents, MediaService media) {
    this.contents = contents;
    this.media = media;
  }

  public ContentCoverageView coverage() {
    ContentCoverageView view = new ContentCoverageView();
    view.setTechnicalTarget(technicalTarget());
    view.setPublishedTotal(contents.countPublished());
    view.setSourceCount(contents.countSources());
    view.setTechnicalTopics(contents.selectTechnicalTopics());
    view.setWordStages(contents.selectWordStages());
    List<ContentCoverageView.CoverageItem> articles = contents.selectArticleDifficulties();
    for (ContentCoverageView.CoverageItem item : articles) {
      if ("intro".equals(item.getName())) item.setName("入门");
      else if ("advanced".equals(item.getName())) item.setName("进阶");
    }
    view.setArticleDifficulties(articles);
    return view;
  }

  public AdminTechnicalContentPageView technical(
      String rawTopic, String rawKeyword, Integer rawPage, Integer rawPageSize) {
    return technical(rawTopic, rawKeyword, null, null, rawPage, rawPageSize);
  }

  public AdminTechnicalContentPageView technical(
      String rawTopic,
      String rawKeyword,
      String rawStartDate,
      String rawEndDate,
      Integer rawPage,
      Integer rawPageSize) {
    return technical(rawTopic, rawKeyword, rawStartDate, rawEndDate, "desc", rawPage, rawPageSize);
  }

  public AdminTechnicalContentPageView technical(
      String rawTopic,
      String rawKeyword,
      String rawStartDate,
      String rawEndDate,
      String rawSortOrder,
      Integer rawPage,
      Integer rawPageSize) {
    int page = rawPage == null ? 1 : rawPage, pageSize = rawPageSize == null ? 20 : rawPageSize;
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始，每页可显示 1 至 100 条内容");
    String topic = clean(rawTopic), keyword = clean(rawKeyword);
    String sortOrder = clean(rawSortOrder).toLowerCase(java.util.Locale.ROOT);
    if (sortOrder.isEmpty()) sortOrder = "desc";
    if (!"asc".equals(sortOrder) && !"desc".equals(sortOrder))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_SORT_ORDER", "来源日期排序仅支持 asc（升序）或 desc（降序）");
    if (topic.length() > 50 || keyword.length() > 80)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "主题或搜索关键词过长");
    java.time.LocalDate start = parseSourceDate(rawStartDate), end = parseSourceDate(rawEndDate);
    if (start != null && end != null && start.isAfter(end))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "开始日期不能晚于结束日期");
    java.time.ZoneId zone = java.time.ZoneId.of("Asia/Shanghai");
    java.sql.Timestamp from =
        start == null ? null : java.sql.Timestamp.from(start.atStartOfDay(zone).toInstant());
    java.sql.Timestamp until =
        end == null
            ? null
            : java.sql.Timestamp.from(end.plusDays(1).atStartOfDay(zone).toInstant());
    int total = contents.countTechnical(topic, keyword, from, until),
        totalPages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    int safePage = totalPages == 0 ? 1 : Math.min(page, totalPages);
    List<AdminTechnicalContentView> items =
        contents.selectTechnical(
            topic, keyword, from, until, sortOrder, (safePage - 1) * pageSize, pageSize);
    for (AdminTechnicalContentView item : items)
      item.setTopics(contents.selectTechnicalTopicNames(item.getVersionId()));
    AdminTechnicalContentPageView result = new AdminTechnicalContentPageView();
    result.setTotal(total);
    result.setPage(safePage);
    result.setPageSize(pageSize);
    result.setTotalPages(totalPages);
    result.setTopic(topic);
    result.setKeyword(keyword);
    result.setSortOrder(sortOrder);
    result.setItems(items);
    return result;
  }

  private java.time.LocalDate parseSourceDate(String raw) {
    if (raw == null || raw.trim().isEmpty()) return null;
    try {
      if (!raw.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException();
      java.time.LocalDate date = java.time.LocalDate.parse(raw);
      if (date.getYear() < 1900 || date.getYear() > 9998) throw new IllegalArgumentException();
      return date;
    } catch (Exception e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_SOURCE_DATE", "来源日期请使用 YYYY-MM-DD 格式，年份范围 1900–9998");
    }
  }

  public AdminTechnicalContentView technicalDetail(String contentId) {
    AdminTechnicalContentView detail = contents.selectTechnicalDetail(contentId);
    if (detail == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "TECH_CONTENT_NOT_FOUND", "技术知识不存在或未发布");
    detail.setTopics(contents.selectTechnicalTopicNames(detail.getVersionId()));
    return detail;
  }

  public AdminTechnicalContentPageView articles(
      String rawDifficulty,
      String rawBookId,
      String rawKeyword,
      Integer rawPage,
      Integer rawPageSize) {
    return articles(rawDifficulty, rawBookId, rawKeyword, "", rawPage, rawPageSize);
  }

  public AdminTechnicalContentPageView articles(
      String rawDifficulty,
      String rawBookId,
      String rawKeyword,
      String rawAudioStatus,
      Integer rawPage,
      Integer rawPageSize) {
    int page = rawPage == null ? 1 : rawPage, pageSize = rawPageSize == null ? 20 : rawPageSize;
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "页码从 1 开始，每页可显示 1 至 100 条内容");
    String difficulty = clean(rawDifficulty).toLowerCase(java.util.Locale.ROOT),
        bookId = clean(rawBookId),
        keyword = clean(rawKeyword),
        audioStatus = clean(rawAudioStatus).toLowerCase(java.util.Locale.ROOT);
    if (!difficulty.isEmpty() && !"intro".equals(difficulty) && !"advanced".equals(difficulty))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_DIFFICULTY", "短文难度只能是 intro 或 advanced");
    if (keyword.length() > 80)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "搜索关键词过长");
    if (bookId.length() > 32)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_FILTER", "词书筛选值不正确");
    if (!audioStatus.isEmpty()
        && !"has_audio".equals(audioStatus)
        && !"missing_audio".equals(audioStatus))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO_FILTER", "音频筛选值不正确");
    List<AdminTechnicalContentView> all =
        contents.selectArticles(difficulty, bookId, keyword, audioStatus);
    all.sort(
        Comparator.comparingInt((AdminTechnicalContentView item) -> articleNumber(item.getTitle()))
            .thenComparing(item -> item.getTitle() == null ? "" : item.getTitle())
            .thenComparing(item -> item.getContentId() == null ? "" : item.getContentId()));
    int total = all.size(), totalPages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
    int safePage = totalPages == 0 ? 1 : Math.min(page, totalPages);
    int from = Math.min((safePage - 1) * pageSize, total), to = Math.min(from + pageSize, total);
    List<AdminTechnicalContentView> items = all.subList(from, to);
    for (AdminTechnicalContentView item : items) attachArticleAudio(item);
    AdminTechnicalContentPageView result = new AdminTechnicalContentPageView();
    result.setTotal(total);
    result.setPage(safePage);
    result.setPageSize(pageSize);
    result.setTotalPages(totalPages);
    result.setTopic(difficulty);
    result.setKeyword(keyword);
    result.setItems(items);
    return result;
  }

  public AdminTechnicalContentView articleDetail(String contentId) {
    AdminTechnicalContentView detail = contents.selectArticleDetail(contentId);
    if (detail == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "ARTICLE_CONTENT_NOT_FOUND", "英语短文不存在或未发布");
    attachArticleAudio(detail);
    return detail;
  }

  @Transactional
  public AdminTechnicalContentView updateArticleCategory(
      String adminId, String rawContentId, String rawBookId) {
    String contentId = clean(rawContentId), bookId = clean(rawBookId);
    validateArticle(contentId);
    validateBook(bookId);
    saveArticleCategory(adminId, contentId, bookId);
    return articleDetail(contentId);
  }

  @Transactional
  public AdminTechnicalContentView updateArticleMetadata(
      String adminId,
      String rawContentId,
      String rawBookId,
      String rawDifficulty,
      Integer estimatedMinutes) {
    String contentId = clean(rawContentId),
        bookId = clean(rawBookId),
        difficulty = clean(rawDifficulty).toLowerCase(java.util.Locale.ROOT);
    String versionId = validateArticle(contentId);
    validateBook(bookId);
    if (!"intro".equals(difficulty) && !"advanced".equals(difficulty))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_DIFFICULTY", "短文难度只能是 intro 或 advanced");
    if (estimatedMinutes == null || estimatedMinutes < 1 || estimatedMinutes > 240)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_READING_TIME", "预计阅读时长只能是 1 至 240 分钟");
    saveArticleCategory(adminId, contentId, bookId);
    int changed =
        contents.updateArticleMetadata(difficulty, estimatedMinutes * 60, versionId, contentId);
    if (changed != 1)
      throw new ApiException(HttpStatus.CONFLICT, "ARTICLE_VERSION_CHANGED", "短文发布版本已变化，请刷新后重试");
    return articleDetail(contentId);
  }

  private String validateArticle(String contentId) {
    if (contentId.length() != 32)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARTICLE_ID", "英语短文标识不正确");
    List<String> versions = contents.selectPublishedArticleVersionIds(contentId);
    if (versions.isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "ARTICLE_CONTENT_NOT_FOUND", "英语短文不存在或未发布");
    return versions.get(0);
  }

  private void validateBook(String bookId) {
    if (bookId.isEmpty()) return;
    if (bookId.length() != 32)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BOOK_ID", "词书标识不正确");
    Integer bookCount = contents.countActiveBook(bookId);
    if (bookCount == null || bookCount == 0)
      throw new ApiException(HttpStatus.BAD_REQUEST, "ARTICLE_BOOK_NOT_AVAILABLE", "所选词书不存在或已停用");
  }

  private void saveArticleCategory(String adminId, String contentId, String bookId) {
    int updated =
        contents.updateArticleCategory(
            bookId.isEmpty() ? null : bookId, adminId, bookId.isEmpty() ? 1 : 0, contentId);
    if (updated == 0)
      contents.insertArticleCategory(
          contentId, bookId.isEmpty() ? null : bookId, adminId, bookId.isEmpty() ? 1 : 0);
  }

  private void attachArticleAudio(AdminTechnicalContentView item) {
    if (item.getArticleAudioAssetId() != null && !item.getArticleAudioAssetId().trim().isEmpty())
      item.setArticleAudioUrl(media.referenceUrl(item.getArticleAudioAssetId()));
  }

  private int articleNumber(String title) {
    if (title == null) return Integer.MAX_VALUE;
    Matcher preferred = ARTICLE_NUMBER_BEFORE_COLON.matcher(title);
    if (preferred.find()) return parseArticleNumber(preferred.group(1));
    Matcher fallback = ANY_NUMBER.matcher(title);
    return fallback.find() ? parseArticleNumber(fallback.group()) : Integer.MAX_VALUE;
  }

  private int parseArticleNumber(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException ignored) {
      return Integer.MAX_VALUE;
    }
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }
}
