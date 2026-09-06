package com.zhixing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.CryptoUtils;
import com.zhixing.model.RegistrationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Component
@ConditionalOnProperty(name = "app.registration-store", havingValue = "redis")
public class RedisRegistrationTicketStore implements RegistrationTicketStore {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisRegistrationTicketStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Override
    public String create(RegistrationContext context) {
        String ticket = CryptoUtils.randomToken(32);
        save(ticket, context);
        return ticket;
    }

    @Override
    public RegistrationContext get(String ticket) {
        try {
            String json = redis.opsForValue().get(key(ticket));
            return json == null ? null : objectMapper.readValue(json, RegistrationContext.class);
        } catch (Exception exception) {
            throw new IllegalStateException("读取待注册状态失败", exception);
        }
    }

    @Override
    public void save(String ticket, RegistrationContext context) {
        try {
            Duration ttl = Duration.between(Instant.now(), context.getExpiresAt());
            if (ttl.isNegative() || ttl.isZero()) return;
            redis.opsForValue().set(key(ticket), objectMapper.writeValueAsString(context), ttl);
        } catch (Exception exception) {
            throw new IllegalStateException("保存待注册状态失败", exception);
        }
    }

    private String key(String ticket) {
        return "zhixing:f01:registration:" + Base64.getUrlEncoder().withoutPadding().encodeToString(CryptoUtils.sha256(ticket));
    }
}
