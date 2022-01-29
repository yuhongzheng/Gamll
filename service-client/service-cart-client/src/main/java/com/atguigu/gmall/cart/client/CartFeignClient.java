package com.atguigu.gmall.cart.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

@FeignClient(value = "service-cart", fallback = CartDegreeFeignClient.class)
public interface CartFeignClient {
    @PostMapping("/api/cart/addToCart/{skuId}/{skuNum}")
    Result addToCart(@PathVariable("skuId") Long skuId, @PathVariable("skuNum") Integer skuNum);

    @GetMapping("/api/cart/getCartCheckedList/{userId}")
    List<CartInfo> getCartCheckedList(@PathVariable("userId") String userId);

    @GetMapping("/api/cart/loadCartCache/{userId}")
    Result loadCartCache(@PathVariable("userId") String userId);

    @DeleteMapping("/api/cart/deleteCart/{skuId}")
    Result deleteCart(@PathVariable String skuId);

    /*
    @GetMapping("/api/cart/cartList")
    Result cartList(HttpServletRequest request);

    @GetMapping("/api/cart/checkCart/{skuId}/{isChecked}")
    Result checkCart(@PathVariable String skuId, @PathVariable Integer isChecked, HttpServletRequest request);
    */
}
