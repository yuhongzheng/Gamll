package com.atguigu.gmall.list.config;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {
    @Bean
    public DirectExchange goodExchange() {
        return new DirectExchange(MqConst.EXCHANGE_DIRECT_GOODS, true, false);
    }

    @Bean
    public Queue upperQueue() {
        return new Queue(MqConst.QUEUE_GOODS_UPPER, true, false, false);
    }

    @Bean
    public Queue lowerQueue() {
        return new Queue(MqConst.QUEUE_GOODS_LOWER, true, false, false);
    }

    @Bean
    public Binding bindingUpper() {
        return BindingBuilder.bind(upperQueue()).to(goodExchange()).with(MqConst.ROUTING_GOODS_UPPER);
    }

    @Bean
    public Binding bindingLower() {
        return BindingBuilder.bind(lowerQueue()).to(goodExchange()).with(MqConst.ROUTING_GOODS_LOWER);
    }
}
