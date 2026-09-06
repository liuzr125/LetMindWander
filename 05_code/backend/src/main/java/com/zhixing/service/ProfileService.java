package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.dto.UpdateProfileRequest;
import com.zhixing.model.ProfileView;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * F02 个人资料：本人资料读取与更新。校验规则与文档一致：
 * 昵称 1-30 必填；真实姓名≤50；英语名字≤100；生日不得晚于今天；性别 0/1/2/3；
 * 爱好≤10 项每项≤20 去重去空；自我介绍≤500；可见范围 private/friends/public。
 * 手机号与微信身份字段不在更新范围内。
 */
@Service
public class ProfileService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileService.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> VISIBILITY_LEVELS = new HashSet<String>(Arrays.asList("private", "friends", "public"));
    private static final Set<String> VISIBILITY_FIELDS =
            new HashSet<String>(Arrays.asList("real_name", "english_name", "birthday", "gender", "hobbies", "introduction"));
    private static final List<String> AVATAR_ALLOWED_HOSTS = Arrays.asList(
            "thirdwx.qlogo.cn", "wx.qlogo.cn", "mmbiz.qpic.cn",
            "let-mind-wander.oss-cn-beijing.aliyuncs.com");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MediaService media;

    public ProfileService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, MediaService media) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.media = media;
    }

    public ProfileView findView(String userId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, nickname, mobile, avatar_url, status, current_plan_id, short_id, real_name, english_name, " +
                        "birthday, gender, hobbies_json, introduction, profile_visibility, row_version " +
                        "FROM app_user WHERE id = ?", userId);
        if (rows.isEmpty()) return null;
        return toView(rows.get(0));
    }

    @Transactional
    public ProfileView update(String userId, UpdateProfileRequest request) {
        String nickname = validateNickname(request.getNickname());
        String realName = trimToNull(validateLength("真实姓名", request.getRealName(), 50));
        String englishName = trimToNull(validateLength("英语名字", request.getEnglishName(), 100));
        Date birthday = validateBirthday(request.getBirthday());
        Integer gender = validateGender(request.getGender());
        List<String> hobbies = validateHobbies(request.getHobbies());
        String introduction = trimToNull(validateLength("自我介绍", request.getIntroduction(), 500));
        String visibility = validateVisibility(request.getVisibility());
        Map<String, String> fieldVisibility = buildFieldVisibility(visibility);
        if (request.getProfileVisibility() != null) {
            for (Map.Entry<String, String> entry : request.getProfileVisibility().entrySet()) {
                if (!VISIBILITY_FIELDS.contains(entry.getKey())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISIBILITY", "不支持的资料字段");
                }
                fieldVisibility.put(entry.getKey(), validateVisibility(entry.getValue()));
            }
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, avatar_url, row_version, status FROM app_user WHERE id = ? FOR UPDATE", userId);
        if (rows.isEmpty() || !"active".equals(string(rows.get(0), "status"))) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在或不可访问");
        }
        Map<String, Object> current = rows.get(0);
        if (request.getRowVersion() == null || request.getRowVersion().intValue() != ((Number) raw(current, "row_version")).intValue()) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT", "资料已变更，请重新加载后修改");
        }
        String avatarUrl = request.getAvatarUrl();
        if (avatarUrl != null && avatarUrl.trim().isEmpty()) avatarUrl = null;
        if (avatarUrl != null && !avatarUrl.equals(string(current, "avatar_url"))) {
            media.requireOwnedAvatar(userId, avatarUrl);
        }

        jdbcTemplate.update(
                "UPDATE app_user SET nickname = ?, real_name = ?, english_name = ?, birthday = ?, gender = ?, " +
                        "hobbies_json = ?, introduction = ?, profile_visibility = ?, avatar_url = ?, " +
                        "row_version = row_version + 1, updated_at = NOW(3) WHERE id = ?",
                nickname, realName, englishName, birthday, gender,
                toJson(hobbies), introduction, toJson(fieldVisibility), avatarUrl, userId);
        LOGGER.info("Profile updated: userId={}", userId);
        return findView(userId);
    }

    // ---------- 校验 ----------

    private String validateNickname(String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > 30 || value.matches(".*[\\p{Cntrl}].*")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "昵称需为 1 至 30 个字符");
        }
        return value;
    }

    private String validateLength(String label, String value, int max) {
        if (value == null) return null;
        int length = value.trim().codePointCount(0, value.trim().length());
        if (length > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", label + "最多 " + max + " 个字符");
        }
        return value.trim();
    }

    private Date validateBirthday(String birthday) {
        if (birthday == null || birthday.trim().isEmpty()) return null;
        String value = birthday.trim();
        LocalDate parsed;
        try {
            parsed = LocalDate.parse(value);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BIRTHDAY", "生日格式应为 YYYY-MM-DD");
        }
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (parsed.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BIRTHDAY", "生日不能晚于今天");
        }
        return Date.valueOf(parsed);
    }

    private Integer validateGender(Integer gender) {
        if (gender == null) return 0;
        if (gender < 0 || gender > 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GENDER", "性别取值不合法");
        }
        return gender;
    }

    private List<String> validateHobbies(List<String> hobbies) {
        if (hobbies == null || hobbies.isEmpty()) return new ArrayList<String>();
        Set<String> unique = new LinkedHashSet<String>();
        for (String hobby : hobbies) {
            if (hobby == null) continue;
            String value = hobby.trim();
            if (value.isEmpty()) continue;
            if (value.codePointCount(0, value.length()) > 20) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOBBY", "每个爱好最多 20 个字符");
            }
            unique.add(value);
            if (unique.size() > 10) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOBBY", "爱好最多 10 项");
            }
        }
        return new ArrayList<String>(unique);
    }

    private String validateVisibility(String visibility) {
        if (visibility == null || visibility.trim().isEmpty()) return "private";
        String value = visibility.trim().toLowerCase();
        if (!VISIBILITY_LEVELS.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISIBILITY", "资料可见范围不合法");
        }
        return value;
    }

    /** 字段级可见范围映射：未指定时全部跟随整体缺省 private。 */
    private Map<String, String> buildFieldVisibility(String visibility) {
        String level = visibility == null || visibility.trim().isEmpty() ? "private" : visibility.trim().toLowerCase();
        Map<String, String> mapping = new LinkedHashMap<String, String>();
        for (String field : VISIBILITY_FIELDS) mapping.put(field, level);
        return mapping;
    }

    /** Only server-side active relations grant friends visibility. */
    public Map<String, Object> findVisible(String viewerId, String targetId) {
        ProfileView view = findView(targetId);
        if (view == null || !"active".equals(view.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在或不可访问");
        }
        String low = viewerId.compareTo(targetId) < 0 ? viewerId : targetId;
        String high = viewerId.compareTo(targetId) < 0 ? targetId : viewerId;
        boolean friend = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM friend_relation WHERE user_low_id = ? AND user_high_id = ? AND state = 'active'",
                Integer.class, low, high) > 0;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", view.getId());
        result.put("shortId", view.getShortId());
        result.put("nickname", view.getNickname());
        result.put("avatarUrl", view.getAvatarUrl());
        result.put("isFriend", friend);
        result.put("readOnly", true);
        Map<String, String> levels = view.getProfileVisibility();
        Map<String, Object> fields = new LinkedHashMap<String, Object>();
        fields.put("real_name", view.getRealName());
        fields.put("english_name", view.getEnglishName());
        fields.put("birthday", view.getBirthday());
        fields.put("gender", view.getGender());
        fields.put("hobbies", view.getHobbies());
        fields.put("introduction", view.getIntroduction());
        Map<String, Object> visible = new LinkedHashMap<String, Object>();
        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            String level = levels == null ? "private" : levels.get(entry.getKey());
            if ("public".equals(level) || (friend && "friends".equals(level))) {
                visible.put(entry.getKey(), entry.getValue());
            }
        }
        result.put("fields", visible);
        return result;
    }

    // ---------- 组装 ----------

    public ProfileView toView(Map<String, Object> row) {
        ProfileView view = new ProfileView();
        view.setId(string(row, "id"));
        view.setRowVersion(((Number) raw(row, "row_version")).intValue());
        view.setNickname(string(row, "nickname"));
        view.setAvatarUrl(string(row, "avatar_url"));
        view.setMobileMasked(maskMobile(string(row, "mobile")));
        view.setStatus(string(row, "status"));
        view.setCurrentPlanId(string(row, "current_plan_id"));
        view.setShortId(string(row, "short_id"));
        view.setRealName(string(row, "real_name"));
        view.setEnglishName(string(row, "english_name"));
        String birthday = string(row, "birthday");
        view.setBirthday(birthday == null ? null : birthday.substring(0, Math.min(10, birthday.length())));
        Object gender = raw(row, "gender");
        view.setGender(gender == null ? 0 : Integer.valueOf(String.valueOf(gender)));
        view.setHobbies(fromJsonList(string(row, "hobbies_json")));
        view.setIntroduction(string(row, "introduction"));
        Map<String, String> visibility = fromJsonMap(string(row, "profile_visibility"));
        view.setProfileVisibility(visibility);
        view.setVisibility(overallVisibility(visibility));
        return view;
    }

    private String overallVisibility(Map<String, String> fieldVisibility) {
        if (fieldVisibility == null || fieldVisibility.isEmpty()) return "private";
        // 整体可见范围取各字段中的最宽松级别（对外展示语义）。
        if (fieldVisibility.containsValue("public")) return "public";
        if (fieldVisibility.containsValue("friends")) return "friends";
        return "private";
    }

    private String maskMobile(String mobile) {
        if (mobile == null) return null;
        if (mobile.length() < 7) return "****";
        if (mobile.startsWith("+86") && mobile.length() == 14) mobile = mobile.substring(3);
        return mobile.substring(0, 3) + " **** " + mobile.substring(mobile.length() - 4);
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_FAILED", "数据处理失败");
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.trim().isEmpty()) return new ArrayList<String>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return new ArrayList<String>();
        }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (Exception exception) {
            return null;
        }
    }

    private String string(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value instanceof byte[] ? new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8) : String.valueOf(value);
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase());
        return value;
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
