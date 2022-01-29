package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsApiController {

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    /**
     * 返回全部列表
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        return Result.ok(seckillGoodsService.findAll());
    }

    /**
     * 获取实体
     *
     * @param skuId
     * @return
     */
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable("skuId") Long skuId) {
        return Result.ok(seckillGoodsService.getSeckillGoods(skuId));
    }

    @ApiOperation("获取下单码")
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        String userId = request.getHeader("userId");
        try {
            return seckillGoodsService.getSeckillSkuIdStr(skuId, userId);
        } catch (GmallException e) {
            return Result.fail().message(e.getMessage());
        }
    }

    @ApiOperation("根据用户和商品ID实现秒杀预下单")
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable("skuId") Long skuId, @RequestParam("skuIdStr") String skuIdStr,
                               HttpServletRequest request) throws Exception {
        String userId = request.getHeader("userId");
        try {
            seckillGoodsService.seckillOrder(skuId, skuIdStr, userId);
            return Result.ok();
        } catch (GmallException e) {
            Result<String> fail = Result.fail(e.getMessage());
            fail.setCode(e.getCode());
            return fail;
        }
    }

    @ApiOperation("检查秒杀状态")
    @GetMapping(value = "auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable("skuId") Long skuId, HttpServletRequest request) {
        //当前登录用户
        String userId = AuthContextHolder.getUserId(request);
        return seckillGoodsService.checkOrder(skuId, userId);
    }

    @ApiOperation("秒杀确认订单")
    @GetMapping("auth/trade")
    public Result trade(@RequestParam Long skuId, HttpServletRequest request) {
        String userId = request.getHeader("userId");
        Map<String, Object> map = null;
        try {
            map = seckillGoodsService.trade(skuId, userId);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail().message(e.getMessage());
        }
        return Result.ok(map);
    }

    @ApiOperation("用户提交秒杀订单")
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        String userId = AuthContextHolder.getUserId(request);
        Long orderId = null;
        try {
            orderId = seckillGoodsService.submitOrder(userId, orderInfo);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.fail().message(e.getMessage());
        }
        return Result.ok(orderId);

    }
}