package com.atguigu.gmall.order.config;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {
    @Bean
    public DirectExchange getPaymentPayExchange() {
        return new DirectExchange(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY, true, false);
    }

    @Bean
    public Queue getPaymentPayQueue() {
        return new Queue(MqConst.QUEUE_PAYMENT_PAY, true, false, false);
    }

    @Bean
    public Binding getPaymentPayBinding() {
        return BindingBuilder.bind(getPaymentPayQueue()).to(getPaymentPayExchange()).with(MqConst.ROUTING_PAYMENT_PAY);
    }
}
