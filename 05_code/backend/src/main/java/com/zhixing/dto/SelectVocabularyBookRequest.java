package com.zhixing.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

public class SelectVocabularyBookRequest {
    @NotBlank private String bookId;
    @Min(1) @Max(255) private Integer dailyNewLimit;

    public String getBookId(){return bookId;} public void setBookId(String v){bookId=v;}
    public Integer getDailyNewLimit(){return dailyNewLimit;} public void setDailyNewLimit(Integer v){dailyNewLimit=v;}
}
