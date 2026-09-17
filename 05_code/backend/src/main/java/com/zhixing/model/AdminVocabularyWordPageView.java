package com.zhixing.model;

import java.util.Collections;
import java.util.List;

public class AdminVocabularyWordPageView {
    private AdminVocabularyBookView book;
    private Integer total,page,pageSize,totalPages;
    private String keyword;
    private List<AdminVocabularyWordView> items=Collections.emptyList();
    public AdminVocabularyBookView getBook(){return book;} public void setBook(AdminVocabularyBookView v){book=v;}
    public Integer getTotal(){return total;} public void setTotal(Integer v){total=v;}
    public Integer getPage(){return page;} public void setPage(Integer v){page=v;}
    public Integer getPageSize(){return pageSize;} public void setPageSize(Integer v){pageSize=v;}
    public Integer getTotalPages(){return totalPages;} public void setTotalPages(Integer v){totalPages=v;}
    public String getKeyword(){return keyword;} public void setKeyword(String v){keyword=v;}
    public List<AdminVocabularyWordView> getItems(){return items;} public void setItems(List<AdminVocabularyWordView> v){items=v;}
}
