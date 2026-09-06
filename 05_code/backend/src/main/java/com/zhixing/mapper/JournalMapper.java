package com.zhixing.mapper;

import com.zhixing.entity.DailyJournalEntity;
import com.zhixing.entity.JournalRevisionEntity;
import com.zhixing.model.JournalView;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface JournalMapper {
    @Select("SELECT j.id AS journal_id,j.business_date,j.state,j.version_no,r.done_text,r.blocker_text,r.learned_text,r.next_step_text," +
            "r.created_at AS saved_at,j.last_submitted_at AS submitted_at FROM daily_journal j " +
            "LEFT JOIN journal_revision r ON r.id=j.current_revision_id WHERE j.owner_id=#{ownerId} AND j.business_date=#{date}")
    JournalView selectView(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Select("SELECT j.id AS journal_id,j.business_date,j.state,j.version_no,r.done_text,r.blocker_text,r.learned_text,r.next_step_text," +
            "r.created_at AS saved_at,j.last_submitted_at AS submitted_at FROM daily_journal j " +
            "LEFT JOIN journal_revision r ON r.id=j.current_revision_id WHERE j.owner_id=#{ownerId} ORDER BY j.business_date DESC LIMIT 30")
    List<JournalView> selectHistory(@Param("ownerId") String ownerId);

    @Select("SELECT * FROM daily_journal WHERE owner_id=#{ownerId} AND business_date=#{date} FOR UPDATE")
    DailyJournalEntity selectForUpdate(@Param("ownerId") String ownerId,@Param("date") LocalDate date);

    @Insert("INSERT INTO daily_journal (id,owner_id,business_date,version_no,state) VALUES (#{id},#{ownerId},#{businessDate},#{versionNo},#{state})")
    int insertJournal(DailyJournalEntity journal);

    @Insert("INSERT INTO journal_revision (id,owner_id,journal_id,revision_no,done_text,blocker_text,learned_text,next_step_text,content_hash,save_kind) " +
            "VALUES (#{id},#{ownerId},#{journalId},#{revisionNo},#{doneText},#{blockerText},#{learnedText},#{nextStepText},#{contentHash},#{saveKind})")
    int insertRevision(JournalRevisionEntity revision);

    @Update("UPDATE daily_journal SET current_revision_id=#{revisionId},submitted_revision_id=CASE WHEN #{submitted} THEN #{revisionId} ELSE submitted_revision_id END," +
            "version_no=#{versionNo},state=CASE WHEN #{submitted} THEN 'submitted' ELSE state END," +
            "first_submitted_at=CASE WHEN #{submitted} THEN COALESCE(first_submitted_at,#{now}) ELSE first_submitted_at END," +
            "last_submitted_at=CASE WHEN #{submitted} THEN #{now} ELSE last_submitted_at END,updated_at=#{now} WHERE id=#{journalId}")
    int updatePointers(@Param("journalId") String journalId,@Param("revisionId") String revisionId,
                       @Param("versionNo") Integer versionNo,@Param("submitted") boolean submitted,@Param("now") Instant now);
}
