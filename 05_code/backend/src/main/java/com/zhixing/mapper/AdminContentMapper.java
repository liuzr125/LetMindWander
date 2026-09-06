package com.zhixing.mapper;

import com.zhixing.model.ContentCoverageView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AdminContentMapper {
    @Select("SELECT COUNT(*) FROM learning_content WHERE state='published'") Integer countPublished();
    @Select("SELECT COUNT(*) FROM content_source WHERE enabled=1") Integer countSources();
    @Select("SELECT t.name,COUNT(DISTINCT lc.id) AS item_count FROM learning_topic t JOIN content_topic ct ON ct.topic_id=t.id " +
            "JOIN content_version cv ON cv.id=ct.content_version_id JOIN learning_content lc ON lc.published_version_id=cv.id " +
            "WHERE lc.content_type='tech' AND lc.state='published' GROUP BY t.id,t.name ORDER BY t.name")
    List<ContentCoverageView.CoverageItem> selectTechnicalTopics();
    @Select("SELECT CASE stage WHEN 'primary' THEN '小学' WHEN 'junior' THEN '初中' WHEN 'senior' THEN '高中' ELSE '未分级' END AS name," +
            "COUNT(*) AS item_count FROM learning_content WHERE content_type='word' AND state='published' GROUP BY stage ORDER BY stage")
    List<ContentCoverageView.CoverageItem> selectWordStages();
}
