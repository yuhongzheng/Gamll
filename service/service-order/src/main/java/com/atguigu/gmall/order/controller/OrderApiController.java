package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.service.OrderService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/order")
public class OrderApiController {
    @Autowired
    OrderService orderService;

    @GetMapping("auth/trade")
    public Result<Map<String, Object>> trade(HttpServletRequest request) {
        String userId = request.getHeader("userId");
        Map<String, Object> map = orderService.trade(userId);
        return Result.ok(map);
    }

    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request) {
        // 获取到用户Id
        String userId = AuthContextHolder.getUserId(request);
        String tradeNo = request.getParameter("tradeNo");
        orderInfo.setUserId(Long.parseLong(userId));

        // 验证通过，保存订单！
        try {
            Long orderId = orderService.saveOrderInfo(orderInfo, tradeNo);
            return Result.ok(orderId);
        } catch (GmallException e) {
            return Result.fail().message(e.getMessage());
        }
    }

    /**
     * 内部调用获取订单
     *
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable(value = "orderId") Long orderId) {
        return orderService.getOrderInfo(orderId);
    }

    @ApiOperation("库存系统调用，拆单")
    @PostMapping("orderSplit")
    @ResponseBody
    public Object orderSplit(HttpServletRequest request) {
        String orderId = request.getParameter("orderId");
        String wareSkuMap = request.getParameter("wareSkuMap"); // [map(wareId: ?, skuIds: list)]
        List<Map> maps = JSON.parseArray(wareSkuMap, Map.class);
        return orderService.splitOrder(orderId, maps);
//        String result = JSON.toJSONString(orderService.splitOrder(orderId, maps));
//        return result;
    }

    @ApiOperation("处理退款请求")
    @GetMapping("inner/refundOrder/{orderId}")
    @ResponseBody
    public Boolean refundOrder(@PathVariable Long orderId) {
        return orderService.refund(orderId);
    }

    @ApiOperation("秒杀提交订单")
    @PostMapping("inner/seckill/submitSeckillOrder")
    public Long submitSeckillOrder(@RequestBody OrderInfo orderInfo) {
        return orderService.saveSeckillOrder(orderInfo);
    }
}
