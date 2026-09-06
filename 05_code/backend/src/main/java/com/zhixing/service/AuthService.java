package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import com.zhixing.dto.RegisterRequest;
import com.zhixing.dto.PhoneAuthorizationRequest;
import com.zhixing.dto.SendSmsCodeRequest;
import com.zhixing.dto.WechatLoginRequest;
import com.zhixing.entity.AppUserEntity;
import com.zhixing.mapper.AuthMapper;
import com.zhixing.model.AuthResult;
import com.zhixing.model.RegistrationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private final AuthMapper authMapper;
    private final AppProperties properties;
    private final WechatGateway wechat;
    private final SessionService sessions;
    private final RegistrationTicketStore tickets;
    private final RegistrationService registration;
    private final UserQueryService users;
    private final SmsVerificationService smsVerification;

    public AuthService(AuthMapper authMapper, AppProperties properties, WechatGateway wechat,
                       SessionService sessions, RegistrationTicketStore tickets,
                       RegistrationService registration, UserQueryService users,
                       SmsVerificationService smsVerification) {
        this.authMapper = authMapper;
        this.properties = properties;
        this.wechat = wechat;
        this.sessions = sessions;
        this.tickets = tickets;
        this.registration = registration;
        this.users = users;
        this.smsVerification = smsVerification;
    }

    public Object login(WechatLoginRequest request) {
        requirePrivacy(request.getPrivacyVersion());
        WechatGateway.Identity identity = wechat.exchangeCode(request.getCode());
        AppUserEntity existing = users.findByWechat(identity.getAppId(), identity.getOpenId());
        if (existing != null) {
            if (!"active".equals(existing.getStatus())) {
                authMapper.revokeActiveUserSessions(existing.getId(), Timestamp.from(Instant.now()));
                LOGGER.warn("Login denied: disabled userId={}", existing.getId());
                throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_UNAVAILABLE", "账号已停用或正在注销");
            }
            LOGGER.info("Existing user login accepted: userId={}", existing.getId());
            return sessions.createForUser(existing.getId());
        }

        if (!StringUtils.hasText(request.getInviteCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVITE_UNAVAILABLE", "请输入邀请码");
        }
        String inviteCode = request.getInviteCode().trim().toUpperCase();
        validateInviteAvailable(inviteCode);

        RegistrationContext context = new RegistrationContext();
        context.setWxAppId(identity.getAppId());
        context.setWxOpenId(identity.getOpenId());
        context.setInviteCode(inviteCode);
        context.setPrivacyVersion(request.getPrivacyVersion());
        context.setExpiresAt(Instant.now().plus(properties.getRegistrationTtl()));
        String ticket = tickets.create(context);
        LOGGER.info("New user admission accepted; registration ticket created");

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("stage", "registration_required");
        response.put("registrationTicket", ticket);
        response.put("expiresAt", context.getExpiresAt());
        response.put("nextPage", "/pages/auth/register/index");
        Map<String, Object> profile = new LinkedHashMap<String, Object>();
        profile.put("nickname", "学习者");
        profile.put("avatarUrl", null);
        response.put("profile", profile);
        return response;
    }

    public AuthResult register(RegisterRequest request) {
        requirePrivacy(request.getPrivacyVersion());
        RegistrationContext context = tickets.get(request.getRegistrationTicket());
        if (context == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REGISTRATION_TICKET_EXPIRED", "注册凭证已过期，请返回重新登录");
        }
        synchronized (context) {
            if (context.getCompletedResult() != null) return context.getCompletedResult();
            if (!context.getPrivacyVersion().equals(request.getPrivacyVersion())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PRIVACY_CONSENT_REQUIRED", "隐私协议版本已更新，请重新确认");
            }
            String mobile = request.getMobile().trim();
            smsVerification.verifyAndConsume(request.getRegistrationTicket(), mobile, request.getSmsCode());
            context.setMobile(mobile);
            AuthResult result = registration.complete(context, mobile, request.getNickname(), request.getAvatarUrl(), request.isAiConsent());
            context.setCompletedResult(result);
            tickets.save(request.getRegistrationTicket(), context);
            LOGGER.info("Registration completed: userId={}", result.getUser() == null ? "unknown" : result.getUser().getId());
            return result;
        }
    }

    public SmsVerificationService.SendResult sendSmsCode(SendSmsCodeRequest request) {
        RegistrationContext context = tickets.get(request.getRegistrationTicket());
        if (context == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REGISTRATION_TICKET_EXPIRED", "注册凭证已过期，请返回重新登录");
        }
        synchronized (context) {
            if (context.getCompletedResult() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "REGISTRATION_COMPLETED", "注册已完成，请直接登录");
            }
            return smsVerification.send(request.getRegistrationTicket(), request.getMobile().trim());
        }
    }

    public Map<String, Object> authorizePhone(PhoneAuthorizationRequest request) {
        RegistrationContext context = tickets.get(request.getRegistrationTicket());
        if (context == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REGISTRATION_TICKET_EXPIRED", "注册凭证已过期，请返回重新登录");
        }
        synchronized (context) {
            String mobile = wechat.resolveMobile(request.getCode());
            context.setMobile(mobile);
            tickets.save(request.getRegistrationTicket(), context);
            Map<String, Object> response = new LinkedHashMap<String, Object>();
            response.put("mobileMasked", maskMobile(mobile));
            response.put("authorized", true);
            LOGGER.info("Phone authorization completed for pending registration");
            return response;
        }
    }

    private void validateInviteAvailable(String inviteCode) {
        List<Map<String, Object>> invites = authMapper.selectInviteByHash(CryptoUtils.sha256(inviteCode));
        if (invites.isEmpty() || !"available".equals(value(invites.get(0), "status"))) {
            LOGGER.warn("Invite admission rejected: unavailable invite");
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_UNAVAILABLE", "邀请码无效、已使用或已撤销");
        }
        Instant expiresAt = instant(invites.get(0), "expires_at");
        if (expiresAt == null || !expiresAt.isAfter(Instant.now())) {
            LOGGER.warn("Invite admission rejected: expired invite");
            throw new ApiException(HttpStatus.CONFLICT, "INVITE_UNAVAILABLE", "邀请码已过期");
        }
        Map<String, Object> counter = authMapper.selectTrialAdmission();
        int limit = ((Number) (counter.get("invited_limit") == null ? counter.get("INVITED_LIMIT") : counter.get("invited_limit"))).intValue();
        int used = ((Number) (counter.get("invited_used") == null ? counter.get("INVITED_USED") : counter.get("invited_used"))).intValue();
        if (used >= limit) {
            LOGGER.warn("Invite admission rejected: quota exhausted, used={}, limit={}", used, limit);
            throw new ApiException(HttpStatus.CONFLICT, "ADMISSION_FULL", "受邀试用名额已满，暂未开放注册");
        }
    }

    private void requirePrivacy(String version) {
        if (!properties.getPrivacyVersion().equals(version)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRIVACY_CONSENT_REQUIRED", "请阅读并同意最新版用户协议和隐私政策");
        }
    }

    private String value(Map<String, Object> row, String key) {
        Object value = row.get(key); if (value == null) value = row.get(key.toUpperCase());
        return value == null ? null : String.valueOf(value);
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + " **** " + mobile.substring(mobile.length() - 4);
    }

    /** MySQL Connector/J may return a TIMESTAMP as Timestamp, LocalDateTime or String. */
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
