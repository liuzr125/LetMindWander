package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.DailyTaskEntity;
import java.time.LocalDate;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** F04 任务数据访问入口，业务层不得直接编写任务查询 SQL。 */
@Mapper
public interface DailyTaskMapper extends BaseMapper<DailyTaskEntity> {
  @Select(
      "SELECT t.content_id,cv.meaning,CASE WHEN lc.state='published' AND live.review_status='approved' THEN 1 ELSE 0 END AS available "
          + "FROM daily_task t JOIN daily_package p ON p.id=t.package_id AND p.owner_id=#{owner} "
          + "LEFT JOIN learning_content lc ON lc.id=t.content_id "
          + "LEFT JOIN content_version cv ON cv.id=t.content_version_id AND cv.content_id=t.content_id "
          + "LEFT JOIN content_version live ON live.id=lc.published_version_id AND live.content_id=lc.id "
          + "WHERE t.package_id=#{id} AND t.task_type='word' AND t.status<>'CANCELLED'")
  List<java.util.Map<String, Object>> selectWordInfo(
      @Param("id") String id, @Param("owner") String owner);

  @Select(
      "SELECT b.book_name FROM daily_package p LEFT JOIN vocabulary_book b ON b.id=p.words_book_id WHERE p.id=#{id} AND p.owner_id=#{owner}")
  String selectWordsBookName(@Param("id") String id, @Param("owner") String owner);

  @Select("SELECT * FROM daily_task WHERE package_id = #{packageId} ORDER BY sort_no, id")
  List<DailyTaskEntity> selectByPackage(@Param("packageId") String packageId);

  @Select("SELECT * FROM daily_task WHERE id = #{taskId} AND owner_id = #{ownerId} FOR UPDATE")
  DailyTaskEntity selectOwnedForUpdate(
      @Param("taskId") String taskId, @Param("ownerId") String ownerId);

  @Select(
      "SELECT t.* FROM daily_task t JOIN daily_package p ON p.id=t.package_id "
          + "WHERE t.owner_id=#{ownerId} AND p.business_date=#{date} AND t.task_type='journal' "
          + "AND t.status<>'CANCELLED' ORDER BY t.sort_no LIMIT 1 FOR UPDATE")
  DailyTaskEntity selectJournalForUpdate(
      @Param("ownerId") String ownerId, @Param("date") LocalDate date);

  @Select(
      "SELECT * FROM daily_task WHERE id=#{taskId} AND owner_id=#{ownerId} AND knowledge_id=#{knowledgeId} "
          + "AND task_type='review' FOR UPDATE")
  DailyTaskEntity selectReviewForUpdate(
      @Param("ownerId") String ownerId,
      @Param("taskId") String taskId,
      @Param("knowledgeId") String knowledgeId);

  @Update(
      "UPDATE daily_task SET status=#{status}, started_at=#{startedAt}, completed_at=#{completedAt}, version_no=#{nextVersion}, updated_at=CURRENT_TIMESTAMP(3) "
          + "WHERE id=#{id} AND owner_id=#{ownerId} AND version_no=#{expectedVersion}")
  int transition(
      @Param("id") String id,
      @Param("ownerId") String ownerId,
      @Param("expectedVersion") Integer expectedVersion,
      @Param("nextVersion") Integer nextVersion,
      @Param("status") String status,
      @Param("startedAt") java.time.Instant startedAt,
      @Param("completedAt") java.time.Instant completedAt);
}
