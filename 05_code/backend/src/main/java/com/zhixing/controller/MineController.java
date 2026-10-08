package com.zhixing.controller;

import com.zhixing.dto.UserFeedbackRequest;
import com.zhixing.model.*;
import com.zhixing.service.MineService;
import com.zhixing.service.SessionService;
import java.util.*;
import javax.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mine")
public class MineController {
  private final SessionService sessions;
  private final MineService mine;

  public MineController(SessionService sessions, MineService mine) {
    this.sessions = sessions;
    this.mine = mine;
  }

  private String user(String auth) {
    return sessions.requireUser(auth).getUserId();
  }

  /** 汇总当前用户的个人页统计。 */
  @GetMapping("/overview")
  public MineOverviewView overview(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return mine.overview(user(auth));
  }

  /** 按类型及关键字读取本人收藏。 */
  @GetMapping("/favorites")
  public List<FavoriteView> favorites(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) String query) {
    return mine.favorites(user(auth), type, query);
  }

  /** 返回本人隐私设置与数据请求状态。 */
  @GetMapping("/privacy")
  public PrivacyView privacy(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return mine.privacy(user(auth));
  }

  /** 提交本人数据导出申请，不在请求中导出文件。 */
  @PostMapping("/exports")
  public PrivacyView export(@RequestHeader(value = "Authorization", required = false) String auth) {
    return mine.requestExport(user(auth));
  }

  /** 提交本人注销申请，具体状态转换由服务层处理。 */
  @PostMapping("/deletions")
  public Map<String, Object> delete(
      @RequestHeader(value = "Authorization", required = false) String auth) {
    return mine.requestDeletion(user(auth));
  }

  /** 保存当前用户提交的反馈，正文不写入诊断日志。 */
  @PostMapping("/feedback")
  public Map<String, Object> feedback(
      @RequestHeader(value = "Authorization", required = false) String auth,
      @Valid @RequestBody UserFeedbackRequest request) {
    return mine.feedback(user(auth), request);
  }
}
