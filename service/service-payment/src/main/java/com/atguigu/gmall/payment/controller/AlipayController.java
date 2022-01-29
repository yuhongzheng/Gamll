package com.atguigu.gmall.payment.controller;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.util.Map;

@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {
    @Autowired
    private AlipayService alipayService;
    @Autowired
    private PaymentService paymentService;

    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitOrder(@PathVariable(value = "orderId") Long orderId, HttpServletResponse response) {
        String from = "";
        try {
            from = alipayService.createAlipay(orderId);
        } catch (GmallException e) {
            e.printStackTrace();
            return "error: " + e.getMessage();
        }
        return from;
    }

    @ApiOperation("同步回调页面，显示订单信息给用户")
    @RequestMapping("callback/return")
    public String callBack() {
        // 同步回调给用户展示信息
        return "redirect:" + AlipayConfig.return_order_url;
    }

    @ApiOperation("支付宝异步回调")
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramsMap) {
        try {
            alipayService.handleNotify(paramsMap);
        } catch (GmallException e) {
            e.printStackTrace();
            return "failure";
        }

        return "success";
    }

    @RequestMapping("refund/{orderId}")
    @ApiOperation("用户退款")
    @ResponseBody
    public Result refund(@PathVariable(value = "orderId")Long orderId) {
        // 调用退款接口
        try {
            alipayService.refund(orderId);
        } catch (GmallException e) {
            e.printStackTrace();
            return Result.fail(e.getMessage());
        }
        return Result.ok();
    }

    @ApiOperation("根据订单ID查找支付记录")
    @GetMapping("getPaymentInfo/{orderId}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable Long orderId){
        return paymentService.getPaymentInfoByOrderId(orderId);
    }

    @ApiOperation("关闭交易")
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        return alipayService.closePay(orderId);
    }
}
