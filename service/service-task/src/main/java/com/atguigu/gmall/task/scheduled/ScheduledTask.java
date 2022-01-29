package com.atguigu.gmall.task.scheduled;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;

@Component
@EnableScheduling
@Slf4j
public class ScheduledTask {
    @Autowired
    RabbitTemplate rabbitTemplate;

    /**
     * 发送异步消息，加载秒杀商品到redis
     */
//    @Scheduled(cron = "0/30 * * * * ?")
    public void task1() {
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_TASK, MqConst.ROUTING_TASK_1, DateUtil.formatDate(new Date()));
    }

    /**
     * 秒杀结束后删除缓存中相关数据
     */
//    @Scheduled(cron = "0 0 18 * * ?")
    public void task18() {
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_TASK, MqConst.ROUTING_TASK_18, new Date());
    }
}
