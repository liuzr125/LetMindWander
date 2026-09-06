package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.entity.AppUserEntity;
import com.zhixing.mapper.AppUserMapper;
import com.zhixing.model.AuthResult;
import com.zhixing.model.RegistrationContext;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class RegistrationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationService.class);
    private final JdbcTemplate jdbcTemplate;
    private final SessionService sessions;
    private final UserQueryService users;
    private final AppUserMapper appUserMapper;

    public RegistrationService(JdbcTemplate jdbcTemplate, SessionService sessions, UserQueryService users, AppUserMapper appUserMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessions = sessions;
        this.users = users;
        this.appUserMapper = appUserMapper;
    }

    @Transactional
    public AuthResult complete(RegistrationContext context, String mobile, String nickname, String avatarUrl, boolean aiConsent) {
        validateNickname(nickname);
        String normalizedAvatar = validateAvatar(avatarUrl);
        Instant now = Instant.now();

        AppUserEntity existing = users.findByWechat(context.getWxAppId(), context.getWxOpenId());
        if (existing != null) {
            LOGGER.info("Registration request is idempotent: existing userId={}", existing.getId());
            return activeExisting(existing);
        }

        Map<String, Object> counter = one(jdbcTemplate.queryForList(
                "SELECT id, invited_limit, invited_used FROM admission_counter WHERE scope_code = 'trial' FOR UPDATE"));
        if (counter == null) throw new ApiException(HttpStatus.CONFLICT, "ADMISSION_FULL", "试用名额尚未初始化");

        Map<String, Object> invite = one(jdbcTemplate.queryForList(
                "SELECT id, status, expires_at FROM invite_code WHERE code_hash = ? FOR UPDATE",
                CryptoUtils.sha256(normalizeInvite(context.getInviteCode()))));

        existing = users.findByWechat(context.getWxAppId(), context.getWxOpenId());
        if (existing != null) return activeExisting(existing);
        validateInvite(invite, now);

        int limit = number(counter, "invited_limit");
        int used = number(counter, "invited_used");
        if (used >= limit) throw new ApiException(HttpStatus.CONFLICT, "ADMISSION_FULL", "受邀试用名额已满，暂未开放注册");

        String userId = CryptoUtils.randomId();
        String planId = CryptoUtils.randomId();
        // 短ID：R_ + 4 位注册顺序号。注册事务已通过 admission_counter 行锁串行化，MAX(seq_no)+1 无并发冲突。
        Long maxSeqNo = appUserMapper.selectMaxSeqNo();
        long seqNo = (maxSeqNo == null ? 0L : maxSeqNo.longValue()) + 1L;
        String shortId = "R_" + String.format("%04d", seqNo);
        try {
            jdbcTemplate.update("INSERT INTO app_user " +
                            "(id, wx_app_id, wx_open_id, nickname, mobile, avatar_url, status, current_plan_id, seq_no, short_id, last_login_at, mobile_bound_at, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 'active', ?, ?, ?, ?, ?, ?, ?)",
                    userId, context.getWxAppId(), context.getWxOpenId(), nickname.trim(), mobile, normalizedAvatar,
                    planId, seqNo, shortId, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
        } catch (DuplicateKeyException duplicate) {
            AppUserEntity duplicateUser = users.findByWechat(context.getWxAppId(), context.getWxOpenId());
            if (duplicateUser != null) return activeExisting(duplicateUser);
            throw duplicate;
        }

        LocalDate effectiveDate = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        jdbcTemplate.update("INSERT INTO learning_plan (id, owner_id, version_no, effective_date, change_reason, created_at) VALUES (?, ?, 1, ?, 'registration_default', ?)",
                planId, userId, java.sql.Date.valueOf(effectiveDate), Timestamp.from(now));
        int redeemed = jdbcTemplate.update("UPDATE invite_code SET status = 'redeemed', redeemed_by = ?, redeemed_at = ?, updated_at = ? " +
                        "WHERE id = ? AND status = 'available'",
                userId, Timestamp.from(now), Timestamp.from(now), string(invite, "id"));
        int occupied = jdbcTemplate.update("UPDATE admission_counter SET invited_used = invited_used + 1, updated_at = ? " +
                        "WHERE id = ? AND invited_used < invited_limit",
                Timestamp.from(now), string(counter, "id"));
        if (redeemed != 1 || occupied != 1) {
            LOGGER.warn("Registration transaction conflict: userId={}, redeemed={}, occupied={}", userId, redeemed, occupied);
            throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", "注册请求已被处理，请重新登录");
        }

        consent(userId, "privacy", context.getPrivacyVersion(), "grant", now);
        if (aiConsent) {
            consent(userId, "ai_send", context.getPrivacyVersion(), "grant", now);
            jdbcTemplate.update("UPDATE app_user SET ai_consent_version = ?, ai_consented_at = ? WHERE id = ?",
                    context.getPrivacyVersion(), Timestamp.from(now), userId);
        }
        LOGGER.info("Invite redeemed and user created: userId={}", userId);
        return sessions.createForUser(userId);
    }

    private AuthResult activeExisting(AppUserEntity existing) {
        if (!"active".equals(existing.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号已停用或正在注销");
        }
        return sessions.createForUser(existing.getId());
    }

    private void validateInvite(Map<String, Object> invite, Instant now) {
        if (invite == null || !"available".equals(string(invite, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_UNAVAILABLE", "邀请码无效、已使用或已撤销");
        }
        Instant expiresAt = instant(invite, "expires_at");
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_UNAVAILABLE", "邀请码已过期");
        }
    }

    private void validateNickname(String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 30 || value.matches(".*[\\p{Cntrl}].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "昵称需为 1 至 30 个字符");
        }
    }

    private String validateAvatar(String avatarUrl) {
        if (!StringUtils.hasText(avatarUrl)) return null;
        String value = avatarUrl.trim();
        if (value.length() > 2048 || !value.startsWith("https://")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", "头像地址必须为 HTTPS 地址");
        }
        return value;
    }

    private void consent(String userId, String purpose, String version, String decision, Instant now) {
        jdbcTemplate.update("INSERT INTO user_consent (id, owner_id, purpose, document_version, decision, occurred_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                CryptoUtils.randomId(), userId, purpose, version, decision, Timestamp.from(now), Timestamp.from(now));
    }

    private String normalizeInvite(String invite) {
        return invite == null ? "" : invite.trim().toUpperCase();
    }

    private Map<String, Object> one(List<Map<String, Object>> rows) { return rows.isEmpty() ? null : rows.get(0); }
    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key); if (value == null) value = row.get(key.toUpperCase());
        return value == null ? null : String.valueOf(value);
    }
    private int number(Map<String, Object> row, String key) {
        Object value = row.get(key); if (value == null) value = row.get(key.toUpperCase());
        return value == null ? 0 : ((Number) value).intValue();
    }
    /**
     * MySQL Connector/J may expose TIMESTAMP values as Timestamp, LocalDateTime
     * or String depending on the server and connection properties.  Normalize
     * all supported representations before applying the expiry comparison.
     */
    private Instant instant(Map<String, Object> row, String key) {
        Object value = row.get(key); if (value == null) value = row.get(key.toUpperCase());
        if (value instanceof Timestamp) return ((Timestamp) value).toInstant();
        if (value instanceof java.util.Date) return ((java.util.Date) value).toInstant();
        if (value instanceof LocalDateTime) return ((LocalDateTime) value).atZone(ZoneId.of("Asia/Shanghai")).toInstant();
        if (value instanceof String) {
            try {
                return Timestamp.valueOf((String) value).toInstant();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }
}
