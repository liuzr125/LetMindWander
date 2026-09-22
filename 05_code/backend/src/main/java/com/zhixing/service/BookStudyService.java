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
 * 把这次动作落到「用户 × 词书」的汇总表与每日明细表，管理端即可查看每本书的学习记录与详情。
 *
 * 只记录动作次数与时间：已学/掌握词数、当天新学词数按 learning_record 实时统计，避免冗余字段与事实漂移。
 */
@Service
public class BookStudyService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    public static final String KIND_STUDY="study",KIND_REVIEW="review";
    private final JdbcTemplate jdbc;
    public BookStudyService(JdbcTemplate jdbc){this.jdbc=jdbc;}

    /** 学习动作：kind=study（词条页已学会、记忆反馈、熟悉度）；kind=review（到期复习）。一个词条可能属于多本词书，都记一笔。 */
    @Transactional
    public void record(String ownerId,String contentId,String kind,Instant now){
        if(ownerId==null||contentId==null)return;
        List<String> bookIds=jdbc.queryForList("SELECT DISTINCT book_id FROM vocabulary_book_word WHERE content_id=?",String.class,contentId);
        if(bookIds.isEmpty())return;
        LocalDate businessDate=(now==null?Instant.now():now).atZone(BUSINESS_ZONE).toLocalDate();
        Timestamp at=Timestamp.from(now==null?Instant.now():now);
        boolean review=KIND_REVIEW.equals(kind);
        for(String bookId:bookIds){
            boolean newDay=bumpDaily(ownerId,bookId,businessDate,review);
            bumpSummary(ownerId,bookId,at,review,newDay);
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

    private boolean bumpDaily(String ownerId,String bookId,LocalDate businessDate,boolean review){
        String column=review?"reviewed_count":"study_count";
        int updated=jdbc.update("UPDATE vocabulary_book_study_daily SET "+column+"="+column+"+1,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND book_id=? AND business_date=?",
                ownerId,bookId,Date.valueOf(businessDate));
        if(updated>0)return false;
        try{
            jdbc.update("INSERT INTO vocabulary_book_study_daily(id,owner_id,book_id,business_date,study_count,reviewed_count) VALUES(?,?,?,?,?,?)",
                    CryptoUtils.randomId(),ownerId,bookId,Date.valueOf(businessDate),review?0:1,review?1:0);
            return true;
        }catch(DuplicateKeyException e){
            jdbc.update("UPDATE vocabulary_book_study_daily SET "+column+"="+column+"+1,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND book_id=? AND business_date=?",
                    ownerId,bookId,Date.valueOf(businessDate));
            return false;
        }
    }

    private void bumpSummary(String ownerId,String bookId,Timestamp at,boolean review,boolean newDay){
        int study=review?0:1,reviewed=review?1:0,day=newDay?1:0;
        if(updateSummary(ownerId,bookId,at,study,reviewed,day)>0)return;
        try{
            jdbc.update("INSERT INTO vocabulary_book_study_record(id,owner_id,book_id,first_studied_at,last_studied_at,study_count,reviewed_count,study_day_count) VALUES(?,?,?,?,?,?,?,?)",
                    CryptoUtils.randomId(),ownerId,bookId,at,at,study,reviewed,1);
        }catch(DuplicateKeyException e){
            updateSummary(ownerId,bookId,at,study,reviewed,day);
        }
    }

    private int updateSummary(String ownerId,String bookId,Timestamp at,int study,int reviewed,int day){
        return jdbc.update("UPDATE vocabulary_book_study_record SET last_studied_at=?,study_count=study_count+?,reviewed_count=reviewed_count+?," +
                "study_day_count=study_day_count+?,updated_at=CURRENT_TIMESTAMP WHERE owner_id=? AND book_id=?",at,study,reviewed,day,ownerId,bookId);
    }
}
