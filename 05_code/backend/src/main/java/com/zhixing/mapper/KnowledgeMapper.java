package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.KnowledgeItemEntity;
import com.zhixing.model.KnowledgeDetailView;
import com.zhixing.model.KnowledgeFriendView;
import com.zhixing.model.KnowledgeListItemView;
import org.apache.ibatis.annotations.*;
import java.time.LocalDate;
import java.util.List;

/** Knowledge-library persistence. SQL stays in the Mapper layer; services only enforce business rules. */
@Mapper
public interface KnowledgeMapper extends BaseMapper<KnowledgeItemEntity> {
    @Select({"<script>",
            "SELECT ki.id,ki.item_type,CASE WHEN ki.title='' THEN '未命名草稿' ELSE ki.title END AS title,",
            "CASE WHEN LENGTH(ki.body)&gt;120 THEN CONCAT(SUBSTRING(ki.body,1,120),'...') ELSE ki.body END AS summary,",
            "ki.learning_status,ki.verification_status,ki.visibility,ki.state,ki.source_content_id,ki.source_content_version_id,",
            "ki.version_no,ki.updated_at,CASE WHEN rs.state='active' THEN TRUE ELSE FALSE END AS in_review ",
            "FROM knowledge_item ki LEFT JOIN review_schedule rs ON rs.owner_id=ki.owner_id AND rs.knowledge_id=ki.id ",
            "WHERE ki.owner_id=#{ownerId} AND ki.state&lt;&gt;'deleted' ",
            "<if test=\"type != null and type != '' and type != 'all'\">",
            "<choose><when test=\"type == 'note'\">AND ki.item_type IN ('note','ai_note') </when>",
            "<otherwise>AND ki.item_type=#{type} </otherwise></choose></if>",
            "<if test=\"learningStatus != null and learningStatus != ''\">AND ki.learning_status=#{learningStatus} </if>",
            "<if test=\"verificationStatus != null and verificationStatus != ''\">AND ki.verification_status=#{verificationStatus} </if>",
            "<if test=\"query != null and query != ''\">AND (LOWER(ki.title) LIKE CONCAT('%',LOWER(#{query}),'%') ",
            "OR LOWER(ki.search_text) LIKE CONCAT('%',LOWER(#{query}),'%') ",
            "OR EXISTS (SELECT 1 FROM knowledge_tag kt WHERE kt.knowledge_id=ki.id AND LOWER(kt.tag_name) LIKE CONCAT('%',LOWER(#{query}),'%'))) </if>",
            "ORDER BY ki.updated_at DESC,ki.id DESC LIMIT #{limit}",
            "</script>"})
    List<KnowledgeListItemView> selectPage(@Param("ownerId") String ownerId,@Param("type") String type,
                                            @Param("query") String query,@Param("learningStatus") String learningStatus,
                                            @Param("verificationStatus") String verificationStatus,@Param("limit") int limit);

