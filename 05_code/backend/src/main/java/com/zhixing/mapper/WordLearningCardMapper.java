package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordLearningCardMapper {
  List<String> selectPublishedVersions(@Param("contentId") String contentId);

  List<Map<String, Object>> selectPublishedCardsByType(
      @Param("versionId") String versionId, @Param("cardType") String cardType);
}
