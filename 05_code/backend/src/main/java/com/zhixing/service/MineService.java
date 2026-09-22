package com.zhixing.service;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.UserFeedbackRequest;
import com.zhixing.mapper.MineMapper;
import com.zhixing.model.FavoriteView;
import com.zhixing.model.MineOverviewView;
import com.zhixing.model.PrivacyView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
public class MineService {
 private static final ZoneId ZONE=ZoneId.of("Asia/Shanghai"); private final MineMapper mine; private final ProfileService profiles; private final AppParameterService parameters; private final com.zhixing.config.AppProperties properties;
 public MineService(MineMapper mine,ProfileService profiles,AppParameterService parameters,com.zhixing.config.AppProperties properties){this.mine=mine;this.profiles=profiles;this.parameters=parameters;this.properties=properties;}
 public MineOverviewView overview(String ownerId){LocalDate today=LocalDate.now(ZONE);MineOverviewView v=mine.selectOverview(ownerId,today,today.atStartOfDay(ZONE).toInstant());if(v==null)v=new MineOverviewView();v.setProfile(profiles.findView(ownerId));v.setAiLimit(parameters.intValue(AppParameterService.AI_PERSONAL_DAILY_LIMIT,properties.getAi().getPersonalDailyLimit(),1,10000));return v;}
 public List<FavoriteView> favorites(String ownerId,String type,String query){return mine.selectFavorites(ownerId,trim(type),trim(query));}
 public PrivacyView privacy(String ownerId){return mine.selectPrivacy(ownerId);}
 @Transactional public PrivacyView requestExport(String ownerId){Instant now=Instant.now();if(mine.countActiveExports(ownerId,now)==0)mine.insertExport(CryptoUtils.randomId(),ownerId,now);return privacy(ownerId);}
 @Transactional public Map<String,Object> requestDeletion(String ownerId){if(mine.countActiveDeletions(ownerId)>0)throw new ApiException(HttpStatus.CONFLICT,"DELETION_ALREADY_ACTIVE","账号注销已在处理中");Instant now=Instant.now();String receipt=CryptoUtils.randomToken(32);String id=CryptoUtils.randomId();mine.insertDeletion(id,ownerId,CryptoUtils.sha256(receipt),now.plus(Duration.ofHours(24)),now.plus(Duration.ofDays(7)),now.plus(Duration.ofDays(30)),now);Map<String,Object> r=new LinkedHashMap<String,Object>();r.put("id",id);r.put("state","requested");r.put("receipt",receipt);return r;}
 public Map<String,Object> feedback(String ownerId,UserFeedbackRequest request){String body=trim(request.getBody());if(body==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FEEDBACK","反馈内容不能为空");if(body.codePointCount(0,body.length())>2000)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FEEDBACK","反馈内容最多 2000 个字符");String category=trim(request.getCategory());if(category==null)category="suggestion";if(!Arrays.asList("bug","suggestion","content_error","copyright").contains(category))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FEEDBACK_CATEGORY","反馈类型不合法");String id=CryptoUtils.randomId();mine.insertFeedback(id,ownerId,category,trim(request.getContentId()),body,trim(request.getRequestId()),Instant.now());Map<String,Object> r=new LinkedHashMap<String,Object>();r.put("id",id);r.put("state","open");return r;}
 private String trim(String v){return v==null||v.trim().isEmpty()?null:v.trim();}
}
