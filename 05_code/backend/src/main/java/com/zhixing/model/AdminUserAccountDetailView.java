package com.zhixing.model;

/** Only returned after an explicit, authorized account detail request. */
public class AdminUserAccountDetailView extends AdminUserView {
  private String mobile;

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String value) {
    this.mobile = value;
  }
}
