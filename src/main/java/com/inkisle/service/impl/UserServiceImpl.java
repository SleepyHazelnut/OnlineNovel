package com.inkisle.service.impl;

import com.inkisle.entity.UserInfo;
import com.inkisle.mapper.UserInfoMapper;
import com.inkisle.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Override
    public List<UserInfo> listAll() {
        return userInfoMapper.selectAll();
    }

}
