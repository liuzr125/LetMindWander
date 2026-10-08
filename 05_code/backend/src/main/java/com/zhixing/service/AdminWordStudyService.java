package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.AdminAuditMapper;
import com.zhixing.mapper.AdminWordStudyMapper;
import com.zhixing.mapper.WordStudyModeMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminWordStudyService {
  private final AdminWordStudyMapper events;
  private final WordStudyModeMapper modes;
  private final AdminAuditMapper audits;
  private final WordStudyPreferenceService preferences;

  public AdminWordStudyService(
      AdminWordStudyMapper events,
      WordStudyModeMapper modes,
      AdminAuditMapper audits,
      WordStudyPreferenceService preferences) {
    this.events = events;
    this.modes = modes;
    this.audits = audits;
    this.preferences = preferences;
  }

  public Map<String, Object> mode() {
    return Collections.<String, Object>singletonMap("intervalMode", preferences.intervalMode(null));
  }

  @Transactional
  public Map<String, Object> save(AdminPrincipal actor, Map<String, Object> body) {
    if (actor == null)
      throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_UNAUTHORIZED", "需要管理员会话");
    String next = String.valueOf(body.get("intervalMode"));
    if (!Arrays.asList("fixed", "flexible").contains(next))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_INTERVAL_MODE", "仅支持固定或灵活间隔");
    // 单例行锁序列化并发模式切换，确保审计 before 值对应本次修改。
    modes.initializeGlobalMode();
    String before = modes.lockGlobalMode();
    modes.updateGlobalMode(next);
    if (!before.equals(next))
      audits.insertAudit(
          CryptoUtils.randomId(),
          actor.getAccountId(),
          "word_study_mode_update",
          "word_study_mode",
          "global",
          "succeeded",
          "{\"before\":\"" + before + "\",\"after\":\"" + next + "\"}",
          Instant.now().plusSeconds(180L * 86400));
    return mode();
  }

  public Map<String, Object> events(String roundId, int page, int pageSize) {
    if (page < 1 || pageSize < 1 || pageSize > 100)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGE", "每页 1 至 100 条");
    int total = events.countEvents(roundId);
    List<Map<String, Object>> rows = events.selectEvents(roundId, (page - 1) * pageSize, pageSize);
    Map<String, List<Map<String, Object>>> rounds = new HashMap<>();
    // 一次批量查询轮次，避免逐事件查库；无事件时跳过额外查询。
    if (!rows.isEmpty()) {
      List<String> ids = new ArrayList<>();
      for (Map<String, Object> row : rows) ids.add(String.valueOf(row.get("id")));
      for (Map<String, Object> round : events.selectEventRounds(ids)) {
        String eventId = String.valueOf(round.remove("event_id"));
        rounds.computeIfAbsent(eventId, k -> new ArrayList<>()).add(round);
      }
    }
    for (Map<String, Object> row : rows)
      row.put(
          "rounds", rounds.getOrDefault(String.valueOf(row.get("id")), Collections.emptyList()));
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("items", rows);
    out.put("total", total);
    out.put("page", page);
    out.put("pageSize", pageSize);
    return out;
  }
}
