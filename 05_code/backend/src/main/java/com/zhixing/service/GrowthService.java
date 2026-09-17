package com.zhixing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.ConfirmWeeklyActionsRequest;
import com.zhixing.dto.WeeklyActionSaveRequest;
import com.zhixing.dto.WeeklySummaryConfirmRequest;
import com.zhixing.dto.WeeklySummaryEditRequest;
import com.zhixing.entity.LearningPlanEntity;
import com.zhixing.entity.WeeklyActionEntity;
import com.zhixing.entity.WeeklyRevisionEntity;
import com.zhixing.entity.WeeklySummaryEntity;
import com.zhixing.mapper.GrowthMapper;
import com.zhixing.model.ActionPlanView;
import com.zhixing.model.ActionView;
import com.zhixing.model.GrowthDayView;
import com.zhixing.model.GrowthMetricRow;
import com.zhixing.model.GrowthOverviewView;
import com.zhixing.model.JournalHighlightView;
import com.zhixing.model.WeeklySummaryView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class GrowthService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private static final String[] WEEKDAYS={"周一","周二","周三","周四","周五","周六","周日"};
    private final GrowthMapper growth;
    private final ObjectMapper json;
    public GrowthService(GrowthMapper growth,ObjectMapper json){this.growth=growth;this.json=json;}

    public GrowthOverviewView overview(String ownerId,LocalDate requestedWeek){
        LocalDate start=week(requestedWeek==null?LocalDate.now(BUSINESS_ZONE):requestedWeek),end=start.plusDays(6),today=LocalDate.now(BUSINESS_ZONE);
        Snapshot current=snapshot(ownerId,start);
        GrowthOverviewView view=new GrowthOverviewView();
        view.setWeekStart(start);view.setWeekEnd(end);view.setWeekLabel(range(start,end));
        view.setWeekStatus(today.isBefore(start)?"未开始":today.isAfter(end)?"已结束":"本周进行中");
        view.setAccumulatedGrowthDays(growth.selectAccumulatedGrowthDays(ownerId));
        view.setWeekCompleted(current.completed);view.setReviewFeedbackCount(current.reviews);
        view.setDueReviewCount(growth.selectDueReviewCount(ownerId,today));view.setReviewBacklogCount(growth.selectReviewBacklogCount(ownerId,today));
        view.setDays(current.days);
        view.setPreviousSummary(summary(ownerId,start.minusWeeks(1),true));
        return view;
    }

    @Transactional
    public WeeklySummaryView summary(String ownerId,LocalDate requestedWeek,boolean createOnFirstOpen){
        LocalDate start=week(requestedWeek),end=start.plusDays(6);
        Snapshot live=snapshot(ownerId,start);
        WeeklySummaryEntity summary=growth.selectSummary(ownerId,start);
        if(summary==null&&createOnFirstOpen&&live.hasData)summary=create(ownerId,start,live);
        if(summary==null)return emptySummary(start,end,live);
        WeeklyRevisionEntity revision=growth.selectRevision(ownerId,summary.getCurrentRevisionId());
        if(revision==null&&createOnFirstOpen&&live.hasData){
            revision=revision(ownerId,summary,growth.selectMaxRevision(summary.getId())+1,live,defaultBody(live));
            growth.insertRevision(revision);point(ownerId,summary,revision);summary=growth.selectSummary(ownerId,start);
        }
        if(revision==null&&createOnFirstOpen)return emptySummary(start,end,live);
        if(revision==null)throw new ApiException(HttpStatus.CONFLICT,"WEEKLY_REVISION_MISSING","周总结修订不存在，请重新汇总");
        boolean stale=!Arrays.equals(summary.getSourceFingerprint(),live.fingerprint);
        if(stale&&(summary.getStale()==null||summary.getStale()==0))growth.markSummaryStale(ownerId,summary.getId(),Instant.now());
        return view(ownerId,summary,revision,stale);
    }

    @Transactional
    public WeeklySummaryView edit(String ownerId,LocalDate requestedWeek,WeeklySummaryEditRequest request){
        LocalDate start=week(requestedWeek);
        WeeklySummaryEntity summary=requireLocked(ownerId,start);
        requireVersion(summary,request==null?null:request.getExpectedVersion());
        String body=clean(request.getBody());
        if(body.isEmpty()||body.codePointCount(0,body.length())>10000)throw bad("INVALID_WEEKLY_BODY","周总结正文应为 1—10000 个字符");
        WeeklyRevisionEntity current=growth.selectRevision(ownerId,summary.getCurrentRevisionId());
        WeeklyRevisionEntity revision=copyRevision(ownerId,summary,current,body);
        growth.insertRevision(revision);
        point(ownerId,summary,revision);
        return view(ownerId,growth.selectSummary(ownerId,start),revision,false);
    }

    @Transactional
    public WeeklySummaryView confirm(String ownerId,LocalDate requestedWeek,WeeklySummaryConfirmRequest request){
        LocalDate start=week(requestedWeek);
        WeeklySummaryEntity summary=requireLocked(ownerId,start);
        requireVersion(summary,request==null?null:request.getExpectedVersion());
        if(request.getRevisionId()==null||!request.getRevisionId().equals(summary.getCurrentRevisionId()))throw bad("WEEKLY_REVISION_MISMATCH","只能确认当前周总结修订");
        WeeklyRevisionEntity revision=growth.selectRevision(ownerId,request.getRevisionId());
        if(revision==null||revision.getInvalidatedAt()!=null)throw bad("WEEKLY_REVISION_INVALID","当前周总结修订已失效");
        Instant now=Instant.now();
        growth.confirmRevision(ownerId,revision.getId(),now);
        if(growth.confirmSummary(ownerId,summary.getId(),revision.getId(),summary.getVersionNo(),now)!=1)throw conflict("WEEKLY_VERSION_CONFLICT","周总结已变化，请刷新后重试");
        revision.setConfirmedAt(now);
        return view(ownerId,growth.selectSummary(ownerId,start),revision,false);
    }

    @Transactional
    public WeeklySummaryView recalculate(String ownerId,LocalDate requestedWeek){
        LocalDate start=week(requestedWeek);
        Snapshot live=snapshot(ownerId,start);
        if(!live.hasData)return emptySummary(start,start.plusDays(6),live);
        WeeklySummaryEntity summary=growth.selectSummaryForUpdate(ownerId,start);
        if(summary==null)return view(ownerId,create(ownerId,start,live),growth.selectRevision(ownerId,growth.selectSummary(ownerId,start).getCurrentRevisionId()),false);
        if(Arrays.equals(summary.getSourceFingerprint(),live.fingerprint))return view(ownerId,summary,growth.selectRevision(ownerId,summary.getCurrentRevisionId()),false);
        WeeklyRevisionEntity revision=revision(ownerId,summary,growth.selectMaxRevision(summary.getId())+1,live,defaultBody(live));
        growth.insertRevision(revision);point(ownerId,summary,revision);
        return view(ownerId,growth.selectSummary(ownerId,start),revision,false);
    }

    public ActionPlanView actionPlan(String ownerId,LocalDate requestedWeek){
        LocalDate sourceWeek=week(requestedWeek);
        WeeklySummaryView summary=summary(ownerId,sourceWeek,true);
        if(summary.getId()==null)throw conflict("WEEKLY_SUMMARY_EMPTY","本周没有可汇总记录，暂不能安排行动");
        return actionPlan(ownerId,summary.getId(),sourceWeek);
    }

    @Transactional
    public ActionPlanView saveAction(String ownerId,LocalDate requestedWeek,WeeklyActionSaveRequest request){
        LocalDate sourceWeek=week(requestedWeek);
        WeeklySummaryEntity summary=requireLocked(ownerId,sourceWeek);
        validateAction(request,sourceWeek);
        WeeklyActionEntity action;
        if(request.getId()!=null&&!request.getId().trim().isEmpty()){
            action=growth.selectActionForUpdate(ownerId,request.getId());
            if(action==null||!summary.getId().equals(action.getSummaryId()))throw new ApiException(HttpStatus.NOT_FOUND,"ACTION_NOT_FOUND","行动不存在或无权访问");
            if(!"draft".equals(action.getState()))throw conflict("ACTION_NOT_EDITABLE","已确认行动请从今日任务中编辑");
            if(request.getExpectedVersion()==null||!request.getExpectedVersion().equals(action.getVersionNo()))throw conflict("ACTION_VERSION_CONFLICT","行动已变化，请刷新后重试");
            fill(action,request);
            if(growth.saveDraftAction(action)!=1)throw conflict("ACTION_VERSION_CONFLICT","行动已变化，请刷新后重试");
        }else{
            List<WeeklyActionEntity> all=growth.selectAllActionsForUpdate(ownerId,summary.getId());
            int active=0;for(WeeklyActionEntity item:all)if(!"cancelled".equals(item.getState()))active++;
            if(active>=3)throw bad("ACTION_LIMIT_REACHED","每周最多安排 3 项行动");
            action=reusable(all);
            if(action==null){action=new WeeklyActionEntity();action.setId(CryptoUtils.randomId());action.setOwnerId(ownerId);action.setSummaryId(summary.getId());action.setActionNo(firstFreeSlot(all));action.setState("draft");action.setVersionNo(1);fill(action,request);growth.insertAction(action);}
            else{fill(action,request);if(growth.saveDraftAction(action)!=1)throw conflict("ACTION_VERSION_CONFLICT","行动槽位已变化，请刷新后重试");}
        }
        return actionPlan(ownerId,summary.getId(),sourceWeek);
    }

    @Transactional
    public ActionPlanView cancelAction(String ownerId,LocalDate requestedWeek,String actionId,Integer expectedVersion){
        LocalDate sourceWeek=week(requestedWeek);
        WeeklySummaryEntity summary=requireLocked(ownerId,sourceWeek);
        WeeklyActionEntity action=growth.selectActionForUpdate(ownerId,actionId);
        if(action==null||!summary.getId().equals(action.getSummaryId()))throw new ApiException(HttpStatus.NOT_FOUND,"ACTION_NOT_FOUND","行动不存在或无权访问");
        if(expectedVersion==null||growth.cancelDraftAction(ownerId,actionId,expectedVersion)!=1)throw conflict("ACTION_VERSION_CONFLICT","行动已变化，请刷新后重试");
        return actionPlan(ownerId,summary.getId(),sourceWeek);
    }

    @Transactional
    public ActionPlanView confirmActions(String ownerId,LocalDate requestedWeek,ConfirmWeeklyActionsRequest request){
        LocalDate sourceWeek=week(requestedWeek);
        WeeklySummaryEntity summary=requireLocked(ownerId,sourceWeek);
        List<WeeklyActionEntity> all=growth.selectAllActionsForUpdate(ownerId,summary.getId());
        List<WeeklyActionEntity> active=new ArrayList<WeeklyActionEntity>();
        for(WeeklyActionEntity action:all)if(!"cancelled".equals(action.getState()))active.add(action);
        if(active.isEmpty())throw bad("ACTION_REQUIRED","请先新增至少一项行动");
        Map<LocalDate,Integer> minutes=new LinkedHashMap<LocalDate,Integer>();
        boolean allowTemporary=request!=null&&Boolean.TRUE.equals(request.getConfirmTemporaryDays());
        for(WeeklyActionEntity action:active){
            validateAction(action,sourceWeek);
            LearningPlanEntity plan=requirePlan(ownerId,action.getScheduledDate());
            if(!activeDay(plan,action.getScheduledDate())&&!allowTemporary)throw conflict("NON_ACTIVE_DAY_CONFIRM_REQUIRED","行动包含非学习日，请确认启用当日临时计划");
            minutes.put(action.getScheduledDate(),minutes.containsKey(action.getScheduledDate())?minutes.get(action.getScheduledDate())+action.getEstimatedMinutes():action.getEstimatedMinutes());
        }
        for(Map.Entry<LocalDate,Integer> entry:minutes.entrySet()){
            LearningPlanEntity plan=requirePlan(ownerId,entry.getKey());
            int occupied=growth.selectOtherConfirmedMinutes(ownerId,entry.getKey(),summary.getId());
            if(occupied+entry.getValue()>plan.getDailyBudgetMin())throw conflict("ACTION_BUDGET_EXCEEDED",entry.getKey()+" 的行动预计时长超过每日预算");
            String packageId=growth.selectPackageId(ownerId,entry.getKey());
            if(packageId!=null){int missing=0;for(WeeklyActionEntity action:active)if(entry.getKey().equals(action.getScheduledDate())&&growth.selectActionTaskCount(ownerId,action.getId())==0)missing+=action.getEstimatedMinutes()*60;if(missing>growth.selectPackageRemainingSeconds(packageId))throw conflict("ACTION_BUDGET_EXCEEDED",entry.getKey()+" 的现有任务已占满预算");}
        }
        Instant now=Instant.now();
        for(WeeklyActionEntity action:active)if("draft".equals(action.getState())){if(growth.confirmAction(ownerId,action.getId(),action.getVersionNo(),now)!=1)throw conflict("ACTION_VERSION_CONFLICT","行动已变化，请刷新后重试");action.setState("confirmed");action.setVersionNo(action.getVersionNo()+1);action.setConfirmedAt(now);}
        for(WeeklyActionEntity action:active)attachToExistingPackage(ownerId,action);
        return actionPlan(ownerId,summary.getId(),sourceWeek);
    }

    private WeeklySummaryEntity create(String ownerId,LocalDate start,Snapshot live){
        WeeklySummaryEntity summary=new WeeklySummaryEntity();summary.setId(CryptoUtils.randomId());summary.setOwnerId(ownerId);summary.setWeekStart(start);summary.setSourceFingerprint(live.fingerprint);summary.setStale(0);summary.setVersionNo(1);
        WeeklyRevisionEntity revision=revision(ownerId,summary,1,live,defaultBody(live));summary.setCurrentRevisionId(revision.getId());
        try{growth.insert(summary);growth.insertRevision(revision);return summary;}catch(DuplicateKeyException duplicate){WeeklySummaryEntity concurrent=growth.selectSummary(ownerId,start);if(concurrent!=null)return concurrent;throw duplicate;}
    }

    private WeeklyRevisionEntity revision(String ownerId,WeeklySummaryEntity summary,int number,Snapshot live,String body){
        WeeklyRevisionEntity revision=new WeeklyRevisionEntity();revision.setId(CryptoUtils.randomId());revision.setOwnerId(ownerId);revision.setSummaryId(summary.getId());revision.setRevisionNo(number);revision.setMetricsJson(write(live.metrics));revision.setBody(body);revision.setSourcesJson(write(live.sources));revision.setSourceFingerprint(live.fingerprint);return revision;
    }

    private WeeklyRevisionEntity copyRevision(String ownerId,WeeklySummaryEntity summary,WeeklyRevisionEntity current,String body){
        if(current==null)throw conflict("WEEKLY_REVISION_MISSING","当前周总结修订不存在");
        WeeklyRevisionEntity next=new WeeklyRevisionEntity();next.setId(CryptoUtils.randomId());next.setOwnerId(ownerId);next.setSummaryId(summary.getId());next.setRevisionNo(growth.selectMaxRevision(summary.getId())+1);next.setMetricsJson(current.getMetricsJson());next.setSourcesJson(current.getSourcesJson());next.setSourceFingerprint(current.getSourceFingerprint());next.setBody(body);return next;
    }

    private void point(String ownerId,WeeklySummaryEntity summary,WeeklyRevisionEntity revision){summary.setCurrentRevisionId(revision.getId());summary.setSourceFingerprint(revision.getSourceFingerprint());summary.setStale(0);summary.setVersionNo(summary.getVersionNo()+1);summary.setUpdatedAt(Instant.now());if(growth.updateById(summary)!=1)throw conflict("WEEKLY_VERSION_CONFLICT","周总结已变化，请刷新后重试");}

    private Snapshot snapshot(String ownerId,LocalDate start){
        LocalDate end=start.plusDays(6);List<GrowthMetricRow> rows=growth.selectDailyMetrics(ownerId,start,end);Map<LocalDate,GrowthMetricRow> indexed=new LinkedHashMap<LocalDate,GrowthMetricRow>();for(GrowthMetricRow row:rows)indexed.put(row.getBusinessDate(),row);
        List<GrowthDayView> days=new ArrayList<GrowthDayView>();int max=1,growthDays=0;for(GrowthMetricRow row:rows)max=Math.max(max,row.getPlannedCount()==null?0:row.getPlannedCount());
        for(int i=0;i<7;i++){LocalDate date=start.plusDays(i);GrowthMetricRow row=indexed.get(date);GrowthDayView day=new GrowthDayView();day.setDate(date);day.setWeekday(WEEKDAYS[i]);day.setPlannedCount(row==null?0:row.getPlannedCount());day.setDoneCount(row==null?0:row.getDoneCount());day.setHeightPercent(Math.max(0,Math.min(100,day.getDoneCount()*100/max)));if(day.getDoneCount()>0)growthDays++;days.add(day);}
        Snapshot snapshot=new Snapshot();snapshot.days=days;snapshot.completed=growth.selectCompletedCount(ownerId,start,end);snapshot.learning=growth.selectLearningCount(ownerId,start,end);snapshot.reviews=growth.selectReviewFeedbackCount(ownerId,start,end);snapshot.journalDays=growth.selectJournalDayCount(ownerId,start,end);snapshot.growthDays=Math.max(growthDays,Math.max(snapshot.reviews>0?1:0,snapshot.journalDays));
        snapshot.highlights=growth.selectJournalHighlights(ownerId,start,end);snapshot.gain=firstText(snapshot.highlights,true);snapshot.blocker=firstText(snapshot.highlights,false);snapshot.hasData=snapshot.completed>0||snapshot.reviews>0||snapshot.journalDays>0;
        snapshot.metrics=new LinkedHashMap<String,Object>();snapshot.metrics.put("completedCount",snapshot.completed);snapshot.metrics.put("learningCount",snapshot.learning);snapshot.metrics.put("reviewCount",snapshot.reviews);snapshot.metrics.put("journalDays",snapshot.journalDays);snapshot.metrics.put("growthDays",snapshot.growthDays);snapshot.metrics.put("days",days);snapshot.metrics.put("gainText",snapshot.gain);snapshot.metrics.put("blockerText",snapshot.blocker);
        List<Map<String,Object>> sources=new ArrayList<Map<String,Object>>();for(JournalHighlightView item:snapshot.highlights){Map<String,Object> source=new LinkedHashMap<String,Object>();source.put("journalId",item.getJournalId());source.put("revisionNo",item.getRevisionNo());source.put("businessDate",item.getBusinessDate());sources.add(source);}snapshot.sources=sources;snapshot.fingerprint=CryptoUtils.sha256(write(snapshot.metrics)+write(snapshot.sources));return snapshot;
    }

    private WeeklySummaryView view(String ownerId,WeeklySummaryEntity summary,WeeklyRevisionEntity revision,boolean stale){
        WeeklySummaryView view=new WeeklySummaryView();view.setId(summary.getId());view.setWeekStart(summary.getWeekStart());view.setWeekEnd(summary.getWeekStart().plusDays(6));view.setCurrentRevisionId(summary.getCurrentRevisionId());view.setConfirmedRevisionId(summary.getConfirmedRevisionId());view.setVersionNo(summary.getVersionNo());view.setRevisionNo(revision.getRevisionNo());view.setBody(revision.getBody());view.setStale(stale);view.setHasData(true);view.setHasConfirmedVersion(summary.getConfirmedRevisionId()!=null);view.setStatus(summary.getCurrentRevisionId()!=null&&summary.getCurrentRevisionId().equals(summary.getConfirmedRevisionId())?"confirmed":stale?"stale":"draft");
        Map<String,Object> metrics=readMap(revision.getMetricsJson());view.setMetrics(metrics);view.setDays(json.convertValue(metrics.get("days"),new TypeReference<List<GrowthDayView>>(){}));view.setGainText(text(metrics.get("gainText"),revision.getBody()));view.setBlockerText(text(metrics.get("blockerText"),"本周暂未记录明显阻塞。"));view.setActions(growth.selectActions(ownerId,summary.getId()));return view;
    }

    private WeeklySummaryView emptySummary(LocalDate start,LocalDate end,Snapshot live){WeeklySummaryView view=new WeeklySummaryView();view.setWeekStart(start);view.setWeekEnd(end);view.setStatus("empty");view.setHasData(false);view.setStale(false);view.setHasConfirmedVersion(false);view.setMetrics(live.metrics);view.setDays(live.days);view.setActions(Collections.<ActionView>emptyList());return view;}

    private ActionPlanView actionPlan(String ownerId,String summaryId,LocalDate sourceWeek){List<ActionView> actions=growth.selectActions(ownerId,summaryId);ActionPlanView view=new ActionPlanView();view.setSummaryId(summaryId);view.setWeekStart(sourceWeek.plusWeeks(1));view.setWeekEnd(sourceWeek.plusWeeks(1).plusDays(6));view.setMaxActions(3);view.setActions(actions);int total=0;boolean nonActive=false;for(ActionView action:actions){total+=action.getEstimatedMinutes();LearningPlanEntity plan=growth.selectEffectivePlan(ownerId,action.getScheduledDate());if(plan!=null&&!activeDay(plan,action.getScheduledDate()))nonActive=true;}LearningPlanEntity first=growth.selectEffectivePlan(ownerId,view.getWeekStart());view.setDailyBudgetMinutes(first==null?10:first.getDailyBudgetMin());view.setPlannedMinutes(total);view.setContainsNonActiveDay(nonActive);return view;}

    private void attachToExistingPackage(String ownerId,WeeklyActionEntity action){String packageId=growth.selectPackageId(ownerId,action.getScheduledDate());if(packageId==null||growth.selectActionTaskCount(ownerId,action.getId())>0)return;growth.insertActionTask(CryptoUtils.randomId(),ownerId,packageId,action.getTitle(),"action:"+action.getId(),action.getId(),action.getEstimatedMinutes()*60,growth.selectMaxTaskSort(packageId)+1);growth.refreshPackageCount(packageId);}
    private WeeklySummaryEntity requireLocked(String ownerId,LocalDate start){WeeklySummaryEntity summary=growth.selectSummaryForUpdate(ownerId,start);if(summary==null){WeeklySummaryView created=summary(ownerId,start,true);if(created.getId()==null)throw conflict("WEEKLY_SUMMARY_EMPTY","本周没有可汇总记录");summary=growth.selectSummaryForUpdate(ownerId,start);}return summary;}
    private void requireVersion(WeeklySummaryEntity summary,Integer expected){if(expected==null)throw bad("WEEKLY_VERSION_REQUIRED","缺少周总结版本，请刷新后重试");if(!expected.equals(summary.getVersionNo()))throw conflict("WEEKLY_VERSION_CONFLICT","周总结已变化，请刷新后重试");}
    private LearningPlanEntity requirePlan(String ownerId,LocalDate date){LearningPlanEntity plan=growth.selectEffectivePlan(ownerId,date);if(plan==null)throw conflict("PLAN_REQUIRED","请先保存学习计划");return plan;}
    private boolean activeDay(LearningPlanEntity plan,LocalDate date){if(plan.getIsPaused()!=null&&plan.getIsPaused()==1&&(plan.getPauseUntil()==null||!date.isAfter(plan.getPauseUntil())))return false;return (plan.getWeekdaysMask()&(1<<(date.getDayOfWeek().getValue()-1)))!=0;}
    private void validateAction(WeeklyActionSaveRequest request,LocalDate sourceWeek){if(request==null)throw bad("INVALID_ACTION","行动内容不能为空");validateAction(clean(request.getTitle()),clean(request.getNote()),request.getScheduledDate(),request.getEstimatedMinutes(),sourceWeek);}
    private void validateAction(WeeklyActionEntity action,LocalDate sourceWeek){validateAction(clean(action.getTitle()),clean(action.getNote()),action.getScheduledDate(),action.getEstimatedMinutes(),sourceWeek);}
    private void validateAction(String title,String note,LocalDate date,Integer minutes,LocalDate sourceWeek){if(title.isEmpty()||title.codePointCount(0,title.length())>100)throw bad("INVALID_ACTION_TITLE","行动标题应为 1—100 个字符");if(note.codePointCount(0,note.length())>1000)throw bad("INVALID_ACTION_NOTE","行动备注最多 1000 个字符");LocalDate min=sourceWeek.plusWeeks(1),max=min.plusDays(6);if(date==null||date.isBefore(min)||date.isAfter(max))throw bad("INVALID_ACTION_DATE","行动日期必须位于来源周的下一自然周");if(minutes==null||minutes<1||minutes>30)throw bad("INVALID_ACTION_MINUTES","预计时长应为 1—30 分钟");}
    private void fill(WeeklyActionEntity action,WeeklyActionSaveRequest request){action.setTitle(clean(request.getTitle()));action.setNote(clean(request.getNote()));action.setScheduledDate(request.getScheduledDate());action.setEstimatedMinutes(request.getEstimatedMinutes());}
    private WeeklyActionEntity reusable(List<WeeklyActionEntity> all){for(WeeklyActionEntity action:all)if("cancelled".equals(action.getState()))return action;return null;}
    private int firstFreeSlot(List<WeeklyActionEntity> all){for(int slot=1;slot<=3;slot++){boolean used=false;for(WeeklyActionEntity action:all)if(action.getActionNo()==slot){used=true;break;}if(!used)return slot;}return 3;}
    private LocalDate week(LocalDate date){LocalDate value=date==null?LocalDate.now(BUSINESS_ZONE):date;return value.with(DayOfWeek.MONDAY);}
    private String range(LocalDate start,LocalDate end){DateTimeFormatter formatter=DateTimeFormatter.ofPattern("MM.dd");return formatter.format(start)+"—"+formatter.format(end);}
    private String defaultBody(Snapshot s){if(!s.gain.isEmpty())return s.gain;return "本周完成 "+s.completed+" 项任务，完成 "+s.reviews+" 次复习，并记录了 "+s.journalDays+" 天复盘。";}
    private String firstText(List<JournalHighlightView> items,boolean learned){for(JournalHighlightView item:items){String value=clean(learned?item.getLearnedText():item.getBlockerText());if(!value.isEmpty())return value;}return "";}
    private String clean(String value){return value==null?"":value.trim();}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private Map<String,Object> readMap(String value){try{return json.readValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){throw conflict("WEEKLY_DATA_INVALID","周总结指标快照无法读取");}}
    private String text(Object value,String fallback){String result=value==null?"":String.valueOf(value).trim();return result.isEmpty()?fallback:result;}
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
    private ApiException conflict(String code,String message){return new ApiException(HttpStatus.CONFLICT,code,message);}
    private static class Snapshot{List<GrowthDayView> days;List<JournalHighlightView> highlights;List<Map<String,Object>> sources;Map<String,Object> metrics;int completed,learning,reviews,journalDays,growthDays;String gain,blocker;boolean hasData;byte[] fingerprint;}
}
