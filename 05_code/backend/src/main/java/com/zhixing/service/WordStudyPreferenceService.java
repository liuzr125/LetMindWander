package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.mapper.WordStudyModeMapper;
import com.zhixing.mapper.WordStudyPreferenceMapper;
import com.zhixing.model.WordStudyModeEntity;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Account-level defaults for entering a new-word page and replaying pronunciation. */
@Service
public class WordStudyPreferenceService {
  private final WordStudyPreferenceMapper preferences;
  private final WordStudyModeMapper modes;

  public WordStudyPreferenceService(
      WordStudyPreferenceMapper preferences, WordStudyModeMapper modes) {
    this.preferences = preferences;
    this.modes = modes;
  }

  public Map<String, Object> preferences(String userId) {
    List<Map<String, Object>> rows = preferences.selectActive(userId);
    if (rows.isEmpty()) {
      Map<String, Object> result = defaults();
      result.put("intervalMode", intervalMode(userId));
      return result;
    }
    Map<String, Object> row = rows.get(0);
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("defaultAnswerMode", String.valueOf(row.get("default_answer_mode")));
    result.put("autoPlayEnabled", number(row.get("auto_play_enabled")) != 0);
    result.put("autoPlayAccent", String.valueOf(row.get("auto_play_accent")));
    result.put("autoPlayCount", number(row.get("auto_play_count")));
    result.put("autoPlayIntervalMs", number(row.get("auto_play_interval_ms")));
    result.put("rowVersion", number(row.get("row_version")));
    result.put("intervalMode", intervalMode(userId));
    return result;
  }

  @Transactional
  public Map<String, Object> update(String userId, Map<String, Object> request) {
    Map<String, Object> current = preferences(userId);
    if (request != null && request.containsKey("intervalMode"))
      throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_ONLY_SETTING", "复习模式仅由管理员修改");
    String mode =
        text(request, "defaultAnswerMode", String.valueOf(current.get("defaultAnswerMode")));
    String accent = text(request, "autoPlayAccent", String.valueOf(current.get("autoPlayAccent")));
    boolean enabled =
        bool(request, "autoPlayEnabled", Boolean.TRUE.equals(current.get("autoPlayEnabled")));
    int count = integer(request, "autoPlayCount", number(current.get("autoPlayCount")));
    int interval =
        integer(request, "autoPlayIntervalMs", number(current.get("autoPlayIntervalMs")));
    if (!Arrays.asList("visible", "hidden").contains(mode))
      throw invalid("defaultAnswerMode 仅支持 visible 或 hidden");
    if (!Arrays.asList("uk", "us").contains(accent)) throw invalid("autoPlayAccent 仅支持 uk 或 us");
    if (count < 1 || count > 5) throw invalid("自动播放次数必须为 1–5 次");
    if (interval < 1000 || interval > 2000) throw invalid("播放间隔必须为 1000–2000 毫秒");
    int changed =
        preferences.updatePreferences(userId, mode, enabled ? 1 : 0, accent, count, interval);
    if (changed == 0)
      preferences.insertPreferences(userId, mode, enabled ? 1 : 0, accent, count, interval);
    return preferences(userId);
  }

  public static Map<String, Object> defaults() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("intervalMode", "fixed");
    result.put("defaultAnswerMode", "visible");
    result.put("autoPlayEnabled", true);
    result.put("autoPlayAccent", "uk");
    result.put("autoPlayCount", 1);
    result.put("autoPlayIntervalMs", 1500);
    result.put("rowVersion", 0);
    return result;
  }

  public String intervalMode(String userId) {
    WordStudyModeEntity mode = modes.selectById(1);
    return mode == null ? "fixed" : mode.getIntervalMode();
  }

  private ApiException invalid(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "WORD_STUDY_PREFERENCE_INVALID", message);
  }

  private String text(Map<String, Object> request, String key, String fallback) {
    Object value = request == null ? null : request.get(key);
    return value == null ? fallback : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
  }

  private boolean bool(Map<String, Object> request, String key, boolean fallback) {
    Object value = request == null ? null : request.get(key);
    if (value == null) return fallback;
    if (value instanceof Boolean) return (Boolean) value;
    if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
    if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
    throw invalid(key + " 必须为布尔值");
  }

  private int integer(Map<String, Object> request, String key, int fallback) {
    Object value = request == null ? null : request.get(key);
    if (value == null) return fallback;
    try {
      return number(value);
    } catch (Exception e) {
      throw invalid(key + " 必须为整数");
    }
  }

  private int number(Object value) {
    return value instanceof Number
        ? ((Number) value).intValue()
        : Integer.parseInt(String.valueOf(value));
  }
}
