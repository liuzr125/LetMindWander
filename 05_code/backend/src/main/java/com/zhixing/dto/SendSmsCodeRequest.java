package com.zhixing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class SendSmsCodeRequest {
    @NotBlank(message = "待注册凭证不能为空")
    private String registrationTicket;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String mobile;

    public String getRegistrationTicket() { return registrationTicket; }
    public void setRegistrationTicket(String registrationTicket) { this.registrationTicket = registrationTicket; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
}
