package com.zhixing.mapper;

import com.zhixing.model.CollectionRunView;
import com.zhixing.model.CollectionScheduleView;
import org.apache.ibatis.annotations.*;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ContentCollectionMapper {
    @Select("SELECT id,name,cron_expression,timezone,state,per_run_limit,last_run_at,next_run_at FROM content_collection_schedule WHERE id='00000000000000000000000000000090'")
    CollectionScheduleView selectSchedule();

    @Select("SELECT r.id,s.name AS task_name,r.trigger_key,r.state,r.error_code,r.error_message,r.fetched_count,r.inserted_count,r.skipped_count,r.started_at,r.finished_at,r.created_at " +
            "FROM content_collection_run r JOIN content_collection_schedule s ON s.id=r.schedule_id WHERE r.schedule_id=#{scheduleId} ORDER BY r.created_at DESC,r.id DESC LIMIT #{limit}")
    List<CollectionRunView> selectRuns(@Param("scheduleId") String scheduleId, @Param("limit") int limit);

    @Select("SELECT r.id,s.name AS task_name,r.trigger_key,r.state,r.error_code,r.error_message,r.fetched_count,r.inserted_count,r.skipped_count,r.started_at,r.finished_at,r.created_at " +
            "FROM content_collection_run r JOIN content_collection_schedule s ON s.id=r.schedule_id WHERE r.id=#{id} AND r.schedule_id=#{scheduleId}")
    CollectionRunView selectRun(@Param("id") String id, @Param("scheduleId") String scheduleId);

    @Insert("INSERT INTO content_collection_run (id,schedule_id,trigger_key,state,started_at) VALUES (#{id},#{scheduleId},#{triggerKey},'running',#{startedAt})")
    int insertRun(@Param("id") String id, @Param("scheduleId") String scheduleId, @Param("triggerKey") String triggerKey, @Param("startedAt") Instant startedAt);

    @Update("UPDATE content_collection_run SET state=#{state},fetched_count=#{fetched},inserted_count=#{inserted},skipped_count=#{skipped},error_code=#{errorCode},error_message=#{errorMessage},finished_at=#{finishedAt} WHERE id=#{id}")
    int finishRun(@Param("id") String id, @Param("state") String state, @Param("fetched") int fetched, @Param("inserted") int inserted,
                  @Param("skipped") int skipped, @Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage, @Param("finishedAt") Instant finishedAt);

    @Update("UPDATE content_collection_schedule SET last_run_at=#{lastRunAt},next_run_at=#{nextRunAt} WHERE id=#{id}")
    int updateScheduleTimes(@Param("id") String id, @Param("lastRunAt") Instant lastRunAt, @Param("nextRunAt") Instant nextRunAt);

    @Select("SELECT COUNT(*) FROM learning_content WHERE dedup_hash=UNHEX(#{hash})")
    int countContentByDedup(@Param("hash") String hash);

    @Insert("INSERT INTO learning_content (id,content_type,source_id,dedup_hash,origin_url_hash,state,current_version_id,row_version) " +
            "VALUES (#{id},'tech',#{sourceId},UNHEX(#{dedupHash}),UNHEX(#{urlHash}),'pending',#{versionId},1)")
    int insertPendingContent(@Param("id") String id, @Param("sourceId") String sourceId, @Param("dedupHash") String dedupHash,
                             @Param("urlHash") String urlHash, @Param("versionId") String versionId);

    @Insert("INSERT INTO content_version (id,content_id,version_no,title,summary,body,difficulty,tags_json,estimated_seconds,origin_url,origin_author,origin_published_at,license_snapshot,body_hash,review_status,created_by) " +
            "VALUES (#{id},#{contentId},1,#{title},#{summary},#{body},'intro',#{tagsJson},360,#{url},#{author},#{publishedAt},#{license},UNHEX(#{bodyHash}),'pending',#{adminId})")
    int insertPendingVersion(@Param("id") String id, @Param("contentId") String contentId, @Param("title") String title,
                             @Param("summary") String summary, @Param("body") String body, @Param("tagsJson") String tagsJson,
                             @Param("url") String url, @Param("author") String author, @Param("publishedAt") Instant publishedAt,
                             @Param("license") String license, @Param("bodyHash") String bodyHash, @Param("adminId") String adminId);

    @Insert("INSERT INTO learning_topic (id,scope_key,owner_id,name,normalized_name,state) VALUES (#{id},'system',NULL,#{name},#{normalizedName},'active') " +
            "ON DUPLICATE KEY UPDATE name=VALUES(name),state='active'")
    int ensureTopic(@Param("id") String id, @Param("name") String name, @Param("normalizedName") String normalizedName);

    @Insert("INSERT INTO content_topic (id,content_version_id,topic_id) SELECT #{id},#{versionId},id FROM learning_topic " +
            "WHERE scope_key='system' AND normalized_name=#{normalizedName} ON DUPLICATE KEY UPDATE topic_id=VALUES(topic_id)")
    int insertContentTopic(@Param("id") String id, @Param("versionId") String versionId, @Param("normalizedName") String normalizedName);
}
