package com.atguigu.gmall.order.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.impl.OrderDegradeFeignClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@FeignClient(value = "service-order")
public interface OrderFeignClient {

    @GetMapping("/api/order/auth/trade")
    Result<Map<String, Object>> trade();

    @GetMapping("/api/order/inner/getOrderInfo/{orderId}")
    OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId);

    @GetMapping("/api/order/inner/refundOrder/{orderId}")
    Boolean refundOrder(@PathVariable Long orderId);

    @PostMapping("/api/order/inner/seckill/submitSeckillOrder")
    Long submitSeckillOrder(@RequestBody OrderInfo orderInfo);
}