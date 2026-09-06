package com.zhixing.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** MyBatis-Plus mapping for invite_code; custom admission SQL stays in mapper methods. */
@TableName("invite_code")
public class InviteCodeEntity {
    @TableId
    private String id;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
}
