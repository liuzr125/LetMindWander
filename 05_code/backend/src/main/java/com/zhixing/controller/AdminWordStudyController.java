package com.zhixing.controller;

import com.zhixing.config.AdminSessionFilter;
import com.zhixing.service.AdminPrincipal;
import com.zhixing.service.AdminWordStudyService;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/study-records/word-study")
public class AdminWordStudyController {
  private final AdminWordStudyService study;

  public AdminWordStudyController(AdminWordStudyService study) {
    this.study = study;
  }

  /** 查询全局间隔模式；管理员学习记录权限由统一过滤器校验。 */
  @GetMapping("/mode")
  public Map<String, Object> mode() {
    return study.mode();
  }

  /** 校验并切换全局模式，更新与审计同事务，不重排既有到期日期。 */
  @PutMapping("/mode")
  public Map<String, Object> save(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return study.save(
        (AdminPrincipal) request.getAttribute(AdminSessionFilter.PRINCIPAL_ATTRIBUTE), body);
  }

  /** 分页查询学习事件，可按轮次筛选，并批量附带事件所属轮次。 */
  @GetMapping("/events")
  public Map<String, Object> events(
      @RequestParam(required = false) String roundId,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int pageSize) {
    return study.events(roundId, page, pageSize);
  }
}
