package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.LearningPlanEntity;
import com.zhixing.entity.LearningPlanTopicEntity;
import com.zhixing.entity.LearningTopicEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/** F03 数据访问集中在 Mapper，Service 不包含 SQL 或数据库查询。 */
@Mapper
public interface LearningPlanMapper extends BaseMapper<LearningPlanEntity> {
    /** 最新保存版本必须按单调递增的 version_no 判定；effective_date 可因“调整今日”倒退。 */
    @Select("SELECT * FROM learning_plan WHERE owner_id = #{ownerId} ORDER BY version_no DESC LIMIT 1")
    LearningPlanEntity selectLatest(@Param("ownerId") String ownerId);

    @Select("SELECT * FROM learning_plan WHERE owner_id = #{ownerId} ORDER BY version_no DESC")
    List<LearningPlanEntity> selectHistory(@Param("ownerId") String ownerId);

    @Select("SELECT * FROM learning_plan WHERE owner_id = #{ownerId} ORDER BY version_no DESC LIMIT 1 FOR UPDATE")
    LearningPlanEntity selectLatestForUpdate(@Param("ownerId") String ownerId);

    @Select("SELECT * FROM learning_plan WHERE owner_id = #{ownerId} AND effective_date <= #{date} " +
            "ORDER BY effective_date DESC, version_no DESC LIMIT 1")
    LearningPlanEntity selectEffectiveOn(@Param("ownerId") String ownerId, @Param("date") LocalDate date);

    @Select("SELECT COALESCE(MAX(version_no), 0) FROM learning_plan WHERE owner_id = #{ownerId}")
    Integer selectMaxVersion(@Param("ownerId") String ownerId);

    @Select("SELECT t.* FROM learning_topic t INNER JOIN learning_plan_topic pt ON pt.topic_id = t.id " +
            "WHERE pt.plan_id = #{planId} AND t.state = 'active' ORDER BY t.name")
    List<LearningTopicEntity> selectPlanTopics(@Param("planId") String planId);

    @Select("SELECT * FROM learning_topic WHERE state = 'active' AND (scope_key = 'system' OR owner_id = #{ownerId}) ORDER BY name")
    List<LearningTopicEntity> selectAvailableTopics(@Param("ownerId") String ownerId);

    @Select("SELECT * FROM learning_topic WHERE scope_key = 'system' AND normalized_name = 'ai' AND state = 'active' LIMIT 1")
    LearningTopicEntity selectLegacyAiTopic();

    @Select("SELECT * FROM learning_topic WHERE scope_key = #{scopeKey} AND normalized_name = #{normalizedName}")
    LearningTopicEntity selectTopicByScopeAndName(@Param("scopeKey") String scopeKey, @Param("normalizedName") String normalizedName);

    @Insert("INSERT INTO learning_topic (id, scope_key, owner_id, name, normalized_name, state) " +
            "VALUES (#{id}, #{scopeKey}, #{ownerId}, #{name}, #{normalizedName}, #{state})")
    int insertTopic(LearningTopicEntity topic);

    @Insert("INSERT INTO learning_plan_topic (id, plan_id, topic_id) VALUES (#{id}, #{planId}, #{topicId})")
    int insertPlanTopic(LearningPlanTopicEntity relation);

    @Delete("DELETE FROM learning_plan_topic WHERE plan_id = #{planId}")
    int deletePlanTopics(@Param("planId") String planId);

    @Update("UPDATE app_user SET current_plan_id = #{planId}, updated_at = CURRENT_TIMESTAMP(3) WHERE id = #{ownerId}")
    int updateCurrentPlan(@Param("ownerId") String ownerId, @Param("planId") String planId);
}
