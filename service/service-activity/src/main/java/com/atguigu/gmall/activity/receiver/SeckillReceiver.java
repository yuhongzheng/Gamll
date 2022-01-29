package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.model.activity.UserRecode;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;

@Component
public class SeckillReceiver {
    @Autowired
    SeckillGoodsService seckillGoodsService;

    /**
     * 处理秒杀商品加载到缓存中
     */
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            value = @Queue(value = MqConst.QUEUE_TASK_1, durable = "true", exclusive = "false", autoDelete = "false"),
            key = MqConst.ROUTING_TASK_1
    ))
    @SneakyThrows
    public void receiveTask1(String formatDate, Message message, Channel channel) {
        seckillGoodsService.loadSeckillGoodsToRedis(formatDate);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * 秒杀用户加入队列
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER, durable = "true"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER, type = ExchangeTypes.DIRECT, durable = "true"),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel) throws IOException {
        if (userRecode != null) {
            seckillGoodsService.handleUserRecode(userRecode);
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }else{
            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        }
    }

    /**
     * 秒杀结束后删除缓存中相关数据
     * @param  date 在此日期之前的数据要删除
     */
    @RabbitListener(bindings = @QueueBinding(
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            value = @Queue(value = MqConst.QUEUE_TASK_18, durable = "true", exclusive = "false", autoDelete = "false"),
            key = MqConst.ROUTING_TASK_18
    ))
    @SneakyThrows
    public void receiveTask18(Date date, Message message, Channel channel) {
        seckillGoodsService.removeSeckillGoodsFromRedis(date);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }
}
