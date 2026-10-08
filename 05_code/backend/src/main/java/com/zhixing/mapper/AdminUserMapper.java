package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.AdminUserAccountEntity;
import com.zhixing.model.AdminUserLearningView;
import java.time.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserAccountEntity> {
  int countPage(@Param("keyword") String keyword, @Param("status") String status);

  List<AdminUserAccountEntity> selectPageAccounts(
      @Param("keyword") String keyword,
      @Param("status") String status,
      @Param("limit") int limit,
      @Param("offset") int offset);

  int countLearningPlans(@Param("ownerId") String ownerId);

  int updateAiAuthorization(
      @Param("consentVersion") String consentVersion,
      @Param("consentedAt") Instant consentedAt,
      @Param("userId") String userId);

  AdminUserLearningView.TodayProgressView selectTodayProgress(
      @Param("ownerId") String ownerId, @Param("businessDate") LocalDate businessDate);
}
