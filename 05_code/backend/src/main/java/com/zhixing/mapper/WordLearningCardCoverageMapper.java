package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordLearningCardCoverageMapper {
  List<Map<String, Object>> selectCoverageTargets();

  List<Map<String, Object>> selectTargetScopes();

  List<Map<String, Object>> selectTargetSensesAndExamples();

  List<Map<String, Object>> selectExistingCardKeys();

  int insertBaselineCards(@Param("rows") List<Map<String, Object>> rows);

  List<Map<String, Object>> selectPublishedCardCounts();

  int countImportedLexicalSnapshots();

  int upsertCoverage(@Param("rows") List<Map<String, Object>> rows);

  int upsertTypeCoverage(@Param("rows") List<Map<String, Object>> rows);
}
