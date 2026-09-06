package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SmsVerificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SmsVerificationService.class);
    private final ConcurrentMap<String, Challenge> challenges = new ConcurrentHashMap<String, Challenge>();
    private final SecureRandom random = new SecureRandom();
    private final AppProperties properties;

    public SmsVerificationService(AppProperties properties) {
        this.properties = properties;
    }

    public synchronized SendResult send(String registrationTicket, String mobile) {
        if (!properties.getSms().isMockEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_NOT_CONFIGURED", "短信服务尚未配置");
        }
        Instant now = Instant.now();
        Challenge previous = challenges.get(registrationTicket);
        if (previous != null) {
            Instant resendAt = previous.sentAt.plus(properties.getSms().getResendInterval());
            if (resendAt.isAfter(now)) {
                long retryAfter = Math.max(1, resendAt.getEpochSecond() - now.getEpochSecond());
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SMS_CODE_RATE_LIMITED", "请稍后再获取验证码（" + retryAfter + "秒）");
            }
        }

        String configuredCode = properties.getSms().getFixedCode();
        String code = StringUtils.hasText(configuredCode)
                ? configuredCode
                : String.format("%06d", random.nextInt(1000000));
        Challenge challenge = new Challenge(
                mobile,
                code,
                now,
                now.plus(properties.getSms().getCodeTtl()));
        challenges.put(registrationTicket, challenge);

        // Only the local development adapter writes the code. Production must replace this adapter with an SMS provider.
        LOGGER.info("DEV SMS verification code: mobile={}, code={}, expiresInSeconds={}",
                maskMobile(mobile), code, properties.getSms().getCodeTtl().getSeconds());
        // mock 模式下把验证码返回给小程序端弹窗展示（开发/个人主体阶段没有短信供应商）。
        String returnedCode = properties.getSms().isMockEnabled() ? code : null;
        return new SendResult(returnedCode,
                properties.getSms().getCodeTtl().getSeconds(), properties.getSms().getResendInterval().getSeconds());
    }

    public void verifyAndConsume(String registrationTicket, String mobile, String code) {
        Challenge challenge = challenges.get(registrationTicket);
        if (challenge == null || !challenge.mobile.equals(mobile)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_CODE_REQUIRED", "请先获取短信验证码");
        }
        synchronized (challenge) {
            if (!challenge.expiresAt.isAfter(Instant.now())) {
                challenges.remove(registrationTicket, challenge);
                throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_CODE_EXPIRED", "短信验证码已过期，请重新获取");
            }
            challenge.attempts++;
            if (!challenge.code.equals(code)) {
                LOGGER.warn("SMS verification rejected: mobile={}, attempts={}", maskMobile(mobile), challenge.attempts);
                if (challenge.attempts >= properties.getSms().getMaxAttempts()) {
                    challenges.remove(registrationTicket, challenge);
                    throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_CODE_ATTEMPTS_EXCEEDED", "验证码错误次数过多，请重新获取");
                }
                throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_CODE_INVALID", "短信验证码不正确");
            }
            challenges.remove(registrationTicket, challenge);
            LOGGER.info("SMS verification completed: mobile={}", maskMobile(mobile));
        }
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return "unknown";
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    private static class Challenge {
        private final String mobile;
        private final String code;
        private final Instant sentAt;
        private final Instant expiresAt;
        private int attempts;

        private Challenge(String mobile, String code, Instant sentAt, Instant expiresAt) {
            this.mobile = mobile;
            this.code = code;
            this.sentAt = sentAt;
            this.expiresAt = expiresAt;
        }
    }

    public static class SendResult {
        private final String code;
        private final long expiresIn;
        private final long retryAfter;

        public SendResult(String code, long expiresIn, long retryAfter) {
            this.code = code;
            this.expiresIn = expiresIn;
            this.retryAfter = retryAfter;
        }

        public boolean isSent() { return true; }
        public String getCode() { return code; }
        public long getExpiresIn() { return expiresIn; }
        public long getRetryAfter() { return retryAfter; }
    }
}
