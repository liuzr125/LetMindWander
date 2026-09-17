package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.dto.UpdateProfileRequest;
import com.zhixing.entity.AppUserEntity;
import com.zhixing.mapper.AppUserMapper;
import com.zhixing.model.ProfileView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/** Profile business rules. All persistence is delegated to the MyBatis-Plus mapper. */
@Service
public class ProfileService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> LEVELS = new HashSet<String>(Arrays.asList("private", "friends", "public"));
    private static final List<String> FIELDS = Arrays.asList("real_name", "english_name", "birthday", "gender", "hobbies", "introduction");
    private final AppUserMapper users;
    private final ObjectMapper json;
    private final MediaService media;

    public ProfileService(AppUserMapper users, ObjectMapper json, MediaService media) {
        this.users = users;
        this.json = json;
        this.media = media;
    }

    public ProfileView findView(String userId) {
        AppUserEntity entity = users.selectUserById(userId);
        return entity == null ? null : toView(entity);
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
        Map<String, String> visibility = buildVisibility(request.getVisibility());
        if (request.getProfileVisibility() != null) {
            for (Map.Entry<String, String> entry : request.getProfileVisibility().entrySet()) {
                if (!FIELDS.contains(entry.getKey())) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISIBILITY", "不支持的资料字段");
                }
                visibility.put(entry.getKey(), validateVisibility(entry.getValue()));
            }
        }

        AppUserEntity current = users.selectProfileForUpdate(userId);
        if (current == null || !"active".equals(current.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在或不可访问");
        }
        if (request.getRowVersion() == null || !request.getRowVersion().equals(current.getRowVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT", "资料已变更，请重新加载后修改");
        }
        String avatar = trimToNull(request.getAvatarUrl());
        if (avatar != null && !avatar.equals(current.getAvatarUrl())) media.requireOwnedAvatar(userId, avatar);
        int changed = users.updateProfile(userId, nickname, realName, englishName, birthday, gender, toJson(hobbies),
                introduction, toJson(visibility), avatar, request.getRowVersion());
        if (changed != 1) throw new ApiException(HttpStatus.CONFLICT, "PROFILE_VERSION_CONFLICT", "资料已变更，请重新加载后修改");
        return findView(userId);
    }

    /** A limited friend/public profile never returns the phone number. */
    public Map<String, Object> findVisible(String viewerId, String targetId) {
        ProfileView view = findView(targetId);
        if (view == null || !"active".equals(view.getStatus())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在或不可访问");
        }
        String low = viewerId.compareTo(targetId) < 0 ? viewerId : targetId;
        String high = viewerId.compareTo(targetId) < 0 ? targetId : viewerId;
        boolean friend = users.countActiveFriend(low, high) > 0;
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("id", view.getId()); result.put("shortId", view.getShortId());
        result.put("nickname", view.getNickname()); result.put("avatarUrl", view.getAvatarUrl());
        result.put("isFriend", friend); result.put("readOnly", true);
        Map<String, Object> source = new LinkedHashMap<String, Object>();
        source.put("real_name", view.getRealName()); source.put("english_name", view.getEnglishName());
        source.put("birthday", view.getBirthday()); source.put("gender", view.getGender());
        source.put("hobbies", view.getHobbies()); source.put("introduction", view.getIntroduction());
        Map<String, Object> visible = new LinkedHashMap<String, Object>();
        Map<String, String> levels = view.getProfileVisibility();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String level = levels == null ? "private" : levels.get(entry.getKey());
            if ("public".equals(level) || (friend && "friends".equals(level))) visible.put(entry.getKey(), entry.getValue());
        }
        result.put("fields", visible);
        return result;
    }

    public ProfileView toView(AppUserEntity entity) {
        ProfileView view = new ProfileView();
        view.setId(entity.getId()); view.setRowVersion(entity.getRowVersion()); view.setNickname(entity.getNickname());
        view.setAvatarUrl(entity.getAvatarUrl()); view.setMobileMasked(mask(entity.getMobile())); view.setStatus(entity.getStatus());
        view.setCurrentPlanId(entity.getCurrentPlanId()); view.setShortId(entity.getShortId()); view.setRealName(entity.getRealName());
        view.setEnglishName(entity.getEnglishName()); view.setBirthday(entity.getBirthday() == null ? null : entity.getBirthday().toString());
        view.setGender(entity.getGender() == null ? 0 : entity.getGender()); view.setHobbies(readList(entity.getHobbiesJson()));
        view.setIntroduction(entity.getIntroduction()); Map<String, String> mapping = readMap(entity.getProfileVisibility());
        view.setProfileVisibility(mapping); view.setVisibility(mapping.values().contains("public") ? "public" : mapping.values().contains("friends") ? "friends" : "private");
        return view;
    }

    private String validateNickname(String value) { value = value == null ? "" : value.trim(); int length = value.codePointCount(0, value.length()); if (length < 1 || length > 30 || value.matches(".*[\\p{Cntrl}].*")) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_NICKNAME", "昵称需为 1 至 30 个字符"); return value; }
    private String validateLength(String label, String value, int max) { if (value == null) return null; value = value.trim(); if (value.codePointCount(0, value.length()) > max) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FIELD", label + "最多 " + max + " 个字符"); return value; }
    private Date validateBirthday(String value) { if (value == null || value.trim().isEmpty()) return null; try { LocalDate date = LocalDate.parse(value.trim()); if (date.isAfter(LocalDate.now(BUSINESS_ZONE))) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BIRTHDAY", "生日不能晚于今天"); return Date.valueOf(date); } catch (ApiException exception) { throw exception; } catch (Exception exception) { throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BIRTHDAY", "生日格式应为 YYYY-MM-DD"); } }
    private Integer validateGender(Integer value) { int gender = value == null ? 0 : value; if (gender < 0 || gender > 3) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GENDER", "性别取值不合法"); return gender; }
    private List<String> validateHobbies(List<String> values) { LinkedHashSet<String> unique = new LinkedHashSet<String>(); if (values != null) for (String raw : values) { String value = raw == null ? "" : raw.trim(); if (value.isEmpty()) continue; if (value.codePointCount(0, value.length()) > 20) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOBBY", "每个爱好最多 20 个字符"); unique.add(value); if (unique.size() > 10) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HOBBY", "爱好最多 10 项"); } return new ArrayList<String>(unique); }
    private String validateVisibility(String value) { String level = value == null || value.trim().isEmpty() ? "private" : value.trim().toLowerCase(); if (!LEVELS.contains(level)) throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_VISIBILITY", "资料可见范围不合法"); return level; }
    private Map<String, String> buildVisibility(String value) { Map<String, String> result = new LinkedHashMap<String, String>(); String level = validateVisibility(value); for (String field : FIELDS) result.put(field, level); return result; }
    private String toJson(Object value) { try { return json.writeValueAsString(value); } catch (Exception exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "SERIALIZATION_FAILED", "数据处理失败"); } }
    private List<String> readList(String value) { try { return value == null ? new ArrayList<String>() : json.readValue(value, new TypeReference<List<String>>() { }); } catch (Exception exception) { return new ArrayList<String>(); } }
    private Map<String, String> readMap(String value) { try { Map<String, String> result = value == null ? new LinkedHashMap<String, String>() : json.readValue(value, new TypeReference<LinkedHashMap<String, String>>() { }); for (String field : FIELDS) if (!result.containsKey(field)) result.put(field, "private"); return result; } catch (Exception exception) { return buildVisibility("private"); } }
    private String mask(String mobile) { if (mobile == null) return null; if (mobile.startsWith("+86") && mobile.length() == 14) mobile = mobile.substring(3); return mobile.length() < 7 ? "****" : mobile.substring(0, 3) + " **** " + mobile.substring(mobile.length() - 4); }
    private String trimToNull(String value) { return value == null || value.trim().isEmpty() ? null : value.trim(); }
}
