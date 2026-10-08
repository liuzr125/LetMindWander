package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WordLearningCardEnrichmentMapper {
  int retirePreviousCards(
      @Param("generatorV1") String generatorV1,
      @Param("generatorV2") String generatorV2,
      @Param("generatorV3") String generatorV3);

  List<Map<String, Object>> selectEnrichmentTargets();

  List<Map<String, Object>> selectEnrichmentSenses();

  List<Map<String, Object>> selectLexicalEvidence(@Param("datasetCode") String datasetCode);

  List<Map<String, Object>> selectExistingEnrichmentCards();

  int insertEnrichmentCards(@Param("rows") List<Map<String, Object>> rows);
}
