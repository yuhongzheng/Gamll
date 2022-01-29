package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.scheduling.annotation.Async;

public interface CartAsyncService {
    void saveCartInfo(CartInfo cartInfo);

    /**
     * 根据SKUID和USERID
     * @param cartInfo
     */
    void updateCartInfo(CartInfo cartInfo);

    void deleteCartList(String userId);

    void checkCart(String userId, String skuId, Integer isChecked);

    void deleteCart(String userId, String skuId);
}
