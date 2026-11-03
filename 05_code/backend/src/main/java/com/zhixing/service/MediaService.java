package com.zhixing.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.MediaPersistenceMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * F02 头像上传：仅 JPEG/PNG/WebP，单张 ≤5MB，服务端核验实际魔数而非扩展名。 文件直传阿里云 OSS，本地不存储文件内容；数据库仅保存 media_asset 元数据与
 * object_key。
 */
@Service
public class MediaService {
  private static final Logger LOGGER = LoggerFactory.getLogger(MediaService.class);
  private static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;
  private static final long MAX_FOLLOW_RECORDING_BYTES = 2L * 1024 * 1024;

  private final MediaPersistenceMapper persistence;
  private final AppParameterService parameters;

  @Value("${app.media.oss-endpoint}")
  private String ossEndpoint;

  @Value("${app.media.oss-bucket}")
  private String ossBucket;

  @Value("${app.media.public-base-url}")
  private String publicBaseUrl;

  public MediaService(MediaPersistenceMapper persistence, AppParameterService parameters) {
    this.persistence = persistence;
    this.parameters = parameters;
  }

  @Transactional
  public UploadResult uploadAvatar(
      String ownerId, String filename, String declaredMime, InputStream stream) {
    byte[] bytes;
    try {
      bytes = readLimited(stream, MAX_AVATAR_BYTES, "图片不能超过 5MB");
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_READ_FAILED", "文件读取失败，请重试");
    }
    if (bytes.length == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_EMPTY", "文件不能为空");
    }
    if (bytes.length > MAX_AVATAR_BYTES) {
      throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", "图片不能超过 5MB");
    }

    String actualMime = detectMime(bytes);
    if (actualMime == null) {
      throw new ApiException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_FORMAT_REJECTED", "仅支持 JPEG/PNG/WebP 格式图片");
    }

    com.zhixing.mapper.type.BinaryHash sha256 =
        new com.zhixing.mapper.type.BinaryHash(sha256(bytes));
    String id = CryptoUtils.randomId();
    String objectKey = "avatar/" + id + extensionOf(actualMime);

    putToOss(objectKey, actualMime, bytes);

    Timestamp now = Timestamp.from(Instant.now());
    persistence.insertAvatarAsset(
        id, ownerId, objectKey, actualMime, bytes.length, sha256, now, now);
    LOGGER.info("Avatar upload completed: bytes={}", bytes.length);

    String url = referenceUrl(id);
    return new UploadResult(id, url, signedUrl(id, ownerId), actualMime, bytes.length);
  }

  @Transactional
  public UploadResult uploadFollowRecording(String ownerId, InputStream stream) {
    byte[] bytes;
    try {
      bytes = readLimited(stream, MAX_FOLLOW_RECORDING_BYTES, "跟读录音不能超过 2MB");
    } catch (IOException exception) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_READ_FAILED", "录音读取失败，请重试");
    }
    if (bytes.length == 0) throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_EMPTY", "录音不能为空");
    if (!isMp3(bytes))
      throw new ApiException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_FORMAT_REJECTED", "跟读录音仅支持 MP3 格式");

