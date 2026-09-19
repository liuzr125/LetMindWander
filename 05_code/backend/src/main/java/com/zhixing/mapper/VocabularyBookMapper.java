package com.zhixing.mapper;

import com.zhixing.model.UserVocabularyBookRow;
import com.zhixing.model.VocabularyBookView;
import com.zhixing.model.VocabularyBookProgressItemView;
import com.zhixing.model.VocabularyBookProgressView;
import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.util.List;

@Mapper
public interface VocabularyBookMapper {
    @Select("SELECT id FROM app_user WHERE id=#{ownerId} FOR UPDATE")
    String lockOwner(@Param("ownerId") String ownerId);

    @Select("SELECT vb.id,vb.book_code,vb.book_name,vb.book_type,vb.level_code,vb.description,vb.word_count," +
            "CASE WHEN vb.is_recommended=1 THEN TRUE ELSE FALSE END AS recommended," +
            "CASE WHEN uvb.state='active' THEN TRUE ELSE FALSE END AS selected," +
            "uvb.state AS selection_state,uvb.daily_new_limit,uvb.row_version " +
            "FROM vocabulary_book vb LEFT JOIN user_vocabulary_book uvb ON uvb.book_id=vb.id AND uvb.owner_id=#{ownerId} " +
            "WHERE vb.state='active' ORDER BY vb.sort_no,vb.book_name")
    List<VocabularyBookView> selectAvailable(@Param("ownerId") String ownerId);

    @Select("SELECT vb.id,vb.book_code,vb.book_name,vb.book_type,vb.level_code,vb.description,vb.word_count," +
            "CASE WHEN vb.is_recommended=1 THEN TRUE ELSE FALSE END AS recommended,TRUE AS selected," +
            "uvb.state AS selection_state,uvb.daily_new_limit,uvb.row_version " +
            "FROM user_vocabulary_book uvb JOIN vocabulary_book vb ON vb.id=uvb.book_id " +
            "WHERE uvb.owner_id=#{ownerId} AND uvb.state='active' AND vb.state='active' " +
            "ORDER BY uvb.updated_at DESC LIMIT 1")
    VocabularyBookView selectCurrent(@Param("ownerId") String ownerId);

    @Select("SELECT uvb.book_id FROM user_vocabulary_book uvb JOIN vocabulary_book vb ON vb.id=uvb.book_id " +
            "WHERE uvb.owner_id=#{ownerId} AND uvb.state='active' AND vb.state='active' ORDER BY uvb.updated_at DESC LIMIT 1")
    String selectActiveBookId(@Param("ownerId") String ownerId);

    @Select("SELECT vb.id AS book_id,vb.book_name,vb.book_code,COUNT(DISTINCT cv.id) AS total_count," +
            "COUNT(DISTINCT CASE WHEN lr.learning_status IN ('understood','mastered') THEN lc.id END) AS learned_count " +
            "FROM user_vocabulary_book uvb JOIN vocabulary_book vb ON vb.id=uvb.book_id " +
            "LEFT JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id " +
            "LEFT JOIN learning_content lc ON lc.id=vbw.content_id AND lc.content_type='word' AND lc.state='published' " +
            "LEFT JOIN content_version cv ON cv.id=lc.published_version_id AND cv.review_status='approved' " +
            "LEFT JOIN learning_record lr ON lr.owner_id=uvb.owner_id AND lr.content_id=lc.id " +
            "WHERE uvb.owner_id=#{ownerId} AND uvb.state='active' AND vb.state='active' " +
            "GROUP BY vb.id,vb.book_name,vb.book_code LIMIT 1")
    VocabularyBookProgressView selectCurrentProgress(@Param("ownerId") String ownerId);

