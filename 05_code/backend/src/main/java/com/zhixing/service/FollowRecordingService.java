package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.ContentMapper;
import com.zhixing.model.ContentDetailView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FollowRecordingService {
    public static final String CONSENT_PURPOSE = "follow_recording_upload";
    private final ContentMapper contents;
    private final ConsentService consents;
    private final MediaService media;
    private final JdbcTemplate jdbc;

    public FollowRecordingService(ContentMapper contents, ConsentService consents, MediaService media, JdbcTemplate jdbc) {
        this.contents = contents;
        this.consents = consents;
        this.media = media;
        this.jdbc = jdbc;
    }

    public Map<String, Object> latest(String ownerId, String contentId) {
        requireArticle(ownerId, contentId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,asset_id,duration_ms,updated_at FROM follow_recording WHERE owner_id=? AND content_id=? AND state='active' LIMIT 1",
                ownerId, contentId);
        if (rows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<String, Object>();
            empty.put("exists", false);
            return empty;
        }
        return view(ownerId, contentId, rows.get(0));
    }

    @Transactional
    public Map<String, Object> upload(String ownerId, String contentId, int durationMs, java.io.InputStream stream) {
        ContentDetailView article = requireArticle(ownerId, contentId);
        if (durationMs < 1 || durationMs > 60000) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RECORDING_DURATION", "跟读录音时长须大于 0 且不超过 60 秒");
        }
        consents.requireGranted(ownerId, CONSENT_PURPOSE);

        List<String> oldAssets = jdbc.query(
                "SELECT asset_id FROM follow_recording WHERE owner_id=? AND content_id=? AND state='active'",
                new Object[]{ownerId, contentId}, (rs, rowNum) -> rs.getString(1));
        MediaService.UploadResult uploaded = media.uploadFollowRecording(ownerId, stream);
        Timestamp now = Timestamp.from(Instant.now());
        String id = CryptoUtils.randomId();
        jdbc.update("INSERT INTO follow_recording(id,owner_id,content_id,content_version_id,asset_id,duration_ms,state,created_at,updated_at) " +
                        "VALUES(?,?,?,?,?,?,'active',?,?) ON DUPLICATE KEY UPDATE id=VALUES(id),content_version_id=VALUES(content_version_id),asset_id=VALUES(asset_id),duration_ms=VALUES(duration_ms),state='active',deleted_at=NULL,updated_at=VALUES(updated_at)",
                id, ownerId, contentId, article.getVersionId(), uploaded.getMediaId(), durationMs, now, now);
        for (String oldAsset : oldAssets) {
            if (!uploaded.getMediaId().equals(oldAsset)) media.retireOwnedMedia(ownerId, oldAsset, "follow_recording");
        }
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id); row.put("asset_id", uploaded.getMediaId()); row.put("duration_ms", durationMs); row.put("updated_at", now);
        return view(ownerId, contentId, row);
    }

    @Transactional
    public Map<String, Object> delete(String ownerId, String contentId) {
        requireArticle(ownerId, contentId);
        List<String> assets = jdbc.query(
                "SELECT asset_id FROM follow_recording WHERE owner_id=? AND content_id=? AND state='active'",
                new Object[]{ownerId, contentId}, (rs, rowNum) -> rs.getString(1));
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("UPDATE follow_recording SET state='deleted',deleted_at=?,updated_at=? WHERE owner_id=? AND content_id=? AND state='active'",
                now, now, ownerId, contentId);
        for (String assetId : assets) media.retireOwnedMedia(ownerId, assetId, "follow_recording");
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("deleted", true); return result;
    }

    private ContentDetailView requireArticle(String ownerId, String contentId) {
        ContentDetailView detail = contents.selectDetail(ownerId, contentId);
        if (detail == null) throw new ApiException(HttpStatus.NOT_FOUND, "CONTENT_NOT_FOUND", "短文不存在或未发布");
        if (!"english_article".equals(detail.getContentType())) throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENGLISH_ARTICLE", "该内容不是英语短文");
        return detail;
    }

    private Map<String, Object> view(String ownerId, String contentId, Map<String, Object> row) {
        String assetId = String.valueOf(raw(row, "asset_id"));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("exists", true); result.put("id", raw(row, "id")); result.put("contentId", contentId);
        result.put("durationMs", raw(row, "duration_ms")); result.put("url", media.referenceUrl(assetId));
        result.put("previewUrl", media.signedUrl(assetId, ownerId)); result.put("uploadedAt", raw(row, "updated_at"));
        return result;
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? row.get(key.toUpperCase()) : value;
    }
}
