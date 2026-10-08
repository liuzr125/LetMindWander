package com.zhixing.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Persistence statements for ArticleWordGlossary business operations. */
@Mapper
public interface ArticleWordGlossaryPersistenceMapper {
  List<Map<String, Object>> findPublishedArticleBody(@Param("p0") Object p0);

  int insertGeneratedGlossary(
      @Param("p0") Object p0,
      @Param("p1") Object p1,
      @Param("p2") Object p2,
      @Param("p3") Object p3);

  List<Map<String, Object>> findFormalWordGlossary(@Param("p0") Object p0);

  List<Map<String, Object>> findCachedGlossary(@Param("p0") Object p0);
}
