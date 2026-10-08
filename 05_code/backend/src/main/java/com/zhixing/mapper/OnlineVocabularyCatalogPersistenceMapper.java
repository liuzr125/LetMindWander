package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence statements for OnlineVocabularyCatalog business operations. */
@Mapper
public interface OnlineVocabularyCatalogPersistenceMapper {
  int insertCatalogBook(
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
      @Param("p11") Object p11);

  int updateCatalogBook(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7,
      @Param("p8") Object p8,
      @Param("p9") Object p9);

  List<Map<String, Object>> listCatalogBooks();

  Integer countMatchingWords(@Param("p0") Object p0, @Param("p1") Object p1);

  List<Map<String, Object>> listMatchingWords(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3);

  Map<String, Object> lockCatalogBook(@Param("p0") Object p0);

  Integer countDatasetSnapshot(@Param("p0") Object p0);

  int insertDatasetSnapshot(
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
      @Param("p10") com.zhixing.mapper.type.BinaryHash p10,
      @Param("p11") Object p11);

  int insertSnapshotWord(
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
      @Param("p11") Object p11);

  int completeBookSync(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3);

  int insertSyncAudit(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6);

  List<Map<String, Object>> findCatalogBook(@Param("p0") Object p0);
}
