package com.zhixing.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * F02 头像上传：仅 JPEG/PNG/WebP，单张 ≤5MB，服务端核验实际魔数而非扩展名。
 * 文件直传阿里云 OSS，本地不存储文件内容；数据库仅保存 media_asset 元数据与 object_key。
 */
@Service
public class MediaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MediaService.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final AppParameterService parameters;

    @Value("${app.media.oss-endpoint}")
    private String ossEndpoint;

    @Value("${app.media.oss-bucket}")
    private String ossBucket;

    @Value("${app.media.public-base-url}")
    private String publicBaseUrl;

    public MediaService(JdbcTemplate jdbcTemplate, AppParameterService parameters) {
        this.jdbcTemplate = jdbcTemplate;
        this.parameters = parameters;
    }

    @Transactional
    public UploadResult uploadAvatar(String ownerId, String filename, String declaredMime, InputStream stream) {
        byte[] bytes;
        try {
            bytes = readLimited(stream);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_READ_FAILED", "文件读取失败，请重试");
        }
        if (bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDIA_EMPTY", "文件不能为空");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", "图片不能超过 5MB");
        }

        String actualMime = detectMime(bytes);
        if (actualMime == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MEDIA_FORMAT_REJECTED",
                    "仅支持 JPEG/PNG/WebP 格式图片");
        }

        byte[] sha256 = sha256(bytes);
        String id = CryptoUtils.randomId();
        String objectKey = "avatar/" + id + extensionOf(actualMime);

        putToOss(objectKey, actualMime, bytes);

        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(
                "INSERT INTO media_asset (id, owner_id, purpose, object_key, mime_type, byte_size, sha256, state, created_at, updated_at) " +
                        "VALUES (?, ?, 'avatar', ?, ?, ?, ?, 'ready', ?, ?)",
                id, ownerId, objectKey, actualMime, bytes.length, sha256, now, now);
        LOGGER.info("Avatar uploaded to OSS: ownerId={}, mediaId={}, mime={}, bytes={}",
                ownerId, id, actualMime, bytes.length);

        String url = referenceUrl(id);
        return new UploadResult(id, url, signedUrl(id, ownerId), actualMime, bytes.length);
    }

    /** 供图片代理访问：返回 OSS object 的签名 URL（短时有效）。 */
    public String signedUrl(String mediaId, String viewerId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT object_key, state, owner_id FROM media_asset WHERE id = ? AND purpose = 'avatar'", mediaId);
        if (rows.isEmpty()) return null;
        Map<String, Object> row = rows.get(0);
        if (!"ready".equals(String.valueOf(raw(row, "state")))) return null;
        boolean owner = viewerId != null && viewerId.equals(String.valueOf(raw(row, "owner_id")));
        // Only avatars currently selected by an active user are public. Pending uploads remain private.
        if (!owner && jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM app_user WHERE avatar_url = ? AND status = 'active'",
                Integer.class, referenceUrl(mediaId)) == 0) return null;
        String objectKey = String.valueOf(raw(row, "object_key"));
        OSS client = buildClient();
        try {
            return client.generatePresignedUrl(ossBucket, objectKey,
                    java.util.Date.from(Instant.now().plusSeconds(300))).toString();
        } catch (Exception exception) {
            LOGGER.warn("OSS presign failed: mediaId={}, {}", mediaId, exception.getMessage());
            return null;
        } finally {
            client.shutdown();
        }
    }

    public String referenceUrl(String mediaId) {
        return publicBaseUrl.replaceAll("/+$", "") + "/" + mediaId;
    }

    public void requireOwnedAvatar(String ownerId, String url) {
        String base = publicBaseUrl.replaceAll("/+$", "") + "/";
        String id = url.startsWith(base) ? url.substring(base.length()) : "";
        if (!id.matches("[a-f0-9]{32}") || jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_asset WHERE id = ? AND owner_id = ? AND purpose = 'avatar' AND state = 'ready'",
                Integer.class, id, ownerId) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", "请选择本人成功上传的头像");
        }
    }

    // ---------- OSS ----------

    private void putToOss(String objectKey, String mimeType, byte[] bytes) {
        OSS client = buildClient();
        try {
            client.putObject(ossBucket, objectKey, new ByteArrayInputStream(bytes),
                    new com.aliyun.oss.model.ObjectMetadata() {{
                        setContentType(mimeType);
                        setContentLength(bytes.length);
                    }});
        } catch (Exception exception) {
            LOGGER.error("OSS upload failed: objectKey={}", objectKey, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MEDIA_STORE_FAILED", "图片上传失败，请重试");
        } finally {
            client.shutdown();
        }
    }

    private OSS buildClient() {
        return new OSSClientBuilder().build(ossEndpoint, parameters.required("oss.access_key_id"), parameters.required("oss.access_key_secret"));
    }

    // ---------- 内部 ----------

    private byte[] readLimited(InputStream stream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > MAX_BYTES + 1) {
                throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "MEDIA_TOO_LARGE", "图片不能超过 5MB");
            }
        }
        return buffer.toByteArray();
    }

    /** Decode the actual image, including WebP, instead of trusting extensions or MIME headers. */
    private String detectMime(byte[] bytes) {
        try (javax.imageio.stream.ImageInputStream input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            java.util.Iterator<javax.imageio.ImageReader> readers = javax.imageio.ImageIO.getImageReaders(input);
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
            } finally { reader.dispose(); }
        } catch (Exception invalid) { return null; }
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
        public String getPreviewUrl() { return previewUrl; }
        private final String mimeType;
        private final long byteSize;

        public UploadResult(String mediaId, String url, String previewUrl, String mimeType, long byteSize) {
            this.mediaId = mediaId;
            this.url = url;
            this.previewUrl = previewUrl;
            this.mimeType = mimeType;
            this.byteSize = byteSize;
        }

        public String getMediaId() { return mediaId; }
        public String getUrl() { return url; }
        public String getMimeType() { return mimeType; }
        public long getByteSize() { return byteSize; }
    }
}
