package com.zhixing.service;

import com.zhixing.common.CryptoUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 词书学习记录（写入侧）：用户对某个词条做出学习动作（已学会 / 记忆反馈 / 熟悉度 / 到期复习）时，
 * 把这次动作落到「用户 × 词书 × 轮次」的汇总表与每日明细表，管理端即可查看每本书每一轮的学习记录与详情。
 *
 * 轮次（round）：一轮 = 一次「选定这本词书」。切走时该轮冻结（ended_at + 结束时已学快照），再次切回同一本书会新开一轮，
 * 并把上一轮的已学/未学状态带过来（词条学习状态本身不动，只记「带入已学 carried_learned_count」），之后的学习只改当前轮。
 *
 * 只记录动作次数与时间：已学/已掌握词数、本轮新学词数按 learning_record 实时统计，避免冗余字段与事实漂移。
 */
@Service
public class BookStudyService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    public static final String KIND_STUDY="study",KIND_REVIEW="review";
    private final JdbcTemplate jdbc;
    public BookStudyService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** 选定词书时开启一轮：先冻结其它词书进行中的轮次，再保证这本书有一轮进行中（已在进行中则沿用）。 */
    @Transactional
    public void openRound(String ownerId,String bookId,Instant now){
        if(ownerId==null||bookId==null)return;
        Timestamp at=Timestamp.from(now==null?Instant.now():now);
        closeOtherRounds(ownerId,bookId,at);
        ensureOpenRound(ownerId,bookId,at);
    }

    /** 学习动作：kind=study（词条页已学会、记忆反馈、熟悉度）；kind=review（到期复习）。一个词条可能属于多本词书，都记一笔。 */
    @Transactional
    public void record(String ownerId,String contentId,String kind,Instant now){
        if(ownerId==null||contentId==null)return;
        List<String> bookIds=jdbc.queryForList("SELECT DISTINCT book_id FROM vocabulary_book_word WHERE content_id=?",String.class,contentId);
        if(bookIds.isEmpty())return;
        Instant moment=now==null?Instant.now():now;
        Timestamp at=Timestamp.from(moment);
        LocalDate businessDate=moment.atZone(BUSINESS_ZONE).toLocalDate();
        boolean review=KIND_REVIEW.equals(kind);
        for(String bookId:bookIds){
            String roundId=roundForStudy(ownerId,bookId,at);
            boolean newDay=bumpDaily(roundId,businessDate,review);
            bumpSummary(roundId,at,review,newDay);
        }
    }

    /** 到期复习：先用复习项反查词条，再按词条记到它所属的词书。 */
    @Transactional
    public void recordReview(String ownerId,String scheduleId,Instant now){
        if(ownerId==null||scheduleId==null)return;
        List<String> rows=jdbc.queryForList("SELECT ki.source_content_id FROM review_schedule rs JOIN knowledge_item ki ON ki.id=rs.knowledge_id AND ki.owner_id=rs.owner_id " +
                "WHERE rs.id=? AND rs.owner_id=? AND ki.source_content_id IS NOT NULL",String.class,scheduleId,ownerId);
        if(rows.isEmpty())return;
        record(ownerId,rows.get(0),KIND_REVIEW,now);
    }

    /** 「重新学习」记录：把这次重置写进 vocabulary_book_reset_log（归属这本书当前进行中的轮次）。 */
    @Transactional
    public void recordReset(String ownerId,String bookId,int resetCount,int pausedReviewCount,Instant now){
        if(ownerId==null||bookId==null)return;
        Instant moment=now==null?Instant.now():now;
        Timestamp at=Timestamp.from(moment);
        String roundId=ensureOpenRound(ownerId,bookId,at);
        if(roundId==null)return;
        try{
            jdbc.update("INSERT INTO vocabulary_book_reset_log(id,owner_id,book_id,round_id,reset_count,paused_review_count,reset_at) VALUES(?,?,?,?,?,?,?)",
                    CryptoUtils.randomId(),ownerId,bookId,roundId,resetCount,pausedReviewCount,at);
        }catch(DuplicateKeyException ignored){ }
    }

    /** 当前学习状态；没有记录返回 null。 */
    public String learningStatus(String ownerId,String contentId){
        List<String> rows=jdbc.queryForList("SELECT learning_status FROM learning_record WHERE owner_id=? AND content_id=? LIMIT 1",String.class,ownerId,contentId);
        return rows.isEmpty()?null:rows.get(0);
    }
    public static boolean learned(String status){return "understood".equals(status)||"mastered".equals(status);}

    /** 冻结其它词书进行中的轮次：记录结束时间，并快照结束时已学词数（此后不再改动这一轮）。 */
    private void closeOtherRounds(String ownerId,String bookId,Timestamp at){
        jdbc.update("UPDATE vocabulary_book_study_record r SET r.ended_at=?,r.final_learned_count=(" +
                "SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status IN ('understood','mastered')" +
                "),r.updated_at=CURRENT_TIMESTAMP " +
                "WHERE r.owner_id=? AND r.book_id<>? AND r.ended_at IS NULL",at,ownerId,bookId);
    }

    /** 找这本书进行中的轮次；没有就新开一轮（带入已学 = 当前这本书的已学词数）。返回轮次行 id。 */
    private String ensureOpenRound(String ownerId,String bookId,Timestamp at){
        List<String> open=findOpenRound(ownerId,bookId);
        if(!open.isEmpty())return open.get(0);
        try{
            // 并发下同一本书只允许一轮进行中：唯一键 (owner,book,round_no) 兜底，重复插入时改读已存在的轮次
            jdbc.update("INSERT INTO vocabulary_book_study_record(id,owner_id,book_id,round_no,selected_at,carried_learned_count)" +
                            " SELECT ?,?,?,COALESCE(MAX(round_no),0)+1,?," +
                            " (SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id" +
                            "   WHERE lr.owner_id=? AND w.book_id=? AND lr.learning_status IN ('understood','mastered'))" +
                            " FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=?",
                    CryptoUtils.randomId(),ownerId,bookId,at,ownerId,bookId,ownerId,bookId);
        }catch(DuplicateKeyException ignored){ }
        List<String> again=findOpenRound(ownerId,bookId);
        return again.isEmpty()?null:again.get(0);
    }

    /**
     * 学习动作归属的轮次：
     * - 这本书是当前选定词书 → 用进行中的那一轮（没有就新开一轮）；
     * - 不是当前词书（例如翻到别的词书复习）→ 先把它的「进行中」轮次按切换时间收口，再并进当天的记录；
     *   当天还没有记录就新开一轮并立即结束（一次性记录），这样老记录永远不会被后来的学习改写。
     */
    private String roundForStudy(String ownerId,String bookId,Timestamp at){
        String activeBook=activeBookId(ownerId);
        boolean active=bookId.equals(activeBook);
        List<String> open=findOpenRound(ownerId,bookId);
        if(!open.isEmpty()){
            if(active)return open.get(0);
            closeRound(ownerId,bookId,open.get(0),at);
        }
        if(active)return ensureOpenRound(ownerId,bookId,at);
        // 非当前词书：当天的记录并进去，否则新开一轮并立即结束（一次性记录，之后不再被改写）
        Timestamp dayStart=Timestamp.valueOf(at.toInstant().atZone(BUSINESS_ZONE).toLocalDate().atStartOfDay());
        List<String> sameDay=jdbc.queryForList("SELECT id FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=? AND selected_at>=? ORDER BY round_no DESC LIMIT 1",
                String.class,ownerId,bookId,dayStart);
        if(!sameDay.isEmpty())return sameDay.get(0);
        String created=ensureOpenRound(ownerId,bookId,at);
        if(created!=null)closeRound(ownerId,bookId,created,at);
        return created;
    }

    /** 这本书当前选定的词书 id（没有则 null）。 */
    private String activeBookId(String ownerId){
        List<String> rows=jdbc.queryForList("SELECT book_id FROM user_vocabulary_book WHERE owner_id=? AND state='active' ORDER BY updated_at DESC LIMIT 1",String.class,ownerId);
        return rows.isEmpty()?null:rows.get(0);
    }

    /** 结束一轮：写结束时间（不早于选择时间）与结束时已学快照。 */
    private void closeRound(String ownerId,String bookId,String roundId,Timestamp at){
        jdbc.update("UPDATE vocabulary_book_study_record r SET r.ended_at=GREATEST(COALESCE((SELECT uvb.paused_at FROM user_vocabulary_book uvb WHERE uvb.owner_id=r.owner_id AND uvb.book_id=r.book_id LIMIT 1),?)," +
                "COALESCE(r.selected_at,?)),r.final_learned_count=(" +
                "SELECT COUNT(DISTINCT lr.content_id) FROM learning_record lr JOIN vocabulary_book_word w ON w.content_id=lr.content_id " +
                " WHERE lr.owner_id=r.owner_id AND w.book_id=r.book_id AND lr.learning_status IN ('understood','mastered')" +
                "),r.updated_at=CURRENT_TIMESTAMP WHERE r.id=? AND r.ended_at IS NULL",at,at,roundId);
    }

    private List<String> findOpenRound(String ownerId,String bookId){
        return jdbc.queryForList("SELECT id FROM vocabulary_book_study_record WHERE owner_id=? AND book_id=? AND ended_at IS NULL ORDER BY round_no DESC LIMIT 1",String.class,ownerId,bookId);
    }

    /** 每日动作按轮次累积：老轮次的明细不会被后面几轮改写。 */
    private boolean bumpDaily(String roundId,LocalDate businessDate,boolean review){
        if(roundId==null)return false;
        String column=review?"reviewed_count":"study_count";
        int updated=jdbc.update("UPDATE vocabulary_book_study_daily SET "+column+"="+column+"+1,updated_at=CURRENT_TIMESTAMP WHERE round_id=? AND business_date=?",
                roundId,Date.valueOf(businessDate));
        if(updated>0)return false;
        try{
            jdbc.update("INSERT INTO vocabulary_book_study_daily(id,round_id,owner_id,book_id,business_date,study_count,reviewed_count)" +
                            " SELECT ?,?,owner_id,book_id,?,?,? FROM vocabulary_book_study_record WHERE id=?",
                    CryptoUtils.randomId(),roundId,Date.valueOf(businessDate),review?0:1,review?1:0,roundId);
            return true;
        }catch(DuplicateKeyException e){
            jdbc.update("UPDATE vocabulary_book_study_daily SET "+column+"="+column+"+1,updated_at=CURRENT_TIMESTAMP WHERE round_id=? AND business_date=?",
                    roundId,Date.valueOf(businessDate));
            return false;
        }
    }

    private void bumpSummary(String roundId,Timestamp at,boolean review,boolean newDay){
        if(roundId==null)return;
        int study=review?0:1,reviewed=review?1:0,day=newDay?1:0;
        jdbc.update("UPDATE vocabulary_book_study_record SET first_studied_at=COALESCE(first_studied_at,?),last_studied_at=?," +
                "study_count=study_count+?,reviewed_count=reviewed_count+?,study_day_count=study_day_count+?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                at,at,study,reviewed,day,roundId);
    }

}
