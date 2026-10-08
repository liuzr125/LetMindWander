package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.mapper.AdminTopicMapper;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTopicService {
  private final AdminTopicMapper mapper;

  public AdminTopicService(AdminTopicMapper mapper) {
    this.mapper = mapper;
  }

  public List<Map<String, Object>> list() {
    return mapper.selectSystemTopics();
  }

  @Transactional
  public void save(String actor, String id, String rawName) {
    save(actor, id, rawName, null);
  }

  @Transactional
  public void save(String actor, String id, String rawName, String rawType) {
    String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
    if (name.isEmpty() || name.length() > 50)
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOPIC_NAME", "主题名称为 1 至 50 个字符");
    String normalized = name.toLowerCase(Locale.ROOT);
    if (id != null) lock(id);
    String type =
        rawType == null ? (id == null ? "technical" : mapper.selectTopicType(id)) : rawType;
    if (!"technical".equals(type) && !"vocabulary".equals(type))
      throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TOPIC_TYPE", "请选择技术知识主题或词书标签");
    if (id != null && "vocabulary".equals(type)) {
      Integer technical = mapper.countTechnicalReferences(id);
      Integer plans = mapper.countPlanReferences(id);
      if (technical > 0 || plans > 0)
        throw new ApiException(
            HttpStatus.CONFLICT, "TOPIC_TYPE_IN_USE", "主题有非单词内容或学习计划引用，不能改为词书标签");
    }
    Integer duplicate = mapper.countDuplicateNames(normalized, normalized, id == null ? "" : id);
    if (duplicate != null && duplicate > 0)
      throw new ApiException(HttpStatus.CONFLICT, "TOPIC_NAME_EXISTS", "该主题名称已存在");
    try {
      if (id == null) {
        id = CryptoUtils.randomId();
        mapper.insertSystemTopic(id, name, normalized, type);
      } else mapper.updateSystemTopic(name, type, id);
    } catch (DuplicateKeyException exception) {
      throw new ApiException(HttpStatus.CONFLICT, "TOPIC_NAME_EXISTS", "该主题名称已存在");
    }
    audit(actor, "topic_save", id);
  }

  @Transactional
  public void delete(String actor, String id) {
    lock(id);
    Integer published = mapper.countPublishedTechnicalReferences(id);
    Integer plans = mapper.countPlanReferencesForDeletion(id);
    if ((published != null && published > 0) || (plans != null && plans > 0))
      throw new ApiException(HttpStatus.CONFLICT, "TOPIC_IN_USE", "主题有已发布技术知识或学习计划引用，不能删除");
    mapper.softDeleteSystemTopic(id);
    audit(actor, "topic_delete", id);
  }

  private void lock(String id) {
    if (mapper.selectSystemTopicForUpdate(id).isEmpty())
      throw new ApiException(HttpStatus.NOT_FOUND, "TOPIC_NOT_FOUND", "系统主题不存在");
  }

  private void audit(String actor, String action, String id) {
    mapper.insertTopicAudit(
        CryptoUtils.randomId(),
        actor,
        action,
        id,
        java.sql.Timestamp.from(java.time.Instant.now().plus(java.time.Duration.ofDays(180))));
  }
}
