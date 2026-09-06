package com.zhixing.dto;

import javax.validation.constraints.Min;

public class UpdateAdmissionRequest {
    @Min(value = 1, message = "名额上限至少为 1")
    private int invitedLimit;

    public int getInvitedLimit() { return invitedLimit; }
    public void setInvitedLimit(int invitedLimit) { this.invitedLimit = invitedLimit; }
}
