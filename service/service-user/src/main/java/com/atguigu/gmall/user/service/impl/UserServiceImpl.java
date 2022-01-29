package com.atguigu.gmall.user.service.impl;

import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.mapper.UserMapper;
import com.atguigu.gmall.user.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserMapper userMapper;

    @Override
    public UserInfo login(UserInfo userInfo) {
        String passwd = DigestUtils.md5DigestAsHex(userInfo.getPasswd().getBytes());
        return userMapper.selectOne(new QueryWrapper<UserInfo>().
                eq("login_name", userInfo.getLoginName()).eq("passwd", passwd));
    }
}
