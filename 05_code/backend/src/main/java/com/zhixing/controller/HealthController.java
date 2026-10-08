package com.zhixing.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
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
