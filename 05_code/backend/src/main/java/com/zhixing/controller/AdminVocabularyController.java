package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.*;
import com.zhixing.service.AdminVocabularyService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vocabulary-books")
public class AdminVocabularyController {
  private final AdminVocabularyService books;
  private final AppProperties properties;

  public AdminVocabularyController(AdminVocabularyService books, AppProperties properties) {
    this.books = books;
    this.properties = properties;
  }

  /** 列出管理端可查看的词书及基本统计。 */
  @GetMapping
  public List<AdminVocabularyBookView> books(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    return books.books();
  }

  /** 按关键词和学习阶段分页查询指定词书的单词。 */
  @GetMapping("/{bookId}/words")
  public AdminVocabularyWordPageView words(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String bookId,
      @RequestParam(defaultValue = "1") Integer page,
      @RequestParam(defaultValue = "20") Integer pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String stage) {
    admin(token);
    return books.page(bookId, page, pageSize, keyword, stage);
  }

  /** 读取指定词条的完整学习内容。 */
  @GetMapping("/words/{contentId}")
  public ContentDetailView detail(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId) {
    admin(token);
    return books.detail(properties.getAdminPrincipalId(), contentId);
  }

  /** 为指定词条获取或生成可用发音。 */
  @PostMapping("/words/{contentId}/speech")
  public java.util.Map<String, Object> speech(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String contentId) {
    admin(token);
    return books.speech(properties.getAdminPrincipalId(), contentId);
  }

  private void admin(String token) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), token))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
