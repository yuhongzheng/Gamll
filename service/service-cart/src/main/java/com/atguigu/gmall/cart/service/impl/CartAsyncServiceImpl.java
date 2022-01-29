package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class CartAsyncServiceImpl implements CartAsyncService {
    @Autowired
    CartInfoMapper cartInfoMapper;

    @Override
    @Async
    public void saveCartInfo(CartInfo cartInfo) {
        cartInfoMapper.insert(cartInfo);
    }

    @Override
    @Async
    public void updateCartInfo(CartInfo cartInfo) {
        cartInfoMapper.update(cartInfo, new UpdateWrapper<CartInfo>()
                .eq("user_id", cartInfo.getUserId())
                .eq("sku_id", cartInfo.getSkuId()));
    }

    @Override
    @Async
    public void deleteCartList(String userId) {
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id", userId));
    }

    @Override
    @Async
    public void checkCart(String userId, String skuId, Integer isChecked) {
        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);
        cartInfoMapper.update(cartInfo, new UpdateWrapper<CartInfo>()
                .eq("user_id", userId).eq("sku_id", skuId));
    }

    @Override
    @Async
    public void deleteCart(String userId, String skuId) {
        cartInfoMapper.delete(new QueryWrapper<CartInfo>().eq("user_id", userId).eq("sku_id", skuId));
    }
}
