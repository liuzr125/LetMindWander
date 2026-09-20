package com.zhixing.service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AdminPrincipal {
    private final String accountId, username, roleCode, roleName;
    private final Set<String> menuCodes;
    public AdminPrincipal(String accountId, String username, String roleCode, String roleName, Set<String> menuCodes) {
        this.accountId=accountId; this.username=username; this.roleCode=roleCode; this.roleName=roleName;
        this.menuCodes=Collections.unmodifiableSet(new LinkedHashSet<String>(menuCodes));
    }
    public String getAccountId(){return accountId;} public String getUsername(){return username;}
    public String getRoleCode(){return roleCode;} public String getRoleName(){return roleName;}
    public Set<String> getMenuCodes(){return menuCodes;} public boolean hasMenu(String code){return menuCodes.contains(code);}
}