    @Select("SELECT ki.id,ki.item_type,ki.title,ki.body,ki.learning_status,ki.verification_status,ki.last_verified_date," +
            "ki.visibility,ki.state,ki.source_content_id,ki.source_content_version_id,ki.note_parent_id,ki.version_no," +
            "ki.created_at,ki.updated_at,CASE WHEN rs.state='active' THEN TRUE ELSE FALSE END AS in_review," +
            "COALESCE(parent.title,cv.title) AS source_title," +
            "COALESCE(SUBSTRING(parent.body,1,300),cv.summary) AS source_summary " +
            "FROM knowledge_item ki LEFT JOIN review_schedule rs ON rs.owner_id=ki.owner_id AND rs.knowledge_id=ki.id " +
            "LEFT JOIN knowledge_item parent ON parent.id=ki.note_parent_id AND parent.owner_id=ki.owner_id AND parent.state<>'deleted' " +
            "LEFT JOIN learning_content lc ON lc.id=ki.source_content_id AND lc.state='published' " +
            "LEFT JOIN content_version cv ON cv.id=ki.source_content_version_id AND cv.content_id=lc.id " +
            "WHERE ki.owner_id=#{ownerId} AND ki.id=#{id} AND ki.state<>'deleted'")
    KnowledgeDetailView selectDetail(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT * FROM knowledge_item WHERE owner_id=#{ownerId} AND id=#{id} AND state<>'deleted' FOR UPDATE")
    KnowledgeItemEntity selectOwnedForUpdate(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT problem_json FROM knowledge_item WHERE owner_id=#{ownerId} AND id=#{id} AND state<>'deleted'")
    String selectProblemJson(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT tag_name FROM knowledge_tag WHERE owner_id=#{ownerId} AND knowledge_id=#{id} ORDER BY tag_name")
    List<String> selectTags(@Param("ownerId") String ownerId,@Param("id") String id);

    @Select("SELECT DISTINCT tag_name FROM knowledge_tag WHERE owner_id=#{ownerId} ORDER BY tag_name LIMIT 50")
    List<String> selectAllTags(@Param("ownerId") String ownerId);

    @Insert("INSERT INTO knowledge_revision (id,owner_id,knowledge_id,revision_no,snapshot_json,change_kind) " +
            "VALUES (#{id},#{ownerId},#{knowledgeId},#{revisionNo},#{snapshotJson},#{changeKind})")
    int insertRevision(@Param("id") String id,@Param("ownerId") String ownerId,@Param("knowledgeId") String knowledgeId,
                       @Param("revisionNo") int revisionNo,@Param("snapshotJson") String snapshotJson,@Param("changeKind") String changeKind);

    @Delete("DELETE FROM knowledge_tag WHERE owner_id=#{ownerId} AND knowledge_id=#{id}")
    int deleteTags(@Param("ownerId") String ownerId,@Param("id") String id);

    @Insert("INSERT INTO knowledge_tag (id,owner_id,knowledge_id,tag_name) VALUES (#{tagId},#{ownerId},#{knowledgeId},#{tagName})")
    int insertTag(@Param("tagId") String tagId,@Param("ownerId") String ownerId,
                  @Param("knowledgeId") String knowledgeId,@Param("tagName") String tagName);

    @Update("UPDATE knowledge_item SET item_type=#{itemType},title=#{title},body=#{body},problem_json=#{problemJson}," +
            "search_text=#{searchText},visibility=#{visibility},state=#{state},note_parent_id=#{noteParentId}," +
            "version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE owner_id=#{ownerId} AND id=#{id} " +
            "AND state<>'deleted' AND version_no=#{expectedVersion}")
    int updateItem(@Param("ownerId") String ownerId,@Param("id") String id,@Param("itemType") String itemType,
                   @Param("title") String title,@Param("body") String body,@Param("problemJson") String problemJson,
                   @Param("searchText") String searchText,@Param("visibility") String visibility,@Param("state") String state,
                   @Param("noteParentId") String noteParentId,@Param("expectedVersion") int expectedVersion);

    @Update("UPDATE knowledge_item SET problem_json=#{problemJson},search_text=#{searchText},verification_status=#{status}," +
            "last_verified_date=#{verifiedDate},version_no=version_no+1,updated_at=CURRENT_TIMESTAMP " +
            "WHERE owner_id=#{ownerId} AND id=#{id} AND item_type='problem' AND state<>'deleted' AND version_no=#{expectedVersion}")
    int updateVerification(@Param("ownerId") String ownerId,@Param("id") String id,@Param("problemJson") String problemJson,
                           @Param("searchText") String searchText,@Param("status") String status,
                           @Param("verifiedDate") LocalDate verifiedDate,@Param("expectedVersion") int expectedVersion);

    @Update("UPDATE knowledge_item SET visibility=#{visibility},version_no=version_no+1,updated_at=CURRENT_TIMESTAMP " +
            "WHERE owner_id=#{ownerId} AND id=#{id} AND state<>'deleted' AND version_no=#{expectedVersion}")
    int updateVisibility(@Param("ownerId") String ownerId,@Param("id") String id,
                         @Param("visibility") String visibility,@Param("expectedVersion") int expectedVersion);

    @Update("UPDATE knowledge_item SET state='deleted',visibility='private',version_no=version_no+1,updated_at=CURRENT_TIMESTAMP " +
            "WHERE owner_id=#{ownerId} AND id=#{id} AND state<>'deleted' AND version_no=#{expectedVersion}")
    int softDelete(@Param("ownerId") String ownerId,@Param("id") String id,@Param("expectedVersion") int expectedVersion);

    @Update("UPDATE review_schedule SET state=#{state},due_date=CASE WHEN #{state}='active' THEN COALESCE(due_date,#{dueDate}) ELSE due_date END," +
            "version_no=version_no+1,updated_at=CURRENT_TIMESTAMP WHERE owner_id=#{ownerId} AND knowledge_id=#{knowledgeId}")
    int updateReview(@Param("ownerId") String ownerId,@Param("knowledgeId") String knowledgeId,
                     @Param("state") String state,@Param("dueDate") LocalDate dueDate);

    @Insert("INSERT INTO review_schedule (id,owner_id,knowledge_id,state,stage,due_date,version_no) " +
            "VALUES (#{id},#{ownerId},#{knowledgeId},'active',0,#{dueDate},1)")
    int insertReview(@Param("id") String id,@Param("ownerId") String ownerId,
                     @Param("knowledgeId") String knowledgeId,@Param("dueDate") LocalDate dueDate);

    @Select("SELECT u.id,u.nickname,u.avatar_url,fr.id AS relation_id,fr.generation AS relation_generation " +
            "FROM friend_relation fr JOIN app_user u ON u.id=CASE WHEN fr.user_low_id=#{ownerId} THEN fr.user_high_id ELSE fr.user_low_id END " +
            "WHERE fr.state='active' AND u.status='active' AND (fr.user_low_id=#{ownerId} OR fr.user_high_id=#{ownerId}) " +
            "ORDER BY u.nickname,u.id")
    List<KnowledgeFriendView> selectFriends(@Param("ownerId") String ownerId);

    @Select("SELECT u.id,u.nickname,u.avatar_url,fr.id AS relation_id,fr.generation AS relation_generation " +
            "FROM friend_relation fr JOIN app_user u ON u.id=#{friendId} " +
            "WHERE fr.state='active' AND u.status='active' AND ((fr.user_low_id=#{ownerId} AND fr.user_high_id=#{friendId}) " +
            "OR (fr.user_high_id=#{ownerId} AND fr.user_low_id=#{friendId})) LIMIT 1")
    KnowledgeFriendView selectFriend(@Param("ownerId") String ownerId,@Param("friendId") String friendId);

    @Select("SELECT friend_id FROM knowledge_share_rule WHERE knowledge_id=#{knowledgeId} AND effect='allow' ORDER BY friend_id")
    List<String> selectSelectedFriendIds(@Param("knowledgeId") String knowledgeId);

    @Delete("DELETE FROM knowledge_share_rule WHERE knowledge_id=#{knowledgeId}")
    int deleteShareRules(@Param("knowledgeId") String knowledgeId);

    @Insert("INSERT INTO knowledge_share_rule (id,knowledge_id,friend_id,relation_id,relation_generation,effect,version_no) " +
            "VALUES (#{id},#{knowledgeId},#{friendId},#{relationId},#{generation},'allow',1)")
    int insertShareRule(@Param("id") String id,@Param("knowledgeId") String knowledgeId,@Param("friendId") String friendId,
                        @Param("relationId") String relationId,@Param("generation") int generation);
}
