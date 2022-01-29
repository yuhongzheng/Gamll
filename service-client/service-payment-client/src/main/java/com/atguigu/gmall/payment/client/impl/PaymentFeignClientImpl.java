package com.atguigu.gmall.payment.client.impl;

import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import org.springframework.stereotype.Component;

@Component
public class PaymentFeignClientImpl implements PaymentFeignClient {
    @Override
    public Boolean closePay(Long orderId) {
        throw new GmallException("PaymentFeignClientImpl: Feign调用失败", null);
    }

    @Override
    public PaymentInfo getPaymentInfo(Long orderId) {
        throw new GmallException("PaymentFeignClientImpl: Feign调用失败", null);
    }
}
