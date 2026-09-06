package com.zhixing.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

public class CreateInviteRequest {
    @Min(value = 1, message = "至少生成 1 个邀请码")
    @Max(value = 100, message = "单次最多生成 100 个邀请码")
    private int count = 1;
    @Min(value = 1, message = "有效期至少 1 天")
    @Max(value = 30, message = "有效期最多 30 天")
    private int expiresInDays = 7;

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
    public int getExpiresInDays() { return expiresInDays; }
    public void setExpiresInDays(int expiresInDays) { this.expiresInDays = expiresInDays; }
}
