package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.JournalSaveRequest;
import com.zhixing.entity.DailyJournalEntity;
import com.zhixing.entity.JournalRevisionEntity;
import com.zhixing.mapper.JournalMapper;
import com.zhixing.model.JournalView;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
public class JournalService {
    private static final ZoneId BUSINESS_ZONE=ZoneId.of("Asia/Shanghai");
    private final JournalMapper journals; private final DailyTaskService dailyTasks;
    public JournalService(JournalMapper journals,DailyTaskService dailyTasks){this.journals=journals;this.dailyTasks=dailyTasks;}
    public JournalView get(String ownerId,LocalDate date){checkDate(date);JournalView view=journals.selectView(ownerId,date);if(view!=null)return view;view=new JournalView();view.setBusinessDate(date);view.setState("draft");view.setVersionNo(0);view.setDoneText("");view.setBlockerText("");view.setLearnedText("");view.setNextStepText("");return view;}
    public List<JournalView> history(String ownerId){return journals.selectHistory(ownerId);}
    @Transactional public JournalView save(String ownerId,LocalDate date,JournalSaveRequest request,boolean submit){checkDate(date);validate(request,submit);DailyJournalEntity journal=journals.selectForUpdate(ownerId,date);if(journal==null){journal=new DailyJournalEntity();journal.setId(CryptoUtils.randomId());journal.setOwnerId(ownerId);journal.setBusinessDate(date);journal.setVersionNo(0);journal.setState("draft");try{journals.insertJournal(journal);}catch(DuplicateKeyException e){journal=journals.selectForUpdate(ownerId,date);}}int expected=request.getExpectedVersion()==null?0:request.getExpectedVersion();if(!Integer.valueOf(expected).equals(journal.getVersionNo()))throw new ApiException(HttpStatus.CONFLICT,"JOURNAL_VERSION_CONFLICT","服务器已有更新，请先保留本地稿并重新加载");int next=journal.getVersionNo()+1;JournalRevisionEntity revision=new JournalRevisionEntity();revision.setId(CryptoUtils.randomId());revision.setOwnerId(ownerId);revision.setJournalId(journal.getId());revision.setRevisionNo(next);revision.setDoneText(clean(request.getDoneText()));revision.setBlockerText(clean(request.getBlockerText()));revision.setLearnedText(clean(request.getLearnedText()));revision.setNextStepText(clean(request.getNextStepText()));revision.setSaveKind(submit?"submit":"manual");revision.setContentHash(CryptoUtils.sha256(join(revision)));journals.insertRevision(revision);Instant now=Instant.now();journals.updatePointers(journal.getId(),revision.getId(),next,submit,now);if(submit)dailyTasks.completeJournalTask(ownerId,date);return get(ownerId,date);}
    private void checkDate(LocalDate date){if(date==null||date.isAfter(LocalDate.now(BUSINESS_ZONE)))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_JOURNAL_DATE","复盘日期不能晚于今天");}
    private void validate(JournalSaveRequest r,boolean submit){if(r==null)throw new ApiException(HttpStatus.BAD_REQUEST,"JOURNAL_REQUIRED","请填写复盘内容");String[] values={clean(r.getDoneText()),clean(r.getBlockerText()),clean(r.getLearnedText()),clean(r.getNextStepText())};int total=0;for(String v:values){int n=v.codePointCount(0,v.length());if(n>3000)throw new ApiException(HttpStatus.BAD_REQUEST,"JOURNAL_FIELD_TOO_LONG","每项最多 3000 个字符");total+=n;}if(total>10000)throw new ApiException(HttpStatus.BAD_REQUEST,"JOURNAL_TOO_LONG","复盘内容合计最多 10000 个字符");if(submit&&total==0)throw new ApiException(HttpStatus.BAD_REQUEST,"JOURNAL_EMPTY","至少填写一项后再提交");}
    private String clean(String v){return v==null?"":v.trim();}
    private String join(JournalRevisionEntity r){return r.getDoneText()+"\n"+r.getBlockerText()+"\n"+r.getLearnedText()+"\n"+r.getNextStepText();}
}
