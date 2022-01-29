package com.atguigu.gmall.cart.service;

import brave.http.HttpRequest;
import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

public interface CartService {
    void addToCart(Long skuId, String userId, Integer skuNum);

    List<CartInfo> cartList(String userId, String userTempId);

    void checkCart(String userId, String skuId, Integer isChecked);

    void deleteCart(String userId, String skuId);

    List<CartInfo> getCartCheckedList(String userId);

    void loadCartInfoToRedis(String userId);
}
