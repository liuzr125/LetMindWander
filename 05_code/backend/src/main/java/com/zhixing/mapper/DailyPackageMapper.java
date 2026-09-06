package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.DailyPackageEntity;
import com.zhixing.entity.PackageRevisionEntity;
import com.zhixing.entity.TaskEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/** F04 的所有读取与更新 SQL 均集中在 Mapper，Service 只编排规则与事务。 */
@Mapper
public interface DailyPackageMapper extends BaseMapper<DailyPackageEntity> {
    @Select("SELECT * FROM daily_package WHERE owner_id=#{ownerId} AND business_date=#{businessDate}")
    DailyPackageEntity selectByOwnerAndDate(@Param("ownerId") String ownerId, @Param("businessDate") LocalDate businessDate);

    @Select("SELECT * FROM daily_package WHERE owner_id=#{ownerId} AND business_date=#{businessDate} FOR UPDATE")
    DailyPackageEntity selectByOwnerAndDateForUpdate(@Param("ownerId") String ownerId, @Param("businessDate") LocalDate businessDate);

    @Update("UPDATE daily_package SET final_done_count=#{doneCount}, current_count=#{currentCount}, updated_at=CURRENT_TIMESTAMP(3) WHERE id=#{packageId}")
    int updateMetrics(@Param("packageId") String packageId, @Param("doneCount") int doneCount, @Param("currentCount") int currentCount);

    @Insert("INSERT INTO package_revision (id, owner_id, package_id, version_no, reason, before_json, after_json) VALUES " +
            "(#{id}, #{ownerId}, #{packageId}, #{versionNo}, #{reason}, #{beforeJson}, #{afterJson})")
    int insertRevision(PackageRevisionEntity entity);

    @Insert("INSERT INTO task_event (id, owner_id, task_id, task_version, event_type, actor_type, from_status, to_status, occurred_at, reason) VALUES " +
            "(#{id}, #{ownerId}, #{taskId}, #{taskVersion}, #{eventType}, #{actorType}, #{fromStatus}, #{toStatus}, #{occurredAt}, #{reason})")
    int insertEvent(TaskEventEntity entity);
}
