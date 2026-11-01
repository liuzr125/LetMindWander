package com.zhixing.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.zhixing.common.*;
import com.zhixing.config.AppProperties;
import com.zhixing.controller.AdminAuthController;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminLoginGuardTest {
  private final AppProperties.AdminLoginSecurity config = new AppProperties.AdminLoginSecurity();
  private final MutableClock clock = new MutableClock();
  private final AdminLoginGuard guard = new AdminLoginGuard(config, clock);

  private void fail(String name, String ip) {
    try (AdminLoginGuard.Attempt attempt = guard.begin(name, ip)) {
      attempt.failed();
    }
  }

  @Test
  void accountLocksAcrossDifferentSourcesAndIgnoresCaseAndWhitespace() {
    for (int i = 0; i < 5; i++) fail(i % 2 == 0 ? " Admin " : "ADMIN", "ip-" + i);
    AdminLoginThrottledException error =
        assertThrows(AdminLoginThrottledException.class, () -> guard.begin("admin", "new-ip"));
    assertEquals(900, error.getRetryAfterSeconds());
    assertEquals("ADMIN_LOGIN_THROTTLED", error.getCode());
    clock.advance(899);
    assertEquals(
        1,
        assertThrows(AdminLoginThrottledException.class, () -> guard.begin("admin", "new-ip"))
            .getRetryAfterSeconds());
    clock.advance(1);
    try (AdminLoginGuard.Attempt attempt = guard.begin("admin", "new-ip")) {
      attempt.succeeded();
    }
  }

  @Test
  void sourceLocksAcrossAccountsAndCannotBeResetBySuccessfulLogin() {
    for (int i = 0; i < 19; i++) fail("admin" + i, "same-ip");
    try (AdminLoginGuard.Attempt attempt = guard.begin("valid-admin", "same-ip")) {
      attempt.succeeded();
    }
    fail("last-admin", "same-ip");
    assertThrows(AdminLoginThrottledException.class, () -> guard.begin("other-admin", "same-ip"));
    try (AdminLoginGuard.Attempt attempt = guard.begin("other-admin", "different-ip")) {
      attempt.succeeded();
    }
  }

  @Test
  void successResetsAccountFailuresButServerErrorsDoNotCountAsFailures() {
    for (int i = 0; i < 4; i++) fail("admin", "ip" + i);
    try (AdminLoginGuard.Attempt attempt = guard.begin("admin", "success-ip")) {
      attempt.succeeded();
    }
    for (int i = 0; i < 4; i++) fail("admin", "next-ip" + i);
    for (int i = 0; i < 10; i++)
      try (AdminLoginGuard.Attempt ignored = guard.begin("admin", "error-ip")) {}
    fail("admin", "last-ip");
    assertThrows(AdminLoginThrottledException.class, () -> guard.begin("admin", "blocked-ip"));
  }

  @Test
  void expiredFailureWindowResetsCounter() {
    for (int i = 0; i < 4; i++) fail("admin", "ip");
    clock.advance(900);
    for (int i = 0; i < 4; i++) fail("admin", "ip");
    try (AdminLoginGuard.Attempt attempt = guard.begin("admin", "ip")) {
      attempt.succeeded();
    }
  }

  @Test
  void inFlightReservationsCannotBypassLimitsAndCloseIsIdempotent() {
    List<AdminLoginGuard.Attempt> attempts = new ArrayList<>();
    for (int i = 0; i < 5; i++) attempts.add(guard.begin("admin", "ip" + i));
    assertThrows(AdminLoginThrottledException.class, () -> guard.begin("admin", "ip-other"));
    for (AdminLoginGuard.Attempt attempt : attempts) {
      attempt.close();
      attempt.close();
    }
    for (int i = 0; i < 5; i++) fail("admin", "ip" + i);
    assertThrows(AdminLoginThrottledException.class, () -> guard.begin("admin", "ip-other"));
  }

  @Test
  void memoryCapRejectsNewKeysWithoutEvictingActiveLocksAndExpiresIdleKeys() {
    AppProperties.AdminLoginSecurity small = new AppProperties.AdminLoginSecurity();
    small.setMaxTrackedKeys(2);
    AdminLoginGuard bounded = new AdminLoginGuard(small, clock);
    for (int i = 0; i < 5; i++)
      try (AdminLoginGuard.Attempt attempt = bounded.begin("admin", "ip")) {
        attempt.failed();
      }
    assertThrows(AdminLoginThrottledException.class, () -> bounded.begin("other", "other-ip"));
    assertThrows(AdminLoginThrottledException.class, () -> bounded.begin("admin", "ip"));
    clock.advance(900);
    try (AdminLoginGuard.Attempt attempt = bounded.begin("other", "other-ip")) {
      attempt.succeeded();
    }
  }

  @Test
  void invalidConfigurationIsRejected() {
    AppProperties.AdminLoginSecurity invalid = new AppProperties.AdminLoginSecurity();
    invalid.setLockDuration(Duration.ZERO);
    assertThrows(IllegalArgumentException.class, () -> new AdminLoginGuard(invalid, clock));
  }

  @Test
  void cancelledChecksAndSuccessfulLoginsDoNotFillTrackingCapacity() {
    AppProperties.AdminLoginSecurity small = new AppProperties.AdminLoginSecurity();
    small.setMaxTrackedKeys(2);
    AdminLoginGuard bounded = new AdminLoginGuard(small, clock);
    for (int i = 0; i < 50; i++) {
      try (AdminLoginGuard.Attempt ignored = bounded.begin("cancelled-" + i, "ip-" + i)) {}
      try (AdminLoginGuard.Attempt attempt = bounded.begin("valid-" + i, "ip-" + i)) {
        attempt.succeeded();
      }
    }
  }

  @Test
  void loginEndpointReturnsRetryAfterAndDoesNotTrustForwardedFor() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    when(auth.login(anyString(), anyString()))
        .thenThrow(new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_LOGIN_INVALID", "账号或密码错误"));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new AdminAuthController(auth, guard))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    for (int i = 0; i < 20; i++)
      mvc.perform(
              post("/api/admin/auth/login")
                  .header("X-Forwarded-For", "spoofed-" + i)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"username\":\"admin" + i + "\",\"password\":\"wrong\"}"))
          .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/admin/auth/login")
                .header("X-Forwarded-For", "new-spoofed")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"another\",\"password\":\"wrong\"}"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "900"))
        .andExpect(jsonPath("$.retryAfterSeconds").value(900))
        .andExpect(jsonPath("$.code").value("ADMIN_LOGIN_THROTTLED"));
    verify(auth, times(20)).login(anyString(), anyString());
  }

  @Test
  void serviceErrorsReleaseCapacityWithoutLockingOutAdministrators() throws Exception {
    AdminAuthService auth = mock(AdminAuthService.class);
    when(auth.login(anyString(), anyString()))
        .thenThrow(
            new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_BOOTSTRAP_REQUIRED", "配置缺失"));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(new AdminAuthController(auth, guard))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    for (int i = 0; i < 7; i++)
      mvc.perform(
              post("/api/admin/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
          .andExpect(status().isServiceUnavailable());
    verify(auth, times(7)).login(anyString(), anyString());
  }

  private static class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-31T00:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }

    void advance(long seconds) {
      now = now.plusSeconds(seconds);
    }
  }
}
