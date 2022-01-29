package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

@Controller
@Api

public class PaymentController {
    @Autowired
    private OrderFeignClient orderFeignClient;

    @GetMapping("pay.html")
    public String success(HttpServletRequest request, Model model) {
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(Long.parseLong(request.getParameter("orderId")));
        model.addAttribute("orderInfo", orderInfo);
        return "payment/pay";
    }

    @GetMapping("pay/success.html")
    public String success() {
        return "payment/success";
    }
}
