package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.baomidou.mybatisplus.extension.service.IService;

public interface PaymentService extends IService<PaymentInfo> {
    void savePaymentInfo(OrderInfo orderInfo);

    PaymentInfo getOne(Long orderId, PaymentType paymentType);

    PaymentInfo getOne(String outTradeNo, PaymentType paymentType);

    PaymentInfo getPaymentInfoByOrderId(Long orderId);
}
