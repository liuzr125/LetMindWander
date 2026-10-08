package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence statements for VocabularyImport business operations. */
@Mapper
public interface VocabularyImportPersistenceMapper {
  List<Map<String, Object>> listEnabledDatasets();

  int insertUploadedDataset(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") com.zhixing.mapper.type.BinaryHash p7,
      @Param("p8") Object p8,
      @Param("p9") Object p9);

  Map<String, Object> findDatasetForDeletion(@Param("p0") Object p0);

  Map<String, Object> lockDatasetForDeletion(@Param("p0") Object p0);

  int countDatasetBatches(@Param("p0") Object p0);

  int countSnapshotWords(@Param("p0") Object p0);

  int deleteSnapshotWords(@Param("p0") Object p0);

  int clearBooksSnapshot(@Param("p0") Object p0);

  int deleteDataset(@Param("p0") Object p0);

  Map<String, Object> findBatchForDeletion(@Param("p0") Object p0);

  int countPublishedItems(@Param("p0") Object p0);

  int countRunningAudioTasks(@Param("p0") Object p0);

  int deleteBatchItems(@Param("p0") Object p0);

  int deleteBatchAudioTasks(@Param("p0") Object p0);

  int deleteBatch(@Param("p0") Object p0);

  Map<String, Object> findDataset(@Param("p0") Object p0);

  Map<String, Object> findTargetBook(@Param("p0") Object p0);

  int insertBatch(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4);

  List<Map<String, Object>> listRecentBatches();

  Map<String, Object> findBatchDetail(@Param("p0") Object p0);

  List<Map<String, Object>> listBatchItems(@Param("p0") Object p0);

  int countSucceededAudioTasks(@Param("p0") Object p0);

  int countFailedAudioTasks(@Param("p0") Object p0);

  Map<String, Object> lockBatchForParsing(@Param("p0") Object p0);

  int countPendingReviews(@Param("p0") Object p0);

  int updateBatchReviewState(
      @Param("p0") Object p0, @Param("p1") Object p1, @Param("p2") Object p2);

  int beginBatchParsing(@Param("p0") Object p0);

  List<String> listSnapshotPayloads(@Param("p0") Object p0);

  List<String> findWordIdsByHash(@Param("p0") com.zhixing.mapper.type.BinaryHash p0);

  int insertBatchItem(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") com.zhixing.mapper.type.BinaryHash p6,
      @Param("p7") Object p7,
      @Param("p8") Object p8,
      @Param("p9") Object p9,
      @Param("p10") Object p10,
      @Param("p11") Object p11,
      @Param("p12") Object p12);

  int finishBatchParsing(
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

  Map<String, Object> findBatchItem(@Param("p0") Object p0, @Param("p1") Object p1);

  int approveBatchItem(@Param("p0") Object p0);

  int rejectBatchItem(@Param("p0") Object p0);

  int countReviewDecisions(@Param("p0") Object p0);

  int markBatchReadyForReview(@Param("p0") Object p0);

  int pauseBatch(@Param("p0") Object p0);

  Map<String, Object> lockBatchWithSource(@Param("p0") Object p0);

  List<Map<String, Object>> listAudioTasksToRun(@Param("p0") Object p0);

  int startAudioTask(@Param("p0") Object p0);

  int completeAudioTask(@Param("p0") Object p0, @Param("p1") Object p1);

  int failAudioTask(@Param("p0") Object p0, @Param("p1") Object p1, @Param("p2") Object p2);

  int countIncompleteAudioTasks(@Param("p0") Object p0);

  Map<String, Object> lockBatchWithLicense(@Param("p0") Object p0);

  int countUnusableAudioTasks(@Param("p0") Object p0);

  int markBatchPublished(@Param("p0") Object p0);

  int insertImportedSource(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4);

  Map<String, Object> findBookLevel(@Param("p0") Object p0);

  int findLastBookSort(@Param("p0") Object p0);

  List<Map<String, Object>> listPublishableItems(@Param("p0") Object p0);

  Map<String, Object> findWordByHash(@Param("p0") com.zhixing.mapper.type.BinaryHash p0);

  int insertPublishedWord(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") com.zhixing.mapper.type.BinaryHash p2,
      @Param("p3") com.zhixing.mapper.type.BinaryHash p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6);

  int insertApprovedVersion(
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
      @Param("p11") com.zhixing.mapper.type.BinaryHash p11,
      @Param("p12") Object p12);

  int insertWordSense(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4);

  int insertWordExample(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4);

  List<Map<String, Object>> listCompletedItemAudioTasks(@Param("p0") Object p0);

  int insertPronunciation(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7);

  int attachItemContent(@Param("p0") Object p0, @Param("p1") Object p1);

  int insertBookWord(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4);

  int refreshBookWordCount(@Param("p0") Object p0, @Param("p1") Object p1);

  int insertAudioTask(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7,
      @Param("p8") Object p8);

  int markBatchFailed(@Param("p0") Object p0, @Param("p1") Object p1, @Param("p2") Object p2);

  int insertAudit(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3,
      @Param("p4") Object p4,
      @Param("p5") Object p5,
      @Param("p6") Object p6,
      @Param("p7") Object p7);
}
