package com.zhixing.service;

import com.zhixing.common.CryptoUtils;
import com.zhixing.model.RegistrationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.registration-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRegistrationTicketStore implements RegistrationTicketStore {
    private final ConcurrentHashMap<String, RegistrationContext> values = new ConcurrentHashMap<String, RegistrationContext>();

    @Override
    public String create(RegistrationContext context) {
        String ticket = CryptoUtils.randomToken(32);
        values.put(key(ticket), context);
        return ticket;
    }

    @Override
    public RegistrationContext get(String ticket) {
        RegistrationContext context = values.get(key(ticket));
        if (context != null && context.getExpiresAt().isBefore(Instant.now())) {
            values.remove(key(ticket));
            return null;
        }
        return context;
    }

    @Override
    public void save(String ticket, RegistrationContext context) {
        values.put(key(ticket), context);
    }

    private String key(String ticket) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(CryptoUtils.sha256(ticket));
    }
}
