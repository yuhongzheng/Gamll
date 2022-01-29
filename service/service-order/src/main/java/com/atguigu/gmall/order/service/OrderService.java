package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.order.OrderInfo;

import java.util.List;
import java.util.Map;

public interface OrderService {
    Map<String, Object> trade(String userId);

    Long saveOrderInfo(OrderInfo orderInfo, String tradeNo);

    void execExpiredOrder(Long orderId);

    OrderInfo getOrderInfo(Long orderId);

    void handlePay(String outTradeNo);

    Object splitOrder(String orderId, List<Map> maps);

    Boolean refund(Long orderId);

    Long saveSeckillOrder(OrderInfo orderInfo);
}
