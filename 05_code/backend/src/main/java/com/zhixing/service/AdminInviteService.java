package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.CreateInviteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminInviteService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdminInviteService.class);
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public AdminInviteService(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Object> create(CreateInviteRequest request) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(request.getExpiresInDays(), ChronoUnit.DAYS);
        List<Map<String, Object>> created = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < request.getCount(); i++) {
            String code = uniqueCode();
            String id = CryptoUtils.randomId();
            jdbcTemplate.update("INSERT INTO invite_code (id, code_hash, status, expires_at, created_by, created_at, updated_at) " +
                            "VALUES (?, ?, 'available', ?, ?, ?, ?)",
                    id, CryptoUtils.sha256(code), Timestamp.from(expiresAt), properties.getAdminPrincipalId(), Timestamp.from(now), Timestamp.from(now));
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("id", id);
            item.put("code", code);
            item.put("status", "available");
            item.put("expiresAt", expiresAt);
            created.add(item);
        }
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("codes", created);
        response.put("notice", "邀请码明文仅在本次响应中展示，请立即安全保存");
        LOGGER.info("Admin created invite batch: count={}, expiresAt={}", created.size(), expiresAt);
        return response;
    }

    public Map<String, Object> list(String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Object> args = new ArrayList<Object>();
        String where = "";
        if (status != null && !status.trim().isEmpty() && !"all".equals(status)) {
            if ("expired".equals(status)) {
                where = " WHERE status = 'available' AND expires_at <= CURRENT_TIMESTAMP ";
            } else {
                where = " WHERE status = ? ";
                args.add(status);
            }
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invite_code" + where, args.toArray(), Long.class);
        args.add(safeSize);
        args.add((safePage - 1) * safeSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, CASE WHEN status = 'available' AND expires_at <= CURRENT_TIMESTAMP THEN 'expired' ELSE status END AS status, " +
                        "expires_at, redeemed_by, redeemed_at, created_at FROM invite_code" + where +
                        " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?", args.toArray());
        List<Map<String, Object>> content = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : rows) content.add(inviteView(row));

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("content", content);
        response.put("page", safePage);
        response.put("size", safeSize);
        response.put("total", total == null ? 0 : total);
        return response;
    }

    @Transactional
    public Map<String, Object> revoke(String id) {
        int changed = jdbcTemplate.update("UPDATE invite_code SET status = 'revoked', updated_at = ? " +
                        "WHERE id = ? AND status = 'available' AND expires_at > ?",
                Timestamp.from(Instant.now()), id, Timestamp.from(Instant.now()));
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "INVITE_UNAVAILABLE", "该邀请码已使用、撤销或过期");
        LOGGER.info("Admin revoked invite record: inviteId={}", id);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("id", id);
        response.put("status", "revoked");
        return response;
    }

    @Transactional
    public Map<String, Object> delete(String id) {
        int changed = jdbcTemplate.update("DELETE FROM invite_code WHERE id = ? AND status <> 'redeemed'", id);
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "INVITE_UNDELETABLE", "已兑换的邀请码不可删除");
        LOGGER.info("Admin deleted invite record: inviteId={}", id);
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("id", id);
        response.put("deleted", true);
        return response;
    }

    public Map<String, Object> admission() {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT invited_limit, invited_used FROM admission_counter WHERE scope_code = 'trial'");
        int limit = number(row, "invited_limit");
        int used = number(row, "invited_used");
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("scopeCode", "trial");
        response.put("invitedLimit", limit);
        response.put("invitedUsed", used);
        response.put("remaining", Math.max(limit - used, 0));
        return response;
    }

    @Transactional
    public Map<String, Object> updateAdmission(int invitedLimit) {
        Map<String, Object> current = jdbcTemplate.queryForMap(
                "SELECT invited_used FROM admission_counter WHERE scope_code = 'trial' FOR UPDATE");
        int used = number(current, "invited_used");
        if (invitedLimit < used) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LIMIT_BELOW_USED", "名额上限不能小于已使用名额 " + used);
        }
        jdbcTemplate.update("UPDATE admission_counter SET invited_limit = ?, updated_at = ? WHERE scope_code = 'trial'",
                invitedLimit, Timestamp.from(Instant.now()));
        LOGGER.info("Admin updated admission limit: limit={}", invitedLimit);
        return admission();
    }

    public Map<String, Object> stats() {
        Map<String, Object> response = new LinkedHashMap<String, Object>(admission());
        response.put("availableCodes", count("status = 'available' AND expires_at > CURRENT_TIMESTAMP"));
        response.put("redeemedCodes", count("status = 'redeemed'"));
        response.put("revokedCodes", count("status = 'revoked'"));
        response.put("expiredCodes", count("status = 'available' AND expires_at <= CURRENT_TIMESTAMP"));
        return response;
    }

    private long count(String condition) {
        Long value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invite_code WHERE " + condition, Long.class);
        return value == null ? 0L : value;
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder code = new StringBuilder("INV-");
            for (int i = 0; i < 8; i++) {
                if (i == 4) code.append('-');
                code.append(ALPHABET[random.nextInt(ALPHABET.length)]);
            }
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM invite_code WHERE code_hash = ?", Integer.class,
                    CryptoUtils.sha256(code.toString()));
            if (count != null && count == 0) return code.toString();
        }
        throw new ApiException(HttpStatus.CONFLICT, "INVITE_GENERATION_FAILED", "邀请码生成冲突，请重试");
    }

    private Map<String, Object> inviteView(Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("id", value(row, "id"));
        item.put("status", value(row, "status"));
        item.put("createdAt", value(row, "created_at"));
        item.put("expiresAt", value(row, "expires_at"));
        item.put("redeemedAt", value(row, "redeemed_at"));
        item.put("redeemedBy", value(row, "redeemed_by"));
        return item;
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key); return value == null ? row.get(key.toUpperCase()) : value;
    }
    private int number(Map<String, Object> row, String key) {
        Object value = value(row, key); return value == null ? 0 : ((Number) value).intValue();
    }
}
