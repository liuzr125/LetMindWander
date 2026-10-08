package com.zhixing.mapper;

import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminWordStudyMapper {
  int countEvents(@Param("roundId") String roundId);

  List<Map<String, Object>> selectEvents(
      @Param("roundId") String roundId, @Param("offset") int offset, @Param("limit") int limit);

  List<Map<String, Object>> selectEventRounds(@Param("eventIds") List<String> eventIds);
}
