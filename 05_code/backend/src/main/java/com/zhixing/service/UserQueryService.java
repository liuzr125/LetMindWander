package com.zhixing.service;

import com.zhixing.entity.AppUserEntity;
import com.zhixing.mapper.AppUserMapper;
import com.zhixing.model.UserView;
import org.springframework.stereotype.Service;

/** 用户查询：数据库访问下沉到 AppUserMapper（MyBatis-Plus），本类只做业务组装。 */
@Service
public class UserQueryService {
    private final AppUserMapper appUserMapper;

    public UserQueryService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    public AppUserEntity findByWechat(String appId, String openId) {
        return appUserMapper.selectByWechat(appId, openId);
    }

    public UserView findView(String userId) {
        AppUserEntity entity = appUserMapper.selectUserById(userId);
        return entity == null ? null : toView(entity);
    }

    public UserView toView(AppUserEntity entity) {
        UserView view = new UserView();
        view.setId(entity.getId());
        view.setNickname(entity.getNickname());
        view.setAvatarUrl(entity.getAvatarUrl());
        view.setMobileMasked(maskMobile(entity.getMobile()));
        view.setStatus(entity.getStatus());
        view.setCurrentPlanId(entity.getCurrentPlanId());
        view.setShortId(entity.getShortId());
        return view;
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + " **** " + mobile.substring(mobile.length() - 4);
    }
}
