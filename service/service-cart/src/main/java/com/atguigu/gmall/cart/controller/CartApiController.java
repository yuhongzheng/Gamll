package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {
    @Autowired
    private CartService cartService;

    @ApiOperation("添加购物车")
    @PostMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable("skuId") Long skuId,
                            @PathVariable("skuNum") Integer skuNum,
                            HttpServletRequest request) {
        String userId = request.getHeader("userId");
        if (StringUtils.isEmpty(userId)) {
            userId = request.getHeader("userTempId");
            if (StringUtils.isEmpty(userId)) {
                return Result.fail().message("获取用户信息失败");
            }
        }
        cartService.addToCart(skuId, userId, skuNum);

        return Result.ok();
    }

    @ApiOperation("查看购物车")
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request) {
        String userId = request.getHeader("userId");
        String userTempId = request.getHeader("userTempId");

        if (StringUtils.isEmpty(userId) && StringUtils.isEmpty(userTempId)) {
            return Result.fail().message("获取用户信息失败");
        }

        List<CartInfo> cartInfoList = cartService.cartList(userId, userTempId);
        return Result.ok(cartInfoList);
    }

    @ApiOperation("点选购物项")
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable String skuId, @PathVariable Integer isChecked, HttpServletRequest request) {
        String userId = request.getHeader("userId");
        String userTempId = request.getHeader("userTempId");

        if (StringUtils.isEmpty(userId) && StringUtils.isEmpty(userTempId)) {
            return Result.fail().message("获取用户信息失败");
        }

        if (StringUtils.isEmpty(userId)) {
            userId = userTempId;
        }

        cartService.checkCart(userId, skuId, isChecked);

        return Result.ok();
    }

    @ApiOperation("删除购物项")
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable String skuId, HttpServletRequest request) {
        String userId = request.getHeader("userId");
        String userTempId = request.getHeader("userTempId");

        if (StringUtils.isEmpty(userId) && StringUtils.isEmpty(userTempId)) {
            return Result.fail().message("获取用户信息失败");
        }

        if (StringUtils.isEmpty(userId)) {
            userId = userTempId;
        }

        cartService.deleteCart(userId, skuId);

        return Result.ok();
    }

    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable(value = "userId") String userId) {
        return cartService.getCartCheckedList(userId);
    }

    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId) {
        cartService.loadCartInfoToRedis(userId);
        return Result.ok();
    }
}
