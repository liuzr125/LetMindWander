package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.dto.UpdateActionRequest;
import com.zhixing.mapper.ActionMapper;
import com.zhixing.model.ActionView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActionService {
    private final ActionMapper actions;
    public ActionService(ActionMapper actions){this.actions=actions;}
    public ActionView get(String ownerId,String id){ActionView view=actions.selectOwned(ownerId,id);if(view==null)throw new ApiException(HttpStatus.NOT_FOUND,"ACTION_NOT_FOUND","行动不存在或无权访问");return view;}
    @Transactional public ActionView update(String ownerId,String id,UpdateActionRequest request){ActionView current=get(ownerId,id);if(!"confirmed".equals(current.getState()))throw new ApiException(HttpStatus.CONFLICT,"ACTION_NOT_EDITABLE","只能编辑已确认且未取消的行动");if(request==null||request.getExpectedVersion()==null)throw bad("ACTION_VERSION_REQUIRED","缺少行动版本，请刷新后重试");String title=clean(request.getTitle());String note=clean(request.getNote());Integer minutes=request.getEstimatedMinutes();if(title.isEmpty()||title.codePointCount(0,title.length())>100)throw bad("INVALID_ACTION_TITLE","行动标题应为 1—100 个字符");if(note.codePointCount(0,note.length())>1000)throw bad("INVALID_ACTION_NOTE","行动备注最多 1000 个字符");if(minutes==null||minutes<1||minutes>30)throw bad("INVALID_ACTION_MINUTES","预计时长应为 1—30 分钟");if(actions.countOverBudgetPackages(ownerId,id,minutes)>0)throw new ApiException(HttpStatus.CONFLICT,"ACTION_BUDGET_EXCEEDED","调整后将超过目标日预算，请先调整当日计划");if(actions.update(ownerId,id,title,note,minutes,request.getExpectedVersion())!=1)throw new ApiException(HttpStatus.CONFLICT,"ACTION_VERSION_CONFLICT","行动已变更，请刷新后重试");actions.updatePendingTaskSnapshots(ownerId,id,title,minutes);return get(ownerId,id);}
    private String clean(String value){return value==null?"":value.trim();}
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}
}
