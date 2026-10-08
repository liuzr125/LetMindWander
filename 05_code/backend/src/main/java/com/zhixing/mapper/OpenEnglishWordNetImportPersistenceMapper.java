package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence statements for OpenEnglishWordNetImport business operations. */
@Mapper
public interface OpenEnglishWordNetImportPersistenceMapper {
  List<String> listActiveBookHeadwords();

  int upsertDatasetSnapshot(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7);

  int retireDatasetEvidence(@Param("p0") Object p0);

  int upsertEvidenceBatch(@Param("rows") List<Map<String, Object>> rows);

  int completeDatasetImport(@Param("p0") Object p0, @Param("p1") Object p1);
}
