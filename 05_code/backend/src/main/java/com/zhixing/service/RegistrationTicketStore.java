package com.zhixing.service;

import com.zhixing.model.RegistrationContext;

public interface RegistrationTicketStore {
    String create(RegistrationContext context);
    RegistrationContext get(String ticket);
    void save(String ticket, RegistrationContext context);
}
