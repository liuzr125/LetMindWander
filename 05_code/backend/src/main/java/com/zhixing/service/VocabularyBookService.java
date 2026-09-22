package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.common.CryptoUtils;
import com.zhixing.dto.SelectVocabularyBookRequest;
import com.zhixing.mapper.VocabularyBookMapper;
import com.zhixing.mapper.LearningPlanMapper;
import com.zhixing.entity.LearningPlanEntity;
import com.zhixing.model.UserVocabularyBookRow;
import com.zhixing.model.VocabularyBookView;
import com.zhixing.model.VocabularyBookProgressItemView;
import com.zhixing.model.VocabularyBookProgressView;
import com.zhixing.model.VocabularyBookResetView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
public class VocabularyBookService {
    private final VocabularyBookMapper books;
    private final LearningPlanMapper plans;
    private final BookStudyService bookStudy;
    public VocabularyBookService(VocabularyBookMapper books,LearningPlanMapper plans,BookStudyService bookStudy){this.books=books;this.plans=plans;this.bookStudy=bookStudy;}

    public List<VocabularyBookView> list(String ownerId){return books.selectAvailable(ownerId);}
    public VocabularyBookView current(String ownerId){return books.selectCurrent(ownerId);}

    public VocabularyBookProgressView progress(String ownerId,String rawStatus,Integer rawPage,Integer rawPageSize){
        String status=rawStatus==null?"all":rawStatus.trim().toLowerCase(Locale.ROOT);
        if(!"all".equals(status)&&!"learned".equals(status)&&!"remaining".equals(status))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PROGRESS_STATUS","进度筛选只支持 all、learned 或 remaining");
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>50)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页 1 至 50 条");
        VocabularyBookProgressView result=books.selectCurrentProgress(ownerId);
        if(result==null)throw new ApiException(HttpStatus.CONFLICT,"VOCABULARY_BOOK_REQUIRED","请先选择学习词书");
        int total=result.getTotalCount()==null?0:result.getTotalCount();
        int learned=result.getLearnedCount()==null?0:result.getLearnedCount();
        result.setTotalCount(total);result.setLearnedCount(learned);result.setRemainingCount(Math.max(0,total-learned));
        LearningPlanEntity plan=plans.selectEffectiveOn(ownerId,LocalDate.now(ZoneId.of("Asia/Shanghai")));if(plan==null)plan=plans.selectLatest(ownerId);
        int dailyNewCount=plan==null||plan.getNewWordCount()==null?0:Math.max(0,plan.getNewWordCount());
        result.setDailyNewCount(dailyNewCount);result.setEstimatedRemainingDays(dailyNewCount==0?null:(int)Math.ceil(result.getRemainingCount()/(double)dailyNewCount));
        result.setCompletionRate(total==0?0.0:Math.round(learned*1000.0/total)/10.0);
        result.setStatus(status);result.setPage(page);result.setPageSize(pageSize);
        List<VocabularyBookProgressItemView> items=books.selectProgressItems(ownerId,result.getBookId(),status,(page-1)*pageSize,pageSize+1);
        result.setHasMore(items.size()>pageSize);if(items.size()>pageSize)items=items.subList(0,pageSize);result.setItems(items);
        return result;
    }

    @Transactional
    public VocabularyBookView select(String ownerId,SelectVocabularyBookRequest request){
        String bookId=request.getBookId().trim();
        if(books.countActiveBook(bookId)==0)throw new ApiException(HttpStatus.NOT_FOUND,"VOCABULARY_BOOK_NOT_FOUND","词书不存在或已停用");
        if(books.countAvailableWords(bookId)==0)throw new ApiException(HttpStatus.CONFLICT,"VOCABULARY_BOOK_EMPTY","该词书内容正在准备中");
        LearningPlanEntity latestPlan=plans.selectLatest(ownerId);
        int planDailyCount=latestPlan==null||latestPlan.getNewWordCount()==null?1:latestPlan.getNewWordCount();
        int dailyLimit=request.getDailyNewLimit()==null?Math.max(1,Math.min(255,planDailyCount)):request.getDailyNewLimit();
        Instant now=Instant.now();
        books.lockOwner(ownerId);
        books.pauseOtherBooks(ownerId,bookId,now);
        UserVocabularyBookRow selected=books.selectOwnedForUpdate(ownerId,bookId);
        if(selected==null)books.insertSelection(CryptoUtils.randomId(),ownerId,bookId,dailyLimit,now);
        else books.activateSelection(ownerId,bookId,dailyLimit,now);
        // 学习记录按轮次：切走时冻结旧轮，切回同一本书会新开一轮并带入上一轮的已学/未学
        bookStudy.openRound(ownerId,bookId,now);
        return books.selectCurrent(ownerId);
    }

    /**
     * 「重新学习」：把当前词书里已学（understood / mastered）的词条划回未学，并取消这些词条的到期复习排期。
     * 只改学习状态与熟悉度，不动学习记录本身（首次学会时间等历史保留）；今日已排的任务也不会被重排。
     * 注意词条学习状态是「词」维度的：同一个词同时属于多本词书时，各本书的进度会一起变化。
     */
    @Transactional
    public VocabularyBookResetView resetLearned(String ownerId){
        String bookId=books.selectActiveBookId(ownerId);
        if(bookId==null)throw new ApiException(HttpStatus.CONFLICT,"VOCABULARY_BOOK_REQUIRED","请先选择学习词书");
        VocabularyBookView current=books.selectCurrent(ownerId);
        Instant now=Instant.now();
        books.lockOwner(ownerId);
        int learned=books.countLearnedInBook(ownerId,bookId);
        int paused=books.pauseReviewsInBook(ownerId,bookId,now);
        books.resetLearnedInBook(ownerId,bookId,now);
        // 有实际重置才留一条「重新学习」记录（管理端详情可见），空点不产生噪音
        if(learned>0)bookStudy.recordReset(ownerId,bookId,learned,paused,now);
        VocabularyBookResetView out=new VocabularyBookResetView();
        out.setBookId(bookId);out.setBookName(current==null?null:current.getBookName());
        out.setResetCount(learned);out.setPausedReviewCount(paused);
        int total=books.countAvailableWords(bookId);
        out.setLearnedCount(Math.max(0,books.countLearnedInBook(ownerId,bookId)));out.setTotalCount(total);
        out.setRemainingCount(Math.max(0,total-out.getLearnedCount()));
        return out;
    }
}
