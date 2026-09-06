package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.ConsentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ConsentService {
    private final JdbcTemplate jdbcTemplate;

    public ConsentService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    @Transactional
    public Map<String, Object> record(String userId, ConsentRequest request) {
        if (!"privacy".equals(request.getPurpose()) && !"ai_send".equals(request.getPurpose())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONSENT_PURPOSE", "不支持的同意用途");
        }
        if (!"grant".equals(request.getDecision()) && !"revoke".equals(request.getDecision())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONSENT_DECISION", "不支持的同意决定");
        }
        Instant now = Instant.now();
        jdbcTemplate.update("INSERT INTO user_consent (id, owner_id, purpose, document_version, decision, occurred_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                CryptoUtils.randomId(), userId, request.getPurpose(), request.getDocumentVersion(), request.getDecision(), Timestamp.from(now), Timestamp.from(now));
        if ("ai_send".equals(request.getPurpose())) {
            jdbcTemplate.update("UPDATE app_user SET ai_consent_version = ?, ai_consented_at = ?, updated_at = ? WHERE id = ?",
                    "grant".equals(request.getDecision()) ? request.getDocumentVersion() : null,
                    "grant".equals(request.getDecision()) ? Timestamp.from(now) : null,
                    Timestamp.from(now), userId);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("purpose", request.getPurpose());
        result.put("documentVersion", request.getDocumentVersion());
        result.put("decision", request.getDecision());
        result.put("occurredAt", now);
        return result;
    }
}
