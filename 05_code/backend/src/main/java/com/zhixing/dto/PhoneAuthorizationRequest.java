package com.zhixing.dto;

import javax.validation.constraints.NotBlank;

public class PhoneAuthorizationRequest {
    @NotBlank(message = "待注册凭证不能为空")
    private String registrationTicket;
    @NotBlank(message = "请先授权手机号")
    private String code;

    public String getRegistrationTicket() { return registrationTicket; }
    public void setRegistrationTicket(String registrationTicket) { this.registrationTicket = registrationTicket; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
