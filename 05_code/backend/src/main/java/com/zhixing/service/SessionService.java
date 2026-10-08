package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.mapper.UserSessionMapper;
import com.zhixing.mapper.type.BinaryHash;
import com.zhixing.model.AuthResult;
import com.zhixing.model.AuthenticatedSession;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {
  private static final Logger LOGGER = LoggerFactory.getLogger(SessionService.class);
  private final UserSessionMapper persistence;
  private final AppProperties properties;
  private final UserQueryService users;

  public SessionService(
      UserSessionMapper persistence, AppProperties properties, UserQueryService users) {
    this.persistence = persistence;
    this.properties = properties;
    this.users = users;
  }

  @Transactional
  public AuthResult createForUser(String userId) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.getSessionTtl());
    String token = CryptoUtils.randomToken(32);
    persistence.insertUserSession(
        CryptoUtils.randomId(),
        userId,
        new BinaryHash(CryptoUtils.sha256(token)),
        Timestamp.from(expiresAt),
        Timestamp.from(now));
    persistence.updateUserLogin(Timestamp.from(now), Timestamp.from(now), userId);
    LOGGER.info("User session created");

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
    List<Map<String, Object>> rows =
        persistence.selectUserSessionByTokenHash(new BinaryHash(CryptoUtils.sha256(token)));
    if (rows.isEmpty()) throw unauthorized();

    Map<String, Object> row = rows.get(0);
    Timestamp revokedAt = timestamp(row, "revoked_at");
    Timestamp expiresAt = timestamp(row, "expires_at");
    String status = string(row, "status");
    String userId = string(row, "user_id");
    String sessionId = string(row, "session_id");
    // 同时验证撤销、过期及账户状态，不能仅凭摘要匹配接受会话。
    if (revokedAt != null || expiresAt == null || !expiresAt.toInstant().isAfter(Instant.now())) {
      throw unauthorized();
    }
    if (!"active".equals(status)) {
      persistence.revokeUserSessions(
          Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), userId);
      throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号已停用或正在注销");
    }
    persistence.touchSession(
        Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), sessionId);
    return new AuthenticatedSession(sessionId, userId);
  }

  @Transactional
  public void revoke(String sessionId) {
    persistence.revokeSession(
        Timestamp.from(Instant.now()), Timestamp.from(Instant.now()), sessionId);
    LOGGER.info("User session revoked");
  }

  private String bearer(String authorization) {
    if (authorization == null
        || !authorization.startsWith("Bearer ")
        || authorization.length() <= 7) {
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
