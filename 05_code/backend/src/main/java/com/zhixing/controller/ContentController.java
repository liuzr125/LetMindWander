package com.zhixing.controller;

import com.zhixing.dto.ContentActionRequest;
import com.zhixing.dto.FamiliarityRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ContentDetailView;
import com.zhixing.service.AliyunTtsService;
import com.zhixing.service.ArticleParagraphExplanationService;
import com.zhixing.service.ArticleWordGlossaryService;
import com.zhixing.service.ContentService;
import com.zhixing.service.FollowRecordingService;
import com.zhixing.service.SessionService;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/learning/contents")
public class ContentController {
  private final SessionService sessions;
  private final ContentService contents;
  private final AliyunTtsService tts;
  private final FollowRecordingService followRecordings;
  private final ArticleWordGlossaryService articleWords;
  private final ArticleParagraphExplanationService paragraphExplanations;

  public ContentController(
      SessionService sessions,
      ContentService contents,
      AliyunTtsService tts,
      FollowRecordingService followRecordings,
      ArticleWordGlossaryService articleWords,
      ArticleParagraphExplanationService paragraphExplanations) {
    this.sessions = sessions;
    this.contents = contents;
    this.tts = tts;
    this.followRecordings = followRecordings;
    this.articleWords = articleWords;
    this.paragraphExplanations = paragraphExplanations;
  }

  /** 读取当前用户可访问的已发布内容详情。 */
  @GetMapping("/{id}")
  public ContentDetailView get(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    return contents.get(user(auth), id);
  }

  /** 标记非单词内容已理解；单词必须通过学习反馈完成。 */
  @PostMapping("/{id}/understood")
  public ContentDetailView understood(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody(required = false) ContentActionRequest r) {
    return contents.understood(user(auth), id, r);
  }

  /** 保留旧单词反馈入口，校验后提示客户端改用单词学习接口。 */
  @PostMapping("/{id}/feedback")
  public ContentDetailView feedback(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody ContentActionRequest r) {
    return contents.wordFeedback(user(auth), id, r);
  }

  /** 设置当前用户的内容收藏状态。 */
  @PostMapping("/{id}/favorite")
  public ContentDetailView favorite(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody(required = false) ContentActionRequest r) {
    return contents.favorite(user(auth), id, r);
  }

  /** 设置当前用户的内容复习安排。 */
  @PostMapping("/{id}/review")
  public ContentDetailView review(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody(required = false) ContentActionRequest r) {
    return contents.review(user(auth), id, r);
  }

  /** 设置词条是否加入当前用户生词本。 */
  @PostMapping("/{id}/word-book")
  public ContentDetailView wordBook(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody(required = false) ContentActionRequest r) {
    return contents.wordBook(user(auth), id, r);
  }

  /** 更新当前用户对词条的熟悉度。 */
  @PutMapping("/{id}/familiarity")
  public ContentDetailView familiarity(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestBody FamiliarityRequest r) {
    return contents.familiarity(user(auth), id, r);
  }

  /** 获取词条或内容朗读所需的语音结果。 */
  @PostMapping("/{id}/speech")
  public java.util.Map<String, Object> speech(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    return tts.speech(user(auth), id);
  }

  /** 获取文章中指定单词的语音结果。 */
  @PostMapping("/{id}/words/{term}/speech")
  public java.util.Map<String, Object> articleWordSpeech(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @PathVariable String term) {
    return tts.speechArticleWord(user(auth), id, term);
  }

  /** 核验文章中出现的词头，优先复用正式词条或缓存，再补充学习释义。 */
  @PostMapping("/{id}/words/{term}/lookup")
  public Map<String, Object> articleWordLookup(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @PathVariable String term) {
    return articleWords.ensure(user(auth), id, term);
  }

  /** 读取或生成指定段落的讲解。 */
  @PostMapping("/{id}/paragraphs/{paragraphId}/explanation")
  public Map<String, Object> paragraphExplanation(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @PathVariable String paragraphId) {
    return paragraphExplanations.explain(user(auth), id, paragraphId);
  }

  /** 读取当前用户保存的跟读录音信息。 */
  @GetMapping("/{id}/follow-recording")
  public Map<String, Object> followRecording(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    return followRecordings.latest(user(auth), id);
  }

  /** 保存当前用户上传的跟读录音。 */
  @PostMapping(value = "/{id}/follow-recording", consumes = "multipart/form-data")
  public Map<String, Object> uploadFollowRecording(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id,
      @RequestPart("file") MultipartFile file,
      @RequestParam("durationMs") int durationMs)
      throws IOException {
    return followRecordings.upload(user(auth), id, durationMs, file.getInputStream());
  }

  /** 删除当前用户保存的跟读录音。 */
  @DeleteMapping("/{id}/follow-recording")
  public Map<String, Object> deleteFollowRecording(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @PathVariable String id) {
    return followRecordings.delete(user(auth), id);
  }

  private String user(String auth) {
    AuthenticatedSession session = sessions.requireUser(auth);
    return session.getUserId();
  }
}
