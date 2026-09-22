package com.zhixing.service;

import com.zhixing.common.ApiException;
import com.zhixing.mapper.AdminVocabularyMapper;
import com.zhixing.model.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AdminVocabularyService {
    private final AdminVocabularyMapper words;
    private final ContentService contents;
    private final AliyunTtsService tts;
    public AdminVocabularyService(AdminVocabularyMapper words,ContentService contents,AliyunTtsService tts){this.words=words;this.contents=contents;this.tts=tts;}
    public List<AdminVocabularyBookView> books(){return words.selectBooks();}
    public AdminVocabularyWordPageView page(String bookId,Integer rawPage,Integer rawPageSize,String rawKeyword,String rawStage){
        int page=rawPage==null?1:rawPage,pageSize=rawPageSize==null?20:rawPageSize;
        if(page<1||pageSize<1||pageSize>100)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE","页码从 1 开始，每页可显示 1 至 100 个单词");
        AdminVocabularyBookView book=words.selectBook(bookId);if(book==null)throw new ApiException(HttpStatus.NOT_FOUND,"VOCABULARY_BOOK_NOT_FOUND","词书不存在或已停用");
        String keyword=rawKeyword==null?"":rawKeyword.trim();if(keyword.length()>80)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_KEYWORD","搜索词最多 80 个字符");
        String stage=rawStage==null?"":rawStage.trim();if(stage.length()>32)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_STAGE","筛选学段不合法");
        int total=words.countWords(bookId,keyword,stage),totalPages=total==0?0:(total+pageSize-1)/pageSize;
        AdminVocabularyWordPageView result=new AdminVocabularyWordPageView();result.setBook(book);result.setTotal(total);result.setPage(page);result.setPageSize(pageSize);result.setTotalPages(totalPages);result.setKeyword(keyword);result.setStage(stage);result.setItems(words.selectWords(bookId,keyword,stage,(page-1)*pageSize,pageSize));return result;
    }
    public ContentDetailView detail(String adminPrincipalId,String contentId){ContentDetailView detail=contents.get(adminPrincipalId,contentId);if(!"word".equals(detail.getContentType()))throw new ApiException(HttpStatus.NOT_FOUND,"WORD_NOT_FOUND","单词不存在或未发布");return detail;}
    public java.util.Map<String,Object> speech(String adminPrincipalId,String contentId){detail(adminPrincipalId,contentId);return tts.speech(adminPrincipalId,contentId);}
}
