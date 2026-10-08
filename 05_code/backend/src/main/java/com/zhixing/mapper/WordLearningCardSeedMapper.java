package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordLearningCardSeedMapper {
  List<String> selectSeedVersions(
      @Param("word") String word,
      @Param("meaningContains") String meaningContains,
      @Param("senseMeaningContains") String senseMeaningContains);

  int countCardKey(@Param("versionId") String versionId, @Param("cardKey") String cardKey);

  int insertSeedCard(
      @Param("id") String id,
      @Param("versionId") String versionId,
      @Param("cardKey") String cardKey,
      @Param("cardType") String cardType,
      @Param("senseLabel") String senseLabel,
      @Param("title") String title,
      @Param("relatedTerm") String relatedTerm,
      @Param("body") String body,
      @Param("exampleText") String exampleText,
      @Param("exampleTranslation") String exampleTranslation,
      @Param("recallPrompt") String recallPrompt,
      @Param("sourceKind") String sourceKind,
      @Param("sourceTitle") String sourceTitle,
      @Param("sourceUrl") String sourceUrl,
      @Param("sourceVerified") Integer sourceVerified,
      @Param("rightsNote") String rightsNote,
      @Param("sortNo") Integer sortNo);
}
