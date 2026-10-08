package com.zhixing.controller;

import com.zhixing.common.*;
import com.zhixing.config.AppProperties;
import com.zhixing.service.OnlineVocabularyCatalogService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/vocabulary/online-books")
public class AdminOnlineVocabularyController {
  private final OnlineVocabularyCatalogService service;
  private final AppProperties properties;

  public AdminOnlineVocabularyController(
      OnlineVocabularyCatalogService service, AppProperties properties) {
    this.service = service;
    this.properties = properties;
  }

  /** 读取固定在线词书目录及同步状态。 */
  @GetMapping
  public List<Map<String, Object>> list(
      @RequestHeader(value = "X-Admin-Token", required = false) String token) {
    admin(token);
    return service.books();
  }

  /** 分页搜索当前词书快照中的词条。 */
  @GetMapping("/{code}/words")
  public Map<String, Object> words(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String code,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize,
      @RequestParam(required = false) String keyword) {
    admin(token);
    return service.words(code, page, pageSize, keyword);
  }

  /** 下载锁定版本的词表，校验完整性并保存不可变快照。 */
  @PostMapping("/{code}/sync")
  public Map<String, Object> sync(
      @RequestHeader(value = "X-Admin-Token", required = false) String token,
      @PathVariable String code) {
    admin(token);
    return service.sync(code, properties.getAdminPrincipalId());
  }

  private void admin(String t) {
    if (!CryptoUtils.constantTimeEquals(properties.getAdminToken(), t))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "管理员访问令牌无效");
  }
}
