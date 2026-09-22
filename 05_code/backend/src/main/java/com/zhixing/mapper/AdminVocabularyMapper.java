package com.zhixing.mapper;

import com.zhixing.model.AdminVocabularyBookView;
import com.zhixing.model.AdminVocabularyWordView;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AdminVocabularyMapper {
    @Select("SELECT vb.id book_id,vb.book_code,vb.book_name,vb.book_type,vb.level_code,vb.description," +
            "vb.word_count declared_word_count,COUNT(vbw.id) member_count," +
            "COALESCE(SUM(CASE WHEN lc.content_type='word' AND lc.state='published' AND cv.review_status='approved' THEN 1 ELSE 0 END),0) available_count " +
            "FROM vocabulary_book vb LEFT JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id " +
            "LEFT JOIN learning_content lc ON lc.id=vbw.content_id LEFT JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE vb.state='active' GROUP BY vb.id,vb.book_code,vb.book_name,vb.book_type,vb.level_code,vb.description,vb.word_count,vb.sort_no " +
            "ORDER BY vb.sort_no,vb.book_name")
    List<AdminVocabularyBookView> selectBooks();

    @Select("SELECT vb.id book_id,vb.book_code,vb.book_name,vb.book_type,vb.level_code,vb.description," +
            "vb.word_count declared_word_count,COUNT(vbw.id) member_count," +
            "COALESCE(SUM(CASE WHEN lc.content_type='word' AND lc.state='published' AND cv.review_status='approved' THEN 1 ELSE 0 END),0) available_count " +
            "FROM vocabulary_book vb LEFT JOIN vocabulary_book_word vbw ON vbw.book_id=vb.id " +
            "LEFT JOIN learning_content lc ON lc.id=vbw.content_id LEFT JOIN content_version cv ON cv.id=lc.published_version_id " +
            "WHERE vb.id=#{bookId} AND vb.state='active' GROUP BY vb.id,vb.book_code,vb.book_name,vb.book_type,vb.level_code,vb.description,vb.word_count,vb.sort_no")
    AdminVocabularyBookView selectBook(@Param("bookId")String bookId);

    @Select({"<script>","SELECT lc.id content_id,cv.word_term,cv.phonetic,cv.meaning,lc.stage,lc.state content_state,cv.review_status,",
            "vbw.sort_no,vbw.importance,CASE WHEN vbw.is_core=1 THEN TRUE ELSE FALSE END core,vbw.source_level,vbw.source_ref ",
            "FROM vocabulary_book_word vbw LEFT JOIN learning_content lc ON lc.id=vbw.content_id ",
            "LEFT JOIN content_version cv ON cv.id=lc.published_version_id WHERE vbw.book_id=#{bookId} ",
            "<if test=\"keyword != null and keyword != ''\">AND (LOWER(cv.word_term) LIKE CONCAT('%',LOWER(#{keyword}),'%') OR cv.meaning LIKE CONCAT('%',#{keyword},'%')) </if>",
            "<if test=\"stage == 'unclassified'\">AND lc.stage IS NULL </if>",
            "<if test=\"stage != null and stage != '' and stage != 'unclassified'\">AND lc.stage=#{stage} </if>",
            "ORDER BY vbw.sort_no,vbw.importance DESC,vbw.id LIMIT #{offset},#{limit}","</script>"})
    List<AdminVocabularyWordView> selectWords(@Param("bookId")String bookId,@Param("keyword")String keyword,@Param("stage")String stage,@Param("offset")int offset,@Param("limit")int limit);

    @Select({"<script>","SELECT COUNT(*) FROM vocabulary_book_word vbw LEFT JOIN learning_content lc ON lc.id=vbw.content_id ",
            "LEFT JOIN content_version cv ON cv.id=lc.published_version_id WHERE vbw.book_id=#{bookId} ",
            "<if test=\"keyword != null and keyword != ''\">AND (LOWER(cv.word_term) LIKE CONCAT('%',LOWER(#{keyword}),'%') OR cv.meaning LIKE CONCAT('%',#{keyword},'%')) </if>",
            "<if test=\"stage == 'unclassified'\">AND lc.stage IS NULL </if>",
            "<if test=\"stage != null and stage != '' and stage != 'unclassified'\">AND lc.stage=#{stage} </if>","</script>"})
    int countWords(@Param("bookId")String bookId,@Param("keyword")String keyword,@Param("stage")String stage);
}
