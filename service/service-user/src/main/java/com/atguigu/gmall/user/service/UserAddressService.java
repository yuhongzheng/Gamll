package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;

import java.util.List;

public interface UserAddressService {
    List<UserAddress> findUserAddressListByUserId(String userId);
}
