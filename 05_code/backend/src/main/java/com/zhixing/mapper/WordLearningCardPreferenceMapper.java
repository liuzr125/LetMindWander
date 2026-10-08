package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordLearningCardPreferenceMapper {
  int updatePreference(
      @Param("enabled") Integer enabled,
      @Param("ownerId") String ownerId,
      @Param("cardType") String cardType);

  int insertPreference(
      @Param("ownerId") String ownerId,
      @Param("cardType") String cardType,
      @Param("enabled") Integer enabled);

  List<Map<String, Object>> selectPreferences(@Param("ownerId") String ownerId);
}