    @Select({"<script>",
            "SELECT lc.id AS content_id,cv.word_term,COALESCE(NULLIF(TRIM(cv.phonetic),''),(SELECT p.phonetic FROM pronunciation p WHERE p.content_version_id=cv.id AND p.state='ready' AND p.phonetic IS NOT NULL AND TRIM(p.phonetic)&lt;&gt;'' ORDER BY CASE p.accent WHEN 'uk' THEN 0 WHEN 'us' THEN 1 ELSE 2 END,p.id LIMIT 1),'') AS phonetic,cv.meaning,",
            "CASE WHEN lr.learning_status IN ('understood','mastered') THEN TRUE ELSE FALSE END AS learned,",
            "lr.first_completed_at AS learned_at FROM vocabulary_book_word vbw ",
            "JOIN learning_content lc ON lc.id=vbw.content_id AND lc.content_type='word' AND lc.state='published' ",
            "JOIN content_version cv ON cv.id=lc.published_version_id AND cv.review_status='approved' ",
            "LEFT JOIN learning_record lr ON lr.owner_id=#{ownerId} AND lr.content_id=lc.id ",
            "WHERE vbw.book_id=#{bookId} ",
            "<if test=\"status == 'learned'\">AND lr.learning_status IN ('understood','mastered') </if>",
            "<if test=\"status == 'remaining'\">AND (lr.learning_status IS NULL OR lr.learning_status NOT IN ('understood','mastered')) </if>",
            "ORDER BY vbw.sort_no,vbw.importance DESC,lc.id LIMIT #{offset},#{limit}",
            "</script>"})
    List<VocabularyBookProgressItemView> selectProgressItems(@Param("ownerId") String ownerId,@Param("bookId") String bookId,
                                                              @Param("status") String status,@Param("offset") int offset,@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM vocabulary_book WHERE id=#{bookId} AND state='active'")
    int countActiveBook(@Param("bookId") String bookId);

    @Select("SELECT COUNT(*) FROM vocabulary_book_word vbw JOIN learning_content lc ON lc.id=vbw.content_id " +
            "JOIN content_version cv ON cv.id=lc.published_version_id WHERE vbw.book_id=#{bookId} " +
            "AND lc.content_type='word' AND lc.state='published' AND cv.review_status='approved'")
    int countAvailableWords(@Param("bookId") String bookId);

    @Select("SELECT id,owner_id,book_id,state,daily_new_limit,row_version FROM user_vocabulary_book " +
            "WHERE owner_id=#{ownerId} AND book_id=#{bookId} FOR UPDATE")
    UserVocabularyBookRow selectOwnedForUpdate(@Param("ownerId") String ownerId,@Param("bookId") String bookId);

    @Update("UPDATE user_vocabulary_book SET state='paused',paused_at=#{now},row_version=row_version+1,updated_at=#{now} " +
            "WHERE owner_id=#{ownerId} AND state='active' AND book_id<>#{bookId}")
    int pauseOtherBooks(@Param("ownerId") String ownerId,@Param("bookId") String bookId,@Param("now") Instant now);

    @Insert("INSERT INTO user_vocabulary_book(id,owner_id,book_id,state,daily_new_limit,selected_at,row_version,created_at,updated_at) " +
            "VALUES(#{id},#{ownerId},#{bookId},'active',#{dailyNewLimit},#{now},1,#{now},#{now})")
    int insertSelection(@Param("id") String id,@Param("ownerId") String ownerId,@Param("bookId") String bookId,
                        @Param("dailyNewLimit") int dailyNewLimit,@Param("now") Instant now);

    @Update("UPDATE user_vocabulary_book SET state='active',daily_new_limit=#{dailyNewLimit},selected_at=#{now}," +
            "paused_at=NULL,completed_at=NULL,row_version=row_version+1,updated_at=#{now} " +
            "WHERE owner_id=#{ownerId} AND book_id=#{bookId}")
    int activateSelection(@Param("ownerId") String ownerId,@Param("bookId") String bookId,
                          @Param("dailyNewLimit") int dailyNewLimit,@Param("now") Instant now);
}
