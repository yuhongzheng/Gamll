package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderInfo;

import java.util.Date;
import java.util.Map;

public interface SeckillGoodsService {
    void loadSeckillGoodsToRedis(String formatDate);

    Object findAll();

    Object getSeckillGoods(Long skuId);

    Result getSeckillSkuIdStr(Long skuId, String userId);

    void seckillOrder(Long skuId, String skuIdStr, String userId);

    void handleUserRecode(UserRecode userRecode);

    Result checkOrder(Long skuId, String userId);

    Map<String, Object> trade(Long skuId, String userId);

    Long submitOrder(String userId, OrderInfo orderInfo);

    void removeSeckillGoodsFromRedis(Date date);
}
