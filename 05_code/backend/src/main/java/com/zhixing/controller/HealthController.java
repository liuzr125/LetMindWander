package com.zhixing.controller;

import com.zhixing.service.ReadinessService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
  private final ReadinessService readiness;

  public HealthController(ReadinessService readiness) {
    this.readiness = readiness;
  }

  @GetMapping("/health/live")
  public ResponseEntity<Map<String, String>> live() {
    return ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(java.util.Collections.singletonMap("status", "UP"));
  }

  @GetMapping("/health/ready")
  public ResponseEntity<Map<String, String>> ready() {
    boolean available = readiness.ready();
    return ResponseEntity.status(available ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .header("Cache-Control", "no-store")
        .body(java.util.Collections.singletonMap("status", available ? "UP" : "DOWN"));
  }

  /** 返回进程存活状态；不执行外部依赖健康探测。 */
  @GetMapping("/health")
  public Map<String, Object> health() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("status", "UP");
    result.put("module", "F01");
    result.put("modules", java.util.Arrays.asList("F01", "F02"));
    return result;
  }
}
