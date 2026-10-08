package com.zhixing.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Round and daily study writes, owned by BookStudyService transactions. */
public interface BookStudyWriteMapper {
  List<String> selectContentBooks(@Param("content") String content);

  List<String> selectReviewContent(
      @Param("schedule") String schedule, @Param("owner") String owner);

  int insertResetLog(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("book") String book,
      @Param("round") String round,
      @Param("resetCount") int resetCount,
      @Param("pausedReviewCount") int pausedReviewCount,
      @Param("at") java.sql.Timestamp at);

  List<String> selectLearningStatus(@Param("owner") String owner, @Param("content") String content);

  int closeOtherRounds(
      @Param("at") java.sql.Timestamp at, @Param("owner") String owner, @Param("book") String book);

  int insertNextRound(
      @Param("id") String id,
      @Param("owner") String owner,
      @Param("book") String book,
      @Param("at") java.sql.Timestamp at,
      @Param("learnedOwner") String learnedOwner,
      @Param("learnedBook") String learnedBook,
      @Param("roundOwner") String roundOwner,
      @Param("roundBook") String roundBook);

  List<String> selectSameDayRound(
      @Param("owner") String owner,
      @Param("book") String book,
      @Param("dayStart") java.sql.Timestamp dayStart);

  List<String> selectActiveBook(@Param("owner") String owner);

  int closeRound(
      @Param("at") java.sql.Timestamp at,
      @Param("selectedAt") java.sql.Timestamp selectedAt,
      @Param("round") String round);

  List<String> selectOpenRound(@Param("owner") String owner, @Param("book") String book);

  int insertDaily(
      @Param("id") String id,
      @Param("round") String round,
      @Param("date") java.sql.Date date,
      @Param("study") int study,
      @Param("reviewed") int reviewed,
      @Param("sourceRound") String sourceRound);

  int incrementSummary(
      @Param("firstAt") java.sql.Timestamp firstAt,
      @Param("lastAt") java.sql.Timestamp lastAt,
      @Param("study") int study,
      @Param("reviewed") int reviewed,
      @Param("day") int day,
      @Param("round") String round);

  int incrementDaily(
      @Param("round") String round,
      @Param("date") java.sql.Date date,
      @Param("review") boolean review);
}
