package com.atguigu.gmall.list.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.list.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ListReceiver {
    @Autowired
    RabbitTemplate rabbitTemplate;
    @Autowired
    SearchService searchService;

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_GOODS_UPPER)
    public void upperGoods(Long skuId, Message message, Channel channel) {
        if (skuId != null) {
            searchService.upperGoods(skuId);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }else{
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_GOODS_LOWER)
    public void lowerGoods(Long skuId, Message message, Channel channel) {
        if (skuId != null) {
            searchService.lowerGoods(skuId);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }else{
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }
}
