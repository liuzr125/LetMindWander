package com.zhixing.controller;

import com.zhixing.common.ApiException;
import com.zhixing.dto.UpdateProfileRequest;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.ProfileView;
import com.zhixing.service.MediaService;
import com.zhixing.service.ProfileService;
import com.zhixing.service.SessionService;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * F02 个人资料与头像接口： GET /api/user 获取本人完整资料 PUT /api/user 更新本人资料（手机号只读，不接受字段） POST /api/media/upload
 * 上传头像（multipart，JPEG/PNG/WebP ≤5MB） GET /api/media/{id} 读取媒体文件
 */
@RestController
@RequestMapping("/api")
public class UserController {
  private final SessionService sessions;
  private final ProfileService profiles;
  private final MediaService media;

  public UserController(SessionService sessions, ProfileService profiles, MediaService media) {
    this.sessions = sessions;
    this.profiles = profiles;
    this.media = media;
  }

  /** 返回当前登录用户的完整资料。 */
  @GetMapping({"/user", "/me"})
  public ProfileView me(
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    AuthenticatedSession session = sessions.requireUser(authorization);
    ProfileView view = profiles.findView(session.getUserId());
    if (view == null) throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "用户不存在");
    return view;
  }

  /** 按资料版本更新本人资料；手机号不接受客户端修改。 */
  @PutMapping({"/user", "/me"})
  public ProfileView updateMe(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody UpdateProfileRequest request) {
    AuthenticatedSession session = sessions.requireUser(authorization);
    return profiles.update(session.getUserId(), request);
  }

  /** 验证会话后上传头像，文件校验及媒体归属由服务层处理。 */
  @PostMapping("/media/upload")
  public MediaService.UploadResult uploadAvatar(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestPart("file") MultipartFile file) {
    AuthenticatedSession session = sessions.requireUser(authorization);
    if (file == null || file.isEmpty()) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_EMPTY", "请选择要上传的图片");
    }
    try {
      return media.uploadAvatar(
          session.getUserId(),
          file.getOriginalFilename(),
          file.getContentType(),
          file.getInputStream());
    } catch (java.io.IOException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_READ_FAILED", "文件读取失败，请重试");
    }
  }

  /** 按查看者权限返回目标用户可见资料。 */
  @GetMapping("/users/{id}/profile")
  public java.util.Map<String, Object> visibleProfile(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @PathVariable("id") String userId) {
    return profiles.findVisible(sessions.requireUser(authorization).getUserId(), userId);
  }

  /** 返回资料字段限制和北京时间日期，供表单校验。 */
  @GetMapping("/profile/rules")
  public java.util.Map<String, Object> profileRules() {
    java.util.Map<String, Object> rules = new java.util.LinkedHashMap<String, Object>();
    rules.put("nickname", 30);
    rules.put("realName", 50);
    rules.put("englishName", 100);
    rules.put("hobbyCount", 10);
    rules.put("hobbyLength", 20);
    rules.put("introduction", 500);
    rules.put("avatarBytes", 5 * 1024 * 1024);
    rules.put("today", java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString());
    return rules;
  }

  /** 媒体访问：302 跳转到 OSS 短时签名地址，数据库不存永久外链。 */
  @GetMapping("/media/{id}")
  public ResponseEntity<Void> loadMedia(
      @PathVariable("id") String mediaId,
      @RequestHeader(value = "Authorization", required = false) String authorization) {
    String ownerId = authorization == null ? null : sessions.requireUser(authorization).getUserId();
    String signed = media.signedUrl(mediaId, ownerId);
    if (signed == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.status(HttpStatus.FOUND)
        .header("Cache-Control", "private, no-store")
        .location(java.net.URI.create(signed))
        .build();
  }
}
