package com.zhixing.mapper;

import com.zhixing.model.ActionView;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ActionMapper {
    @Select("SELECT id,title,note,scheduled_date,estimated_minutes,state,version_no FROM weekly_action WHERE id=#{id} AND owner_id=#{ownerId}")
    ActionView selectOwned(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT COUNT(*) FROM daily_task t JOIN daily_package p ON p.id=t.package_id WHERE t.owner_id=#{ownerId} " +
            "AND t.action_id=#{id} AND t.status='TODO' AND ((SELECT COALESCE(SUM(x.estimated_seconds),0) FROM daily_task x " +
            "WHERE x.package_id=t.package_id AND x.status<>'CANCELLED' AND x.id<>t.id)+#{minutes}*60)>p.budget_seconds")
    int countOverBudgetPackages(@Param("ownerId") String ownerId,@Param("id") String id,@Param("minutes") Integer minutes);

    @Update("UPDATE weekly_action SET title=#{title},note=#{note},estimated_minutes=#{minutes},version_no=version_no+1 " +
            "WHERE id=#{id} AND owner_id=#{ownerId} AND state='confirmed' AND version_no=#{expectedVersion}")
    int update(@Param("ownerId") String ownerId,@Param("id") String id,@Param("title") String title,
               @Param("note") String note,@Param("minutes") Integer minutes,@Param("expectedVersion") Integer expectedVersion);

    @Update("UPDATE daily_task SET title_snapshot=#{title},estimated_seconds=#{minutes}*60,version_no=version_no+1 " +
            "WHERE owner_id=#{ownerId} AND action_id=#{id} AND status='TODO'")
    int updatePendingTaskSnapshots(@Param("ownerId") String ownerId,@Param("id") String id,
                                   @Param("title") String title,@Param("minutes") Integer minutes);
}