    String id = CryptoUtils.randomId();
    String objectKey = "follow-recording/" + ownerId + "/" + id + ".mp3";
    putToOss(objectKey, "audio/mpeg", bytes);
    Timestamp now = Timestamp.from(Instant.now());
    persistence.insertFollowRecordingAsset(
        id,
        ownerId,
        objectKey,
        bytes.length,
        new com.zhixing.mapper.type.BinaryHash(sha256(bytes)),
        now,
        now);
    LOGGER.info("Follow recording upload completed: bytes={}", bytes.length);
    return new UploadResult(
        id, referenceUrl(id), signedUrl(id, ownerId), "audio/mpeg", bytes.length);
  }

  /** 供媒体代理访问：返回 OSS object 的签名 URL（短时有效）。头像需归属校验，学习音频公开可读。 */
  public String signedUrl(String mediaId, String viewerId) {
    List<Map<String, Object>> rows = persistence.selectAssetAccessDetails(mediaId);
    if (rows.isEmpty()) return null;
    Map<String, Object> row = rows.get(0);
    if (!"ready".equals(String.valueOf(raw(row, "state")))) return null;
    String purpose = String.valueOf(raw(row, "purpose"));
    String objectKey = String.valueOf(raw(row, "object_key"));

    // 学习词条/例句音频为公开内容，无需归属校验。
    if ("word_audio".equals(purpose)
        || "example_audio".equals(purpose)
        || "article_audio".equals(purpose)) {
      return presign(objectKey);
    }
    if ("follow_recording".equals(purpose)) {
      return viewerId != null && viewerId.equals(String.valueOf(raw(row, "owner_id")))
          ? presign(objectKey, 3600)
          : null;
    }
    if (!"avatar".equals(purpose)) return null;

    boolean owner = viewerId != null && viewerId.equals(String.valueOf(raw(row, "owner_id")));
    // Only avatars currently selected by an active user are public. Pending uploads remain private.
    if (!owner && persistence.countActiveAvatarReferences(referenceUrl(mediaId)) == 0) return null;
    return presign(objectKey);
  }

  private String presign(String objectKey) {
    return presign(objectKey, 300);
  }

  private String presign(String objectKey, long validSeconds) {
    OSS client = buildClient();
    try {
      return client
          .generatePresignedUrl(
              ossBucket, objectKey, java.util.Date.from(Instant.now().plusSeconds(validSeconds)))
          .toString();
    } catch (Exception exception) {
      LOGGER.warn("OSS presign failed: category={}", exception.getClass().getSimpleName());
      return null;
    } finally {
      client.shutdown();
    }
  }

  public String referenceUrl(String mediaId) {
    return publicBaseUrl.replaceAll("/+$", "") + "/" + mediaId;
  }

  @Transactional
  public String storePublicAudio(
      String purpose,
      String folder,
      byte[] bytes,
      String mimeType,
      String originUrl,
      String licenseNote) {
    if (!"word_audio".equals(purpose) && !"article_audio".equals(purpose))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO_PURPOSE", "音频用途不正确");
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES)
      throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_TTS_AUDIO", "语音服务返回的音频不可用");
    String id = CryptoUtils.randomId();
    String objectKey = folder.replaceAll("[^A-Za-z0-9/_-]", "") + "/" + id + ".mp3";
    putToOss(objectKey, mimeType, bytes);
    Timestamp now = Timestamp.from(Instant.now());
    persistence.insertPublicAsset(
        id,
        purpose,
        objectKey,
        mimeType,
        bytes.length,
        new com.zhixing.mapper.type.BinaryHash(sha256(bytes)),
        originUrl,
        licenseNote,
        now,
        now);
    return id;
  }

  /** 词库批处理使用稳定 object_key；成功重试会复用已有资源，不会重复请求或写入媒体表。 */
  @Transactional
  public String storeVocabularyAudio(
      String purpose, String objectKey, byte[] bytes, String originUrl, String licenseNote) {
    if (!"word_audio".equals(purpose) && !"example_audio".equals(purpose))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO_PURPOSE", "词库音频用途不正确");
    if (objectKey == null || !objectKey.matches("english/audio/[A-Za-z0-9/_-]+\\.mp3"))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AUDIO_OBJECT_KEY", "音频对象路径不正确");
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_AVATAR_BYTES)
      throw new ApiException(HttpStatus.BAD_GATEWAY, "INVALID_TTS_AUDIO", "语音服务返回的音频不可用");
    List<String> existing = persistence.selectReadyAssetIdsByObjectKey(objectKey);
    if (!existing.isEmpty()) return existing.get(0);
    putToOss(objectKey, "audio/mpeg", bytes);
    String id = CryptoUtils.randomId();
    Timestamp now = Timestamp.from(Instant.now());
    persistence.insertPublicAsset(
        id,
        purpose,
        objectKey,
        "audio/mpeg",
        bytes.length,
        new com.zhixing.mapper.type.BinaryHash(sha256(bytes)),
        originUrl,
        licenseNote,
        now,
        now);
    return id;
  }

  /** Book assets never use the public-media route; callers must authorize a book snapshot. */
  @Transactional
  public String storeBookAsset(
      String bookId, String key, byte[] bytes, boolean image, String license) {
    if (!bookId.matches("[a-f0-9]{32}") || !key.matches("[a-f0-9]{64}"))
      throw new ApiException(HttpStatus.BAD_REQUEST, "BOOK_ASSET_KEY", "书籍资源标识错误");
    String mime = image && bytes != null ? detectMime(bytes) : image ? null : "audio/mpeg";
    if (bytes == null
        || bytes.length == 0
        || bytes.length > MAX_AVATAR_BYTES
        || mime == null
        || (!image && !isMp3(bytes)))
      throw new ApiException(HttpStatus.BAD_GATEWAY, "BOOK_ASSET_INVALID", "书籍资源格式错误或超过 5MB");
    String objectKey = "book-assets/" + bookId + "/" + key + (image ? extensionOf(mime) : ".mp3");
    List<String> existing = persistence.selectReadyAssetIdsByObjectKey(objectKey);
    if (!existing.isEmpty()) return existing.get(0);
    putToOss(objectKey, mime, bytes);
    String id = CryptoUtils.randomId();
    Timestamp at = Timestamp.from(Instant.now());
    persistence.insertPublicAsset(
        id,
        image ? "book_image" : "book_audio",
        objectKey,
        mime,
        bytes.length,
        new com.zhixing.mapper.type.BinaryHash(sha256(bytes)),
        null,
        license,
        at,
        at);
    return id;
  }

  public boolean isBookAsset(String bookId, String id) {
    List<Map<String, Object>> rows = persistence.selectAssetAccessDetails(id);
    if (rows.isEmpty()) return false;
    Map<String, Object> row = rows.get(0);
    return "ready".equals(String.valueOf(raw(row, "state")))
        && String.valueOf(raw(row, "object_key")).startsWith("book-assets/" + bookId + "/")
        && java.util.Arrays.asList("book_image", "book_audio")
            .contains(String.valueOf(raw(row, "purpose")));
  }

  public boolean isBookAsset(String bookId, String id, boolean image) {
    if (!isBookAsset(bookId, id)) return false;
    return (image ? "book_image" : "book_audio")
        .equals(String.valueOf(raw(persistence.selectAssetAccessDetails(id).get(0), "purpose")));
  }

  public String bookAssetUrl(String bookId, String id) {
    if (!isBookAsset(bookId, id)) return null;
    return presign(
        String.valueOf(raw(persistence.selectAssetAccessDetails(id).get(0), "object_key")), 300);
  }

  public void requireOwnedAvatar(String ownerId, String url) {
    String base = publicBaseUrl.replaceAll("/+$", "") + "/";
    String id = url.startsWith(base) ? url.substring(base.length()) : "";
    if (!id.matches("[a-f0-9]{32}") || persistence.countOwnedReadyAvatar(id, ownerId) == 0) {
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", "请选择本人成功上传的头像");
    }
  }

  @Transactional
  public void retireOwnedMedia(String ownerId, String mediaId, String purpose) {
    List<Map<String, Object>> rows = persistence.selectOwnedReadyAsset(mediaId, ownerId, purpose);
    if (rows.isEmpty()) return;
    String objectKey = String.valueOf(raw(rows.get(0), "object_key"));
    if (persistence.markOwnedReadyAssetDeleted(Timestamp.from(Instant.now()), mediaId, ownerId)
        == 0) return;
    OSS client = buildClient();
    try {
      client.deleteObject(ossBucket, objectKey);
    } catch (Exception exception) {
      // 数据库状态已阻断访问；OSS 生命周期规则可继续清理极少数删除失败的孤儿对象。
      LOGGER.warn(
          "OSS delete failed after retirement: category={}", exception.getClass().getSimpleName());
    } finally {
      client.shutdown();
    }
  }

  // ---------- OSS ----------

  private void putToOss(String objectKey, String mimeType, byte[] bytes) {
    OSS client = buildClient();
    try {
      client.putObject(
          ossBucket,
          objectKey,
          new ByteArrayInputStream(bytes),
          new com.aliyun.oss.model.ObjectMetadata() {
            {
              setContentType(mimeType);
              setContentLength(bytes.length);
            }
          });
    } catch (Exception exception) {
      LOGGER.error("OSS upload failed: category={}", exception.getClass().getSimpleName());
      throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_STORE_FAILED", "文件上传失败，请重试");
    } finally {
      client.shutdown();
    }
  }

  public boolean bookStorageReady() {
    return ossEndpoint != null
        && !ossEndpoint.isEmpty()
        && ossBucket != null
        && !ossBucket.isEmpty()
        && parameters.configured("oss.access_key_id")
        && parameters.configured("oss.access_key_secret");
  }

  protected OSS buildClient() {
    return new OSSClientBuilder()
        .build(
            ossEndpoint,
            parameters.required("oss.access_key_id"),
            parameters.required("oss.access_key_secret"));
  }

  // ---------- 内部 ----------

  private byte[] readLimited(InputStream stream, long maxBytes, String tooLargeMessage)
      throws IOException {
    java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = stream.read(chunk)) != -1) {
      buffer.write(chunk, 0, read);
      if (buffer.size() > maxBytes) {
        throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", tooLargeMessage);
      }
    }
    return buffer.toByteArray();
  }

  private boolean isMp3(byte[] bytes) {
    if (bytes.length >= 3 && bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') return true;
    for (int i = 0; i + 1 < Math.min(bytes.length, 4096); i++) {
      if ((bytes[i] & 0xFF) == 0xFF && (bytes[i + 1] & 0xE0) == 0xE0) return true;
    }
    return false;
  }

  /** Decode the actual image, including WebP, instead of trusting extensions or MIME headers. */
  private String detectMime(byte[] bytes) {
    try (javax.imageio.stream.ImageInputStream input =
        new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      java.util.Iterator<javax.imageio.ImageReader> readers =
          javax.imageio.ImageIO.getImageReaders(input);
      if (!readers.hasNext()) return null;
      javax.imageio.ImageReader reader = readers.next();
      try {
        String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
        if (!java.util.Arrays.asList("jpeg", "jpg", "png", "webp").contains(format)) return null;
        reader.setInput(input);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels < 1 || pixels > 25000000) return null;
        if (reader.read(0) == null) return null;
        return "image/" + ("jpg".equals(format) ? "jpeg" : format);
      } finally {
        reader.dispose();
      }
    } catch (Exception invalid) {
      return null;
    }
  }

  private String extensionOf(String mime) {
    if ("image/png".equals(mime)) return ".png";
    if ("image/webp".equals(mime)) return ".webp";
    return ".jpg";
  }

  private byte[] sha256(byte[] bytes) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(bytes);
    } catch (Exception exception) {
      throw new IllegalStateException("SHA-256 unavailable", exception);
    }
  }

  private Object raw(Map<String, Object> row, String key) {
    Object value = row.get(key);
    if (value == null) value = row.get(key.toUpperCase());
    return value;
  }

  public static class UploadResult {
    private final String mediaId;
    private final String url;
    private final String previewUrl;

    public String getPreviewUrl() {
      return previewUrl;
    }

    private final String mimeType;
    private final long byteSize;

    public UploadResult(
        String mediaId, String url, String previewUrl, String mimeType, long byteSize) {
      this.mediaId = mediaId;
      this.url = url;
      this.previewUrl = previewUrl;
      this.mimeType = mimeType;
      this.byteSize = byteSize;
    }

    public String getMediaId() {
      return mediaId;
    }

    public String getUrl() {
      return url;
    }

    public String getMimeType() {
      return mimeType;
    }

    public long getByteSize() {
      return byteSize;
    }
  }
}
