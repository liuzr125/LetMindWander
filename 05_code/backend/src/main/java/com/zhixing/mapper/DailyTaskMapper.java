package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.DailyTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

/** F04 任务数据访问入口，业务层不得直接编写任务查询 SQL。 */
@Mapper
public interface DailyTaskMapper extends BaseMapper<DailyTaskEntity> {
    @Select("SELECT * FROM daily_task WHERE package_id = #{packageId} ORDER BY sort_no, id")
    List<DailyTaskEntity> selectByPackage(@Param("packageId") String packageId);

    @Select("SELECT * FROM daily_task WHERE id = #{taskId} AND owner_id = #{ownerId} FOR UPDATE")
    DailyTaskEntity selectOwnedForUpdate(@Param("taskId") String taskId, @Param("ownerId") String ownerId);

    @Update("UPDATE daily_task SET status=#{status}, started_at=#{startedAt}, completed_at=#{completedAt}, version_no=#{nextVersion}, updated_at=CURRENT_TIMESTAMP(3) " +
            "WHERE id=#{id} AND owner_id=#{ownerId} AND version_no=#{expectedVersion}")
    int transition(@Param("id") String id, @Param("ownerId") String ownerId, @Param("expectedVersion") Integer expectedVersion,
                   @Param("nextVersion") Integer nextVersion, @Param("status") String status,
                   @Param("startedAt") java.time.Instant startedAt, @Param("completedAt") java.time.Instant completedAt);
}
