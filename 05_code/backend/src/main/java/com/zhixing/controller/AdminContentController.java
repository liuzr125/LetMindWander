package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AdminTechnicalContentPageView;
import com.zhixing.model.AdminTechnicalContentView;
import com.zhixing.model.ContentCoverageView;
import com.zhixing.service.AdminContentService;
import com.zhixing.service.AliyunTtsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {
  private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger(AdminContentController.class);
  private final AdminContentService contents;
  private final AppProperties properties;
  private final AliyunTtsService tts;

  public AdminContentController(
      AdminContentService contents, AppProperties properties, AliyunTtsService tts) {
    this.contents = contents;
    this.properties = properties;
    this.tts = tts;
  }

  /** 读取已发布内容及主题覆盖统计。 */
  @GetMapping("/coverage")
  public ContentCoverageView coverage(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=coverage");
    return contents.coverage();
  }

  /** 验证并保存技术主题达标数量，返回更新后的覆盖统计。 */
  @PutMapping("/coverage/target")
  public ContentCoverageView target(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestBody java.util.Map<String, Object> body) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=target");
    Object raw = body.get("target");
    if (!(raw instanceof Number) || ((Number) raw).doubleValue() != ((Number) raw).intValue())
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "INVALID_TOPIC_TARGET", "达标数量必须为 1 至 10000 的整数");
    return contents.updateTechnicalTarget(((Number) raw).intValue());
  }

  /** 按主题、关键词和来源日期分页读取技术知识。 */
  @GetMapping("/technical")
  public AdminTechnicalContentPageView technical(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(required = false) String topic,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String startDate,
      @RequestParam(required = false) String endDate,
      @RequestParam(defaultValue = "desc") String sortOrder,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=technical");
    return contents.technical(topic, keyword, startDate, endDate, sortOrder, page, pageSize);
  }

  /** 读取已发布技术知识正文及所属主题。 */
  @GetMapping("/technical/{contentId}")
  public AdminTechnicalContentView technicalDetail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=technicalDetail");
    return contents.technicalDetail(contentId);
  }

  /** 按难度、词书、关键词和音频状态分页读取短文。 */
  @GetMapping("/articles")
  public AdminTechnicalContentPageView articles(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @RequestParam(required = false) String difficulty,
      @RequestParam(required = false) String bookId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String audioStatus,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=articles");
    return contents.articles(difficulty, bookId, keyword, audioStatus, page, pageSize);
  }

  /** 读取已发布短文详情并附可访问音频地址。 */
  @GetMapping("/articles/{contentId}")
  public AdminTechnicalContentView articleDetail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=articleDetail");
    return contents.articleDetail(contentId);
  }

  /** 更新短文的词书分类；空词书标识表示清除人工分类。 */
  @PutMapping("/articles/{contentId}/category")
  public AdminTechnicalContentView updateArticleCategory(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId,
      @RequestBody(required = false) ArticleCategoryRequest request) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=updateArticleCategory");
    return contents.updateArticleCategory(
        properties.getAdminPrincipalId(), contentId, request == null ? null : request.getBookId());
  }

  /** 在事务内更新短文分类、难度及预计阅读时长。 */
  @PutMapping("/articles/{contentId}/metadata")
  public AdminTechnicalContentView updateArticleMetadata(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId,
      @RequestBody(required = false) ArticleMetadataRequest request) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=updateArticleMetadata");
    if (request == null)
      throw new ApiException(
          HttpStatus.BAD_REQUEST, "ARTICLE_METADATA_REQUIRED", "请提交短文分类、难度和阅读时长");
    return contents.updateArticleMetadata(
        properties.getAdminPrincipalId(),
        contentId,
        request.getBookId(),
        request.getDifficulty(),
        request.getEstimatedMinutes());
  }

  /** 读取生产语音配置的可用状态，不返回调用凭据。 */
  @GetMapping("/tts-status")
  public java.util.Map<String, Object> ttsStatus(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=ttsStatus");
    return tts.productionStatus();
  }

  /** 验证短文后使用本次临时 Token 生成语音。 */
  @PostMapping("/articles/{contentId}/speech")
  public java.util.Map<String, Object> articleSpeech(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId,
      @RequestBody(required = false) ArticleSpeechRequest request) {
    admin(token);
    LOGGER.debug("admin_request controller=Content operation=articleSpeech");
    // Resolve a published article before invoking the external speech provider.
    contents.articleDetail(contentId);
    return tts.speechWithTemporaryToken(
        properties.getAdminPrincipalId(),
        contentId,
        request == null ? null : request.getNlsToken());
  }

  public static class ArticleSpeechRequest {
    private String nlsToken;

    public String getNlsToken() {
      return nlsToken;
    }

    public void setNlsToken(String nlsToken) {
      this.nlsToken = nlsToken;
    }
  }

  public static class ArticleCategoryRequest {
    private String bookId;

    public String getBookId() {
      return bookId;
    }

    public void setBookId(String bookId) {
      this.bookId = bookId;
    }
  }

  public static class ArticleMetadataRequest {
    private String bookId, difficulty;
    private Integer estimatedMinutes;

    public String getBookId() {
      return bookId;
    }

    public void setBookId(String bookId) {
      this.bookId = bookId;
    }

    public String getDifficulty() {
      return difficulty;
    }

    public void setDifficulty(String difficulty) {
      this.difficulty = difficulty;
    }

    public Integer getEstimatedMinutes() {
      return estimatedMinutes;
    }

    public void setEstimatedMinutes(Integer estimatedMinutes) {
      this.estimatedMinutes = estimatedMinutes;
    }
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
