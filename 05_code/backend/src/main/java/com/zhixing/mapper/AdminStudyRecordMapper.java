package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminStudyRecordMapper {
  int countFilteredRecords(@Param("filter") Map<String, Object> filter);

  int countFilteredOwners(@Param("filter") Map<String, Object> filter);

  int countFilteredBooks(@Param("filter") Map<String, Object> filter);

  int countFilteredOngoingRecords(@Param("filter") Map<String, Object> filter);

  int countStudiedSince(@Param("since") java.sql.Timestamp since);

  List<Map<String, Object>> selectRecordPage(
      @Param("filter") Map<String, Object> filter,
      @Param("offset") int offset,
      @Param("limit") int limit);

  List<Map<String, Object>> selectRecordById(@Param("id") String id);

  int countRecordWords(@Param("filter") Map<String, Object> filter);

  List<Map<String, Object>> selectRecordWords(
      @Param("filter") Map<String, Object> filter,
      @Param("offset") int offset,
      @Param("limit") int limit);

  List<Map<String, Object>> selectActiveBooks();

  int countRoundNewWords(@Param("filter") Map<String, Object> filter);

  List<Map<String, Object>> selectRoundResets(@Param("id") String id);

  List<Map<String, Object>> selectDailyNewWords(@Param("filter") Map<String, Object> filter);

  List<Map<String, Object>> selectRoundDailyActions(@Param("id") String id);
}
