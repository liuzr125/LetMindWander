package com.zhixing.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.*;
import com.zhixing.entity.KnowledgeItemEntity;
import com.zhixing.mapper.KnowledgeMapper;
import com.zhixing.model.KnowledgeDetailView;
import com.zhixing.model.KnowledgeFriendView;
import com.zhixing.model.KnowledgeListItemView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class KnowledgeService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final Set<String> ITEM_TYPES=new HashSet<String>(Arrays.asList("note","problem"));
    private static final Set<String> VISIBILITIES=new HashSet<String>(Arrays.asList("private","friends","selected"));
    private static final Set<String> VERIFICATION_STATES=new HashSet<String>(Arrays.asList("unverified","verified","partial","invalid"));
    private final KnowledgeMapper knowledge;
    private final ObjectMapper json;

    public KnowledgeService(KnowledgeMapper knowledge,ObjectMapper json){this.knowledge=knowledge;this.json=json;}

    public List<KnowledgeListItemView> list(String ownerId,String type,String query,String learningStatus,String verificationStatus,Integer limit){
        int safeLimit=limit==null?20:Math.max(1,Math.min(limit,50));
        List<KnowledgeListItemView> result=knowledge.selectPage(ownerId,clean(type),clean(query),clean(learningStatus),clean(verificationStatus),safeLimit);
        for(KnowledgeListItemView item:result)item.setTags(knowledge.selectTags(ownerId,item.getId()));
        return result;
    }

    public List<String> tags(String ownerId){return knowledge.selectAllTags(ownerId);}
    public List<KnowledgeFriendView> friends(String ownerId){return knowledge.selectFriends(ownerId);}

    public KnowledgeDetailView get(String ownerId,String id){
        KnowledgeDetailView view=knowledge.selectDetail(ownerId,id);
        if(view==null)throw notFound();
        view.setTags(knowledge.selectTags(ownerId,id));
        view.setSelectedFriendIds(knowledge.selectSelectedFriendIds(id));
        String problemJson=knowledge.selectProblemJson(ownerId,id);
        if(problemJson!=null&&!problemJson.trim().isEmpty())view.setProblem(readProblem(problemJson));
        return view;
    }

    @Transactional
    public KnowledgeDetailView create(String ownerId,KnowledgeSaveRequest request){
        Prepared prepared=prepare(ownerId,request);
        KnowledgeItemEntity entity=new KnowledgeItemEntity();
        entity.setId(CryptoUtils.randomId());entity.setOwnerId(ownerId);entity.setItemType(prepared.itemType);
        entity.setTitle(prepared.title);entity.setBody(prepared.body);entity.setProblemJson(prepared.problemJson);
        entity.setSearchText(prepared.searchText);entity.setLearningStatus("unlearned");entity.setVerificationStatus("unverified");
        entity.setVersionNo(1);entity.setVisibility(prepared.visibility);entity.setState(prepared.state);
        entity.setNoteParentId(prepared.noteParentId);entity.setCreatedAt(Instant.now());entity.setUpdatedAt(Instant.now());
        knowledge.insert(entity);
        replaceTags(ownerId,entity.getId(),prepared.tags);
        replaceShares(ownerId,entity.getId(),prepared.visibility,prepared.friendIds);
        insertRevision(ownerId,entity.getId(),1,"create",snapshot(entity,prepared.tags,prepared.friendIds));
        return get(ownerId,entity.getId());
    }

    @Transactional
    public KnowledgeDetailView update(String ownerId,String id,KnowledgeSaveRequest request){
        KnowledgeItemEntity current=requiredForUpdate(ownerId,id);
        int expected=request==null||request.getExpectedVersion()==null?-1:request.getExpectedVersion();
        if(expected!=current.getVersionNo())throw conflict();
        Prepared prepared=prepare(ownerId,request);
        if(!current.getItemType().equals(prepared.itemType))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"KNOWLEDGE_TYPE_IMMUTABLE","已保存知识不能更改类型，请新建另一条知识");
        if(knowledge.updateItem(ownerId,id,prepared.itemType,prepared.title,prepared.body,prepared.problemJson,prepared.searchText,
                prepared.visibility,prepared.state,prepared.noteParentId,expected)==0)throw conflict();
        replaceTags(ownerId,id,prepared.tags);
        replaceShares(ownerId,id,prepared.visibility,prepared.friendIds);
        KnowledgeDetailView changed=get(ownerId,id);
        insertRevision(ownerId,id,changed.getVersionNo(),"edit",snapshot(changed));
        return changed;
    }

    @Transactional
    public KnowledgeDetailView verify(String ownerId,String id,KnowledgeVerificationRequest request){
        KnowledgeItemEntity current=requiredForUpdate(ownerId,id);
        if(!"problem".equals(current.getItemType()))throw new ApiException(HttpStatus.BAD_REQUEST,"NOT_PROBLEM_CARD","只有问题卡可以标记验证状态");
        int expected=request==null||request.getExpectedVersion()==null?-1:request.getExpectedVersion();
        if(expected!=current.getVersionNo())throw conflict();
        String status=clean(request.getStatus());
        if(!VERIFICATION_STATES.contains(status))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VERIFICATION_STATUS","验证状态不正确");
        String note=clean(request.getNote());
        if(("verified".equals(status)||"partial".equals(status))&&note.isEmpty())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"VERIFICATION_NOTE_REQUIRED","已验证或部分验证必须填写验证说明");
        if(length(note)>2000)throw new ApiException(HttpStatus.BAD_REQUEST,"VERIFICATION_NOTE_TOO_LONG","验证说明最多 2000 个字符");
        KnowledgeProblemFields problem=readProblem(current.getProblemJson());
        problem.setVerification(note);
        String problemJson=write(problem);
        List<String> tags=knowledge.selectTags(ownerId,id);
        String searchText=searchText(current.getTitle(),current.getBody(),problem,tags);
        LocalDate date=("verified".equals(status)||"partial".equals(status))?LocalDate.now(BUSINESS_ZONE):null;
        if(knowledge.updateVerification(ownerId,id,problemJson,searchText,status,date,expected)==0)throw conflict();
        KnowledgeDetailView changed=get(ownerId,id);
        insertRevision(ownerId,id,changed.getVersionNo(),"verify",snapshot(changed));
        return changed;
    }

    @Transactional
    public KnowledgeDetailView visibility(String ownerId,String id,KnowledgeVisibilityRequest request){
        KnowledgeItemEntity current=requiredForUpdate(ownerId,id);
        int expected=request==null||request.getExpectedVersion()==null?-1:request.getExpectedVersion();
        if(expected!=current.getVersionNo())throw conflict();
        String visibility=visibility(request.getVisibility());
        List<KnowledgeFriendView> friends=validateFriends(ownerId,visibility,request.getSelectedFriendIds());
        if(knowledge.updateVisibility(ownerId,id,visibility,expected)==0)throw conflict();
        replaceShareLinks(id,visibility,friends);
        KnowledgeDetailView changed=get(ownerId,id);
        insertRevision(ownerId,id,changed.getVersionNo(),"edit",snapshot(changed));
        return changed;
    }

    @Transactional
    public KnowledgeDetailView review(String ownerId,String id,KnowledgeReviewRequest request){
        KnowledgeItemEntity current=requiredForUpdate(ownerId,id);
        if(!"active".equals(current.getState()))throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"DRAFT_CANNOT_REVIEW","草稿保存为正式知识后才能加入复习");
        boolean active=request==null||!Boolean.FALSE.equals(request.getActive());
        LocalDate due=LocalDate.now(BUSINESS_ZONE).plusDays(1);
        String state=active?"active":"paused";
        if(knowledge.updateReview(ownerId,id,state,due)==0&&active){
            try{knowledge.insertReview(CryptoUtils.randomId(),ownerId,id,due);}
            catch(DuplicateKeyException ignored){knowledge.updateReview(ownerId,id,state,due);}
        }
        return get(ownerId,id);
    }

    @Transactional
    public void delete(String ownerId,String id,Integer expectedVersion){
        KnowledgeItemEntity current=requiredForUpdate(ownerId,id);
        int expected=expectedVersion==null?-1:expectedVersion;
        if(expected!=current.getVersionNo()||knowledge.softDelete(ownerId,id,expected)==0)throw conflict();
        knowledge.updateReview(ownerId,id,"paused",null);
        knowledge.deleteShareRules(id);
    }

    private Prepared prepare(String ownerId,KnowledgeSaveRequest request){
        if(request==null)throw new ApiException(HttpStatus.BAD_REQUEST,"KNOWLEDGE_REQUIRED","请填写知识内容");
        Prepared p=new Prepared();
        p.itemType=clean(request.getItemType());
        if(!ITEM_TYPES.contains(p.itemType))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_KNOWLEDGE_TYPE","知识类型仅支持笔记或问题卡");
        p.state="draft".equals(request.getState())?"draft":"active";
        p.title=clean(request.getTitle());
        if(length(p.title)>100)throw new ApiException(HttpStatus.BAD_REQUEST,"KNOWLEDGE_TITLE_TOO_LONG","标题最多 100 个字符");
        p.tags=normalizeTags(request.getTags());
        p.visibility=visibility(request.getVisibility());
        List<KnowledgeFriendView> friends=validateFriends(ownerId,p.visibility,request.getSelectedFriendIds());
        p.friendIds=new ArrayList<String>();for(KnowledgeFriendView friend:friends)p.friendIds.add(friend.getId());
        p.noteParentId=cleanToNull(request.getNoteParentId());
        if(p.noteParentId!=null&&knowledge.selectOwnedForUpdate(ownerId,p.noteParentId)==null)
            throw new ApiException(HttpStatus.NOT_FOUND,"NOTE_SOURCE_NOT_FOUND","原文知识不存在或已不可访问");
        if("problem".equals(p.itemType)){
            KnowledgeProblemFields problem=request.getProblem()==null?new KnowledgeProblemFields():request.getProblem();
            normalize(problem);int total=problemLength(problem);
            if(total>10000)throw new ApiException(HttpStatus.BAD_REQUEST,"KNOWLEDGE_CONTENT_TOO_LONG","问题卡字段合计最多 10000 个字符");
            p.body=problemBody(problem);p.problemJson=write(problem);p.problem=problem;
        }else{
            p.body=clean(request.getBody());p.problemJson=null;
            if(length(p.body)>10000)throw new ApiException(HttpStatus.BAD_REQUEST,"KNOWLEDGE_CONTENT_TOO_LONG","内容最多 10000 个字符");
        }
        if("active".equals(p.state)&&(p.title.isEmpty()||p.body.isEmpty()))
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"KNOWLEDGE_CONTENT_REQUIRED","标题和内容填写完整后才能保存知识");
        p.searchText=searchText(p.title,p.body,p.problem,p.tags);
        return p;
    }

    private List<KnowledgeFriendView> validateFriends(String ownerId,String visibility,List<String> requested){
        LinkedHashSet<String> ids=new LinkedHashSet<String>();
        if(requested!=null)for(String id:requested){String clean=clean(id);if(!clean.isEmpty())ids.add(clean);}
        if("selected".equals(visibility)&&ids.isEmpty())
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"SHARE_FRIEND_REQUIRED","指定好友范围至少选择一位当前好友");
        List<KnowledgeFriendView> result=new ArrayList<KnowledgeFriendView>();
        if(!"selected".equals(visibility))return result;
        for(String id:ids){KnowledgeFriendView friend=knowledge.selectFriend(ownerId,id);if(friend==null)
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"FRIEND_NOT_AVAILABLE","所选好友关系已失效，请刷新后重试");result.add(friend);}
        return result;
    }

    private void replaceTags(String ownerId,String id,List<String> tags){
        knowledge.deleteTags(ownerId,id);
        for(String tag:tags)knowledge.insertTag(CryptoUtils.randomId(),ownerId,id,tag);
    }
    private void replaceShares(String ownerId,String id,String visibility,List<String> friendIds){
        List<KnowledgeFriendView> links=validateFriends(ownerId,visibility,friendIds);
        replaceShareLinks(id,visibility,links);
    }
    private void replaceShareLinks(String id,String visibility,List<KnowledgeFriendView> friends){
        knowledge.deleteShareRules(id);
        if(!"selected".equals(visibility))return;
        for(KnowledgeFriendView friend:friends)knowledge.insertShareRule(CryptoUtils.randomId(),id,friend.getId(),friend.getRelationId(),friend.getRelationGeneration());
    }

    private KnowledgeItemEntity requiredForUpdate(String ownerId,String id){KnowledgeItemEntity item=knowledge.selectOwnedForUpdate(ownerId,id);if(item==null)throw notFound();return item;}
    private ApiException notFound(){return new ApiException(HttpStatus.NOT_FOUND,"KNOWLEDGE_NOT_FOUND","知识不存在、已删除或无权访问");}
    private ApiException conflict(){return new ApiException(HttpStatus.CONFLICT,"KNOWLEDGE_VERSION_CONFLICT","知识已在其他位置更新，请保留当前输入并重新加载");}
    private String visibility(String value){String v=clean(value);if(v.isEmpty())v="private";if(!VISIBILITIES.contains(v))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_VISIBILITY","可见范围不正确");return v;}
    private List<String> normalizeTags(List<String> values){LinkedHashMap<String,String> unique=new LinkedHashMap<String,String>();if(values!=null)for(String value:values){String tag=clean(value);if(tag.isEmpty())continue;if(length(tag)>20)throw new ApiException(HttpStatus.BAD_REQUEST,"TAG_TOO_LONG","每个标签最多 20 个字符");unique.put(tag.toLowerCase(Locale.ROOT),tag);}if(unique.size()>10)throw new ApiException(HttpStatus.BAD_REQUEST,"TOO_MANY_TAGS","每条知识最多 10 个标签");return new ArrayList<String>(unique.values());}
    private void normalize(KnowledgeProblemFields p){p.setPhenomenon(clean(p.getPhenomenon()));p.setEnvironment(clean(p.getEnvironment()));p.setCause(clean(p.getCause()));p.setSolution(clean(p.getSolution()));p.setVerification(clean(p.getVerification()));String[] values={p.getPhenomenon(),p.getEnvironment(),p.getCause(),p.getSolution(),p.getVerification()};for(String value:values)if(length(value)>2000)throw new ApiException(HttpStatus.BAD_REQUEST,"PROBLEM_FIELD_TOO_LONG","问题卡每项最多 2000 个字符");}
    private int problemLength(KnowledgeProblemFields p){return length(p.getPhenomenon())+length(p.getEnvironment())+length(p.getCause())+length(p.getSolution())+length(p.getVerification());}
    private String problemBody(KnowledgeProblemFields p){StringBuilder b=new StringBuilder();append(b,"现象",p.getPhenomenon());append(b,"环境 / 版本",p.getEnvironment());append(b,"原因",p.getCause());append(b,"处理步骤",p.getSolution());append(b,"验证说明",p.getVerification());return b.toString().trim();}
    private void append(StringBuilder b,String label,String value){if(value!=null&&!value.isEmpty()){if(b.length()>0)b.append('\n');b.append(label).append("：").append(value);}}
    private String searchText(String title,String body,KnowledgeProblemFields problem,List<String> tags){StringBuilder b=new StringBuilder();b.append(clean(title)).append('\n').append(clean(body));if(problem!=null)b.append('\n').append(problemBody(problem));for(String tag:tags)b.append('\n').append(tag);return b.toString().trim();}
    private KnowledgeProblemFields readProblem(String value){if(value==null||value.trim().isEmpty())return new KnowledgeProblemFields();try{return json.readValue(value,KnowledgeProblemFields.class);}catch(Exception e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"KNOWLEDGE_DATA_INVALID","问题卡数据格式异常");}}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"KNOWLEDGE_SERIALIZE_FAILED","知识保存失败");}}
    private void insertRevision(String ownerId,String id,int revision,String kind,String snapshot){knowledge.insertRevision(CryptoUtils.randomId(),ownerId,id,revision,snapshot,kind);}
    private String snapshot(KnowledgeItemEntity item,List<String> tags,List<String> friendIds){Map<String,Object> m=new LinkedHashMap<String,Object>();m.put("id",item.getId());m.put("itemType",item.getItemType());m.put("title",item.getTitle());m.put("body",item.getBody());m.put("problem",item.getProblemJson()==null?null:readProblem(item.getProblemJson()));m.put("learningStatus",item.getLearningStatus());m.put("verificationStatus",item.getVerificationStatus());m.put("visibility",item.getVisibility());m.put("state",item.getState());m.put("noteParentId",item.getNoteParentId());m.put("tags",tags);m.put("selectedFriendIds",friendIds);return write(m);}
    private String snapshot(KnowledgeDetailView item){Map<String,Object> m=new LinkedHashMap<String,Object>();m.put("id",item.getId());m.put("itemType",item.getItemType());m.put("title",item.getTitle());m.put("body",item.getBody());m.put("problem",item.getProblem());m.put("learningStatus",item.getLearningStatus());m.put("verificationStatus",item.getVerificationStatus());m.put("visibility",item.getVisibility());m.put("state",item.getState());m.put("noteParentId",item.getNoteParentId());m.put("tags",item.getTags());m.put("selectedFriendIds",item.getSelectedFriendIds());return write(m);}
    private int length(String v){return v==null?0:v.codePointCount(0,v.length());}
    private String clean(String v){return v==null?"":v.trim();}
    private String cleanToNull(String v){String c=clean(v);return c.isEmpty()?null:c;}
    private static class Prepared{String itemType,title,body,problemJson,searchText,visibility,state,noteParentId;KnowledgeProblemFields problem;List<String> tags,friendIds;}
}
