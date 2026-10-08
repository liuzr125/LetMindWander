package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AnnouncementEntity;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AnnouncementMapper extends BaseMapper<AnnouncementEntity> {
  List<AnnouncementEntity> selectPageItems(
      @Param("admin") boolean admin,
      @Param("now") Instant now,
      @Param("limit") int limit,
      @Param("offset") int offset);

  AnnouncementEntity selectPublishedById(@Param("id") String id, @Param("now") Instant now);
}
