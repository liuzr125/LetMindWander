package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.model.AuthResult;
import com.zhixing.model.AuthenticatedSession;
import com.zhixing.model.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SessionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;
    private final UserQueryService users;

    public SessionService(JdbcTemplate jdbcTemplate, AppProperties properties, UserQueryService users) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.users = users;
    }

    @Transactional
    public AuthResult createForUser(String userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.getSessionTtl());
        String token = CryptoUtils.randomToken(32);
        jdbcTemplate.update(
                "INSERT INTO auth_session (id, principal_type, principal_id, token_hash, expires_at, last_seen_at) VALUES (?, 'user', ?, ?, ?, ?)",
                CryptoUtils.randomId(), userId, CryptoUtils.sha256(token), Timestamp.from(expiresAt), Timestamp.from(now));
        jdbcTemplate.update("UPDATE app_user SET last_login_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(now), Timestamp.from(now), userId);
        LOGGER.info("Session created: userId={}, expiresAt={}", userId, expiresAt);

        AuthResult result = new AuthResult();
        result.setStage("authenticated");
        result.setAccessToken(token);
        result.setExpiresAt(expiresAt);
        result.setNextPage("/pages/today/index");
        result.setUser(users.findView(userId));
        return result;
    }

    @Transactional
    public AuthenticatedSession requireUser(String authorization) {
        String token = bearer(authorization);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT s.id AS session_id, s.principal_id AS user_id, s.expires_at, s.revoked_at, u.status " +
                        "FROM auth_session s JOIN app_user u ON u.id = s.principal_id " +
                        "WHERE s.principal_type = 'user' AND s.token_hash = ?",
                CryptoUtils.sha256(token));
        if (rows.isEmpty()) throw unauthorized();

        Map<String, Object> row = rows.get(0);
        Timestamp revokedAt = timestamp(row, "revoked_at");
        Timestamp expiresAt = timestamp(row, "expires_at");
        String status = string(row, "status");
        String userId = string(row, "user_id");
        String sessionId = string(row, "session_id");
        if (revokedAt != null || expiresAt == null || !expiresAt.toInstant().isAfter(Instant.now())) {
            throw unauthorized();
        }
        if (!"active".equals(status)) {
            jdbcTemplate.update("UPDATE auth_session SET revoked_at = ?, updated_at = ? WHERE principal_type = 'user' AND principal_id = ? AND revoked_at IS NULL",
                    Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), userId);
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号已停用或正在注销");
        }
        jdbcTemplate.update("UPDATE auth_session SET last_seen_at = ?, updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), sessionId);
        return new AuthenticatedSession(sessionId, userId);
    }

    @Transactional
    public void revoke(String sessionId) {
        jdbcTemplate.update("UPDATE auth_session SET revoked_at = ?, updated_at = ? WHERE id = ? AND revoked_at IS NULL",
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), sessionId);
        LOGGER.info("Session revoked: sessionId={}", sessionId);
    }

    private String bearer(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7) {
            throw unauthorized();
        }
        return authorization.substring(7).trim();
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态已失效，请重新登录");
    }

    private String string(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        return value == null ? null : String.valueOf(value);
    }

    private Timestamp timestamp(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        if (value instanceof Timestamp) return (Timestamp) value;
        if (value instanceof java.time.LocalDateTime) {
            return Timestamp.valueOf((java.time.LocalDateTime) value);
        }
        if (value instanceof java.time.OffsetDateTime) {
            return Timestamp.from(((java.time.OffsetDateTime) value).toInstant());
        }
        if (value instanceof Instant) return Timestamp.from((Instant) value);
        if (value instanceof java.util.Date) return new Timestamp(((java.util.Date) value).getTime());
        return null;
    }
}
