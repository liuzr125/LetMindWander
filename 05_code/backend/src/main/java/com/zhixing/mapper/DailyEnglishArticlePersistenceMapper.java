package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence statements for DailyEnglishArticle business operations. */
@Mapper
public interface DailyEnglishArticlePersistenceMapper {
  int insertGenerationRun(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3);

  List<Integer> listGeneratedSlots(@Param("p0") Object p0, @Param("p1") Object p1);

  List<String> listGeneratedTitles(@Param("p0") Object p0, @Param("p1") Object p1);

  int finishGenerationRun(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7);

  Integer countPublishedByDifficulty(@Param("p0") Object p0);

  List<Map<String, Object>> listRecentRuns(@Param("p0") Object p0);

  List<Map<String, Object>> findRunDetail(@Param("p0") Object p0);

  List<Map<String, Object>> listRunModelUsage(@Param("p0") Object p0);

  List<Map<String, Object>> listEligibleBooks();

  List<Map<String, Object>> listRecentTopics();

  int countBookWords(@Param("p0") Object p0);

  List<Map<String, Object>> listTargetWords(
      @Param("p0") Object p0, @Param("p1") Object p1, @Param("p2") Object p2);

  int insertPublishedArticle(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") com.zhixing.mapper.type.BinaryHash p2,
      @Param("p3") com.zhixing.mapper.type.BinaryHash p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6);

  int insertApprovedArticleVersion(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7,
      @Param("p8") Object p8,
      @Param("p9") Object p9,
      @Param("p10") Object p10,
      @Param("p11") Object p11,
      @Param("p12") com.zhixing.mapper.type.BinaryHash p12,
      @Param("p13") Object p13,
      @Param("p14") Object p14);

  int insertBookArticle(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6);

  int insertArticleCategory(@Param("p0") Object p0, @Param("p1") Object p1, @Param("p2") Object p2);

  List<Map<String, Object>> findRunByTrigger(@Param("p0") Object p0);

  Map<String, Object> findRunById(@Param("p0") Object p0);
}
