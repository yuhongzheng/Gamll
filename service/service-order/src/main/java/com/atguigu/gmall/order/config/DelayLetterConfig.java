package com.atguigu.gmall.order.config;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.Map;

@Configuration
public class DelayLetterConfig {
    @Bean
    public CustomExchange exchangeDirectOrderCancel() {
        Map<String, Object> map = Collections.singletonMap("x-delayed-type", "direct");
        return new CustomExchange(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL, "x-delayed-message", true, false, map);
    }

    @Bean
    public Queue queueOrderCancel() {
        return new Queue(MqConst.QUEUE_ORDER_CANCEL, true, false, false);
    }


    @Bean
    public Binding bindingDelay() {
        return BindingBuilder.bind(queueOrderCancel()).to(exchangeDirectOrderCancel()).with(MqConst.ROUTING_ORDER_CANCEL).noargs();
    }

}
