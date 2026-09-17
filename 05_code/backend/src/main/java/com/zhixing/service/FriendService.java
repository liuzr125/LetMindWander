package com.zhixing.service;
import com.zhixing.common.*;
import com.zhixing.dto.*;
import com.zhixing.entity.*;
import com.zhixing.mapper.FriendMapper;
import com.zhixing.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class FriendService {
 private final FriendMapper friends;
 public FriendService(FriendMapper friends){this.friends=friends;}
 public List<FriendView> list(String ownerId){return friends.selectFriends(ownerId);}
 public List<FriendSearchView> search(String ownerId,String type,String query){String q=trim(query);if(q==null)return Collections.emptyList();String t=Arrays.asList("id","mobile","nickname").contains(type)?type:"id";return friends.search(ownerId,t,q);}
 public List<FriendRequestView> requests(String ownerId,String direction){return friends.selectRequests(ownerId,"sent".equals(direction)?"sent":"received");}
 public List<SharedKnowledgeView> shared(String ownerId,String friendId){return friends.selectShared(ownerId,friendId);}
 @Transactional public Map<String,Object> send(String ownerId,CreateFriendRequest request){String target=trim(request.getTargetUserId()),remark=trim(request.getRemark());if(target==null||target.equals(ownerId))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FRIEND_TARGET","不能向自己发送好友申请");if(friends.countActiveUser(target)==0)throw new ApiException(HttpStatus.NOT_FOUND,"USER_NOT_FOUND","目标用户不存在或不可用");if(remark==null||remark.codePointCount(0,remark.length())>200)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FRIEND_REMARK","申请备注需为 1 至 200 个字符");String low=ownerId.compareTo(target)<0?ownerId:target,high=ownerId.compareTo(target)<0?target:ownerId;FriendRelationEntity relation=friends.selectRelationForUpdate(low,high);if(relation==null){relation=new FriendRelationEntity();relation.setId(CryptoUtils.randomId());relation.setUserLowId(low);relation.setUserHighId(high);relation.setState("none");relation.setGeneration(0);relation.setVersionNo(1);friends.insert(relation);relation=friends.selectRelationForUpdate(low,high);}if("active".equals(relation.getState()))throw new ApiException(HttpStatus.CONFLICT,"ALREADY_FRIENDS","你们已经是好友");FriendRequestEntity pending=friends.selectPendingForUpdate(relation.getId());if(pending!=null)throw new ApiException(HttpStatus.CONFLICT,"FRIEND_REQUEST_PENDING","已有待处理的好友申请");String id=CryptoUtils.randomId();friends.insertRequest(id,relation.getId(),ownerId,target,remark,Instant.now());Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("id",id);result.put("state","pending");return result;}
 @Transactional public FriendRequestView decide(String ownerId,String requestId,DecideFriendRequest request){String state="accept".equals(request.getDecision())?"accepted":"reject".equals(request.getDecision())?"rejected":null;if(state==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_FRIEND_DECISION","只支持同意或拒绝");FriendRequestEntity current=friends.selectRequestForUpdate(requestId,ownerId);if(current==null)throw new ApiException(HttpStatus.NOT_FOUND,"FRIEND_REQUEST_NOT_FOUND","好友申请不存在");if(!"pending".equals(current.getState()))throw new ApiException(HttpStatus.CONFLICT,"FRIEND_REQUEST_FINISHED","好友申请已处理");if(request.getExpectedVersion()!=null&&!request.getExpectedVersion().equals(current.getVersionNo()))throw new ApiException(HttpStatus.CONFLICT,"FRIEND_REQUEST_VERSION_CONFLICT","申请状态已更新");Instant now=Instant.now();if(friends.decide(requestId,state,current.getVersionNo(),now)!=1)throw new ApiException(HttpStatus.CONFLICT,"FRIEND_REQUEST_VERSION_CONFLICT","申请状态已更新");if("accepted".equals(state))friends.activate(current.getRelationId(),now);for(FriendRequestView view:friends.selectRequests(ownerId,"received"))if(requestId.equals(view.getId()))return view;throw new ApiException(HttpStatus.NOT_FOUND,"FRIEND_REQUEST_NOT_FOUND","好友申请不存在");}
 @Transactional public void remove(String ownerId,String friendId){String low=ownerId.compareTo(friendId)<0?ownerId:friendId,high=ownerId.compareTo(friendId)<0?friendId:ownerId;FriendRelationEntity relation=friends.selectRelationForUpdate(low,high);if(relation==null||!"active".equals(relation.getState()))throw new ApiException(HttpStatus.NOT_FOUND,"FRIEND_NOT_FOUND","好友关系不存在");friends.remove(relation.getId(),Instant.now());}
 private String trim(String v){return v==null||v.trim().isEmpty()?null:v.trim();}
}
