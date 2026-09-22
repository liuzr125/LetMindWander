package com.zhixing.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 管理端「AI 用量日志」：按 天 × 用户 汇总每日额度使用与调用明细。 */
@Mapper
public interface AdminAiUsageMapper {
    /** 额度行 + 用户（global/system 作用域的行因为没有对应 app_user 会被自然排除）。 */
    String QUOTA_FROM = "FROM ai_daily_quota q JOIN app_user u ON u.id=q.scope_key ";
    /** 调用明细聚合：同一天同一用户的成功/失败/进行中、token 与费用。 */
    String ATTEMPT_AGG = "LEFT JOIN (SELECT owner_id ownerId,quota_date quotaDate," +
            "SUM(CASE WHEN state='succeeded' THEN 1 ELSE 0 END) succeededCount," +
            "SUM(CASE WHEN state IN ('failed','timeout') THEN 1 ELSE 0 END) failedCount," +
            "SUM(CASE WHEN state IN ('reserved','sending') THEN 1 ELSE 0 END) runningCount," +
            "SUM(COALESCE(input_tokens,0)) inputTokens,SUM(COALESCE(output_tokens,0)) outputTokens," +
            "SUM(COALESCE(settled_amount,0)) costCny FROM ai_attempt WHERE owner_id IS NOT NULL GROUP BY owner_id,quota_date) a " +
            "ON a.ownerId=q.scope_key AND a.quotaDate=q.quota_date ";
    String QUOTA_WHERE = "WHERE q.quota_date BETWEEN #{dateFrom} AND #{dateTo} " +
            "<if test=\"keyword != null and keyword != ''\">AND (u.nickname LIKE CONCAT('%',#{keyword},'%') OR u.mobile LIKE CONCAT('%',#{keyword},'%') OR u.short_id LIKE CONCAT('%',#{keyword},'%') OR u.id LIKE CONCAT('%',#{keyword},'%')) </if>";

    @Select({"<script>",
            "SELECT q.quota_date quotaDate,u.id userId,u.nickname,u.short_id shortId,u.mobile,",
            "q.used_count usedCount,q.limit_count limitCount,",
            "COALESCE(a.succeededCount,0) succeededCount,COALESCE(a.failedCount,0) failedCount,COALESCE(a.runningCount,0) runningCount,",
            "COALESCE(a.inputTokens,0) inputTokens,COALESCE(a.outputTokens,0) outputTokens,COALESCE(a.costCny,0) costCny ",
            QUOTA_FROM, ATTEMPT_AGG, QUOTA_WHERE,
            "ORDER BY q.quota_date DESC,q.used_count DESC,u.nickname LIMIT #{offset},#{limit}","</script>"})
    List<Map<String,Object>> selectUsageLogs(@Param("dateFrom")String dateFrom,@Param("dateTo")String dateTo,@Param("keyword")String keyword,@Param("offset")int offset,@Param("limit")int limit);

    @Select({"<script>","SELECT COUNT(*) ",QUOTA_FROM,QUOTA_WHERE,"</script>"})
    int countUsageLogs(@Param("dateFrom")String dateFrom,@Param("dateTo")String dateTo,@Param("keyword")String keyword);

    @Select({"<script>",
            "SELECT COUNT(DISTINCT q.scope_key) activeUsers,COALESCE(SUM(q.used_count),0) usedTotal,COALESCE(SUM(q.limit_count),0) limitTotal ",
            QUOTA_FROM,QUOTA_WHERE,"</script>"})
    Map<String,Object> summarizeQuota(@Param("dateFrom")String dateFrom,@Param("dateTo")String dateTo,@Param("keyword")String keyword);

    @Select({"<script>",
            "SELECT COALESCE(SUM(a.settled_amount),0) costTotal,COALESCE(SUM(a.input_tokens),0) inputTokens,COALESCE(SUM(a.output_tokens),0) outputTokens,",
            "COALESCE(SUM(CASE WHEN a.state='succeeded' THEN 1 ELSE 0 END),0) succeededCount,",
            "COALESCE(SUM(CASE WHEN a.state IN ('failed','timeout') THEN 1 ELSE 0 END),0) failedCount ",
            "FROM ai_attempt a JOIN app_user u ON u.id=a.owner_id WHERE a.quota_date BETWEEN #{dateFrom} AND #{dateTo} ",
            "<if test=\"keyword != null and keyword != ''\">AND (u.nickname LIKE CONCAT('%',#{keyword},'%') OR u.mobile LIKE CONCAT('%',#{keyword},'%') OR u.short_id LIKE CONCAT('%',#{keyword},'%') OR u.id LIKE CONCAT('%',#{keyword},'%')) </if>",
            "</script>"})
    Map<String,Object> summarizeAttempts(@Param("dateFrom")String dateFrom,@Param("dateTo")String dateTo,@Param("keyword")String keyword);
}
