package com.zhixing.common;

import org.springframework.http.HttpStatus;

public class AdminLoginThrottledException extends ApiException {
  private final long retryAfterSeconds;

  public AdminLoginThrottledException(long seconds) {
    super(
        HttpStatus.TOO_MANY_REQUESTS,
        "ADMIN_LOGIN_THROTTLED",
        "登录尝试过于频繁，请在 " + Math.max(1, seconds) + " 秒后重试");
    this.retryAfterSeconds = Math.max(1, seconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
