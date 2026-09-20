package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.ConsentRequest;
import com.zhixing.entity.UserConsentEntity;
import com.zhixing.mapper.AppUserMapper;
import com.zhixing.mapper.UserConsentMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class ConsentService {
    private static final Set<String> ALLOWED_PURPOSES = new HashSet<String>(Arrays.asList(
            "privacy", "follow_recording_upload"));
    private final UserConsentMapper consents;
    private final AppUserMapper users;
    public ConsentService(UserConsentMapper consents, AppUserMapper users) { this.consents = consents; this.users = users; }

    @Transactional
    public Map<String, Object> record(String userId, ConsentRequest request) {
        if ("ai_send".equals(request.getPurpose())) throw new ApiException(HttpStatus.FORBIDDEN, "AI_AUTHORIZATION_ADMIN_REQUIRED", "AI 使用权限仅可由管理员在管理端授予");
        if (!ALLOWED_PURPOSES.contains(request.getPurpose())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONSENT_PURPOSE", "不支持的同意用途");
        if (!"grant".equals(request.getDecision()) && !"revoke".equals(request.getDecision())) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CONSENT_DECISION", "不支持的同意决定");
        Instant now = Instant.now(); UserConsentEntity entity = new UserConsentEntity(); entity.setId(CryptoUtils.randomId()); entity.setOwnerId(userId); entity.setPurpose(request.getPurpose()); entity.setDocumentVersion(request.getDocumentVersion()); entity.setDecision(request.getDecision()); entity.setOccurredAt(now); entity.setCreatedAt(now); consents.insert(entity);
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("purpose", request.getPurpose()); result.put("documentVersion", request.getDocumentVersion()); result.put("decision", request.getDecision()); result.put("occurredAt", now); return result;
    }

    public boolean hasGranted(String userId, String purpose) {
        return "grant".equals(consents.latestDecision(userId, purpose));
    }

    public void requireGranted(String userId, String purpose) {
        if (!hasGranted(userId, purpose)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CONSENT_REQUIRED", "请先同意跟读录音云端保存");
        }
    }
}
