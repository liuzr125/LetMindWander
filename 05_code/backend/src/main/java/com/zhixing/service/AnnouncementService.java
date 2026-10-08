package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.entity.AnnouncementEntity;
import com.zhixing.mapper.AdminAuditMapper;
import com.zhixing.mapper.AnnouncementMapper;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnouncementService {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AnnouncementService.class);
  private final AnnouncementMapper announcements;
  private final AdminAuditMapper audit;

  public AnnouncementService(AnnouncementMapper announcements, AdminAuditMapper audit) {
    this.announcements = announcements;
    this.audit = audit;
  }

  public List<Map<String, Object>> list(boolean admin, int page, int size) {
    if (page < 1 || size < 1 || size > 100 || page > 100000) throw bad("分页参数无效");
    List<Map<String, Object>> result = new ArrayList<>();
    for (AnnouncementEntity item :
        announcements.selectPageItems(admin, Instant.now(), size, (page - 1) * size))
      result.add(view(item, false));
    return result;
  }

  public Map<String, Object> detail(String id, boolean admin) {
    return view(required(id, admin), true);
  }

  // 详情再次执行可见性过滤，不能仅依赖列表过滤，以免直接访问未发布公告。
  private AnnouncementEntity required(String id, boolean admin) {
    AnnouncementEntity item =
        admin ? announcements.selectById(id) : announcements.selectPublishedById(id, Instant.now());
    if (item == null)
      throw new ApiException(HttpStatus.NOT_FOUND, "ANNOUNCEMENT_NOT_FOUND", "公告不存在或尚未发布");
    return item;
  }

  @Transactional
  public Map<String, Object> save(String actor, String id, Map<String, String> body) {
    String title = clean(body.get("title")),
        content = clean(body.get("body")),
        state = body.get("state");
    if (title.isEmpty() || title.length() > 120 || content.isEmpty() || content.length() > 20000)
      throw bad("标题需 1–120 字，正文需 1–20000 字");
    if (!Arrays.asList("draft", "published", "withdrawn").contains(state)) throw bad("公告状态无效");
    Instant publish = null;
    String input = clean(body.get("publishAt"));
    try {
      if (!input.isEmpty()) publish = Instant.parse(input);
    } catch (DateTimeException e) {
      throw bad("发布时间需为包含时区的 ISO 时间");
    }
    Instant moment = Instant.now();
    if ("published".equals(state) && publish == null) publish = moment;
    boolean create = id == null;
    AnnouncementEntity item = create ? new AnnouncementEntity() : required(id, true);
    if (create) {
      id = CryptoUtils.randomId();
      item.setId(id);
      item.setCreatedBy(actor);
      item.setCreatedAt(moment);
    }
    item.setTitle(title);
    item.setBody(content);
    item.setState(state);
    // MP 实体允许更新为 null，草稿清空发布时间时不会留下旧的定时发布值。
    item.setPublishAt(publish);
    item.setUpdatedBy(actor);
    item.setUpdatedAt(moment);
    if (create) announcements.insert(item);
    else announcements.updateById(item);
    audit.insertAudit(
        CryptoUtils.randomId(),
        actor,
        "announcement_save",
        "announcement",
        id,
        "succeeded",
        "{\"state\":\"" + state + "\"}",
        moment.plus(Duration.ofDays(180)));
    LOG.info("Announcement save completed in transaction: state={}, created={}", state, create);
    return detail(id, true);
  }

  private Map<String, Object> view(AnnouncementEntity item, boolean full) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", item.getId());
    out.put("title", item.getTitle());
    out.put("state", item.getState());
    out.put("publishAt", item.getPublishAt());
    out.put("updatedAt", item.getUpdatedAt());
    if (full) out.put("body", item.getBody());
    return out;
  }

  private String clean(String value) {
    return value == null ? "" : value.trim();
  }

  private ApiException bad(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANNOUNCEMENT", message);
  }
}
