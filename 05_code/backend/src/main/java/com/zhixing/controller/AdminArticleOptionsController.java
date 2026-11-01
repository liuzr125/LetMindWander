package com.zhixing.controller;

import com.zhixing.model.AdminVocabularyBookView;
import com.zhixing.service.AdminContentService;
import com.zhixing.service.AdminVocabularyService;
import com.zhixing.service.AliyunTtsService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

/** 短文页面专属配置与依赖，同属 articles 权限；不返回密钥或其他业务统计。 */
@RestController
@RequestMapping("/api/admin/content/articles")
public class AdminArticleOptionsController {
  private final AdminContentService contents;
  private final AdminVocabularyService vocabulary;
  private final AliyunTtsService tts;

  public AdminArticleOptionsController(
      AdminContentService contents, AdminVocabularyService vocabulary, AliyunTtsService tts) {
    this.contents = contents;
    this.vocabulary = vocabulary;
    this.tts = tts;
  }

  @GetMapping("/options")
  public Map<String, Object> options() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("articleDifficulties", contents.articleCoverage());
    List<Map<String, String>> books = new ArrayList<>();
    for (AdminVocabularyBookView book : vocabulary.books()) {
      Map<String, String> item = new LinkedHashMap<>();
      item.put("bookId", book.getBookId());
      item.put("bookName", book.getBookName());
      books.add(item);
    }
    result.put("books", books);
    result.put("audioSettings", tts.articleVoiceSettings());
    return result;
  }

  @GetMapping("/tts-status")
  public Map<String, Object> speechStatus() {
    Map<String, Object> result = new LinkedHashMap<>(tts.articleVoiceSettings());
    result.put("appKeyConfigured", tts.articleAppKeyConfigured());
    return result;
  }

  @PutMapping("/tts-settings")
  public Map<String, Object> saveSpeechSettings(@RequestBody Map<String, Object> body) {
    Object voice = body.get("voice");
    return tts.saveArticleVoice(voice instanceof String ? (String) voice : null);
  }
}
