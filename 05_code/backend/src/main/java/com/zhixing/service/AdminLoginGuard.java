package com.zhixing.service;

import com.zhixing.common.AdminLoginThrottledException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.config.AppProperties;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 单实例登录保护。只保存账号/来源的摘要，不保存密码、账号或 IP 原文。 */
@Component
public class AdminLoginGuard {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AdminLoginGuard.class);
  private final AppProperties.AdminLoginSecurity config;
  private final Clock clock;
  private final Map<String, State> states = new HashMap<>();

  @Autowired
  public AdminLoginGuard(AppProperties properties) {
    this(properties.getAdminLoginSecurity(), Clock.systemUTC());
  }

  AdminLoginGuard(AppProperties.AdminLoginSecurity config, Clock clock) {
    if (config.getAccountMaxFailures() < 1
        || config.getSourceMaxFailures() < 1
        || config.getMaxTrackedKeys() < 2
        || config.getFailureWindow() == null
        || config.getLockDuration() == null
        || config.getFailureWindow().getSeconds() < 1
        || config.getLockDuration().getSeconds() < 1)
      throw new IllegalArgumentException("管理员登录防护配置不正确");
    this.config = config;
    this.clock = clock;
  }

  public synchronized Attempt begin(String username, String remoteAddress) {
    Instant now = clock.instant();
    Duration retention =
        config.getFailureWindow().compareTo(config.getLockDuration()) > 0
            ? config.getFailureWindow()
            : config.getLockDuration();
    states
        .values()
        .removeIf(state -> state.inFlight == 0 && !state.updatedAt.plus(retention).isAfter(now));
    String accountKey =
        key("account", username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    String sourceKey = key("source", remoteAddress == null ? "unknown" : remoteAddress);
    State account = states.get(accountKey), source = states.get(sourceKey);
    check(account, config.getAccountMaxFailures(), now);
    check(source, config.getSourceMaxFailures(), now);
    int added = (account == null ? 1 : 0) + (source == null ? 1 : 0);
    if (states.size() + added > config.getMaxTrackedKeys())
      throw new AdminLoginThrottledException(60);
    if (account == null) {
      account = new State(now);
      states.put(accountKey, account);
    }
    if (source == null) {
      source = new State(now);
      states.put(sourceKey, source);
    }
    account.inFlight++;
    source.inFlight++;
    account.updatedAt = now;
    source.updatedAt = now;
    return new Attempt(accountKey, sourceKey, account, source);
  }

  private void check(State state, int limit, Instant now) {
    if (state == null) return;
    if (state.lockedUntil != null) {
      if (state.lockedUntil.isAfter(now))
        throw new AdminLoginThrottledException(
            Math.max(1, (Duration.between(now, state.lockedUntil).toMillis() + 999) / 1000));
      state.failures = 0;
      state.firstFailure = null;
      state.lockedUntil = null;
    }
    if (state.firstFailure != null
        && !state.firstFailure.plus(config.getFailureWindow()).isAfter(now)) {
      state.failures = 0;
      state.firstFailure = null;
    }
    // 把正在校验的请求计入容量，避免并发请求在失败落账前一起绕过阈值。
    if (state.failures + state.inFlight >= limit) throw new AdminLoginThrottledException(1);
  }

  private String key(String scope, String value) {
    return scope + ":" + Base64.getEncoder().encodeToString(CryptoUtils.sha256(value));
  }

  private void fail(State state, int limit, String scope, Instant now) {
    if (state.firstFailure == null
        || !state.firstFailure.plus(config.getFailureWindow()).isAfter(now)) {
      state.firstFailure = now;
      state.failures = 0;
    }
    state.failures++;
    if (state.failures >= limit && (state.lockedUntil == null || !state.lockedUntil.isAfter(now))) {
      state.lockedUntil = now.plus(config.getLockDuration());
      LOG.warn("Administrator login protection activated: scope={}", scope);
    }
  }

  private static class State {
    private int failures, inFlight;
    private Instant firstFailure, lockedUntil, updatedAt;

    State(Instant now) {
      updatedAt = now;
    }
  }

  public final class Attempt implements AutoCloseable {
    private final String accountKey, sourceKey;
    private final State account, source;
    private boolean closed;

    private Attempt(String accountKey, String sourceKey, State account, State source) {
      this.accountKey = accountKey;
      this.sourceKey = sourceKey;
      this.account = account;
      this.source = source;
    }

    public void failed() {
      finish(false, true);
    }

    public void succeeded() {
      finish(true, false);
    }

    @Override
    public void close() {
      finish(false, false);
    }

    private void finish(boolean success, boolean failure) {
      synchronized (AdminLoginGuard.this) {
        if (closed) return;
        closed = true;
        Instant now = clock.instant();
        account.inFlight--;
        source.inFlight--;
        account.updatedAt = now;
        source.updatedAt = now;
        if (failure) {
          fail(account, config.getAccountMaxFailures(), "account", now);
          fail(source, config.getSourceMaxFailures(), "source", now);
        } else if (success) {
          account.failures = 0;
          account.firstFailure = null;
          account.lockedUntil = null;
          // 来源失败数不能被另一个账号的成功登录清除。
        }
        discardUnused(accountKey, account);
        discardUnused(sourceKey, source);
      }
    }

    private void discardUnused(String key, State state) {
      if (state.inFlight == 0 && state.failures == 0 && state.lockedUntil == null)
        states.remove(key, state);
    }
  }
}
