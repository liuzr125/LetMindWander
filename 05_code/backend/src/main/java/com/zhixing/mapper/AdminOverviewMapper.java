package com.zhixing.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminOverviewMapper {
  int countActiveUsers();

  int countPublishedContent();

  int countPublishedWords();

  int countPublishedArticles();

  int countTasksForDate(@Param("date") LocalDate date);

  int countCompletedTasksForDate(@Param("date") LocalDate date);

  int countOpenFeedback();

  int countAvailableInvites();

  BigDecimal sumSpendForMonth(@Param("date") LocalDate date);
}
