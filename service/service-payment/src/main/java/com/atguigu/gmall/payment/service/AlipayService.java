package com.atguigu.gmall.payment.service;

import java.util.Map;

public interface AlipayService {
    String createAlipay(Long orderId);

    void handleNotify(Map<String, String> paramsMap);

    void refund(Long orderId);

    Boolean closePay(Long orderId);
}
