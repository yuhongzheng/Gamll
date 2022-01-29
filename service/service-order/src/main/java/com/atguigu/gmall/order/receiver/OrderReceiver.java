package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class OrderReceiver {
    @Autowired
    OrderInfoMapper orderInfoMapper;
    @Autowired
    OrderService orderService;


    /**
     * 订单超时取消订单
     */
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    protected void cancelOrder(Long orderId, Message message, Channel channel) {
        orderService.execExpiredOrder(orderId);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * 接收AlipayService发过来的信息，用户支付成功
     *
     * @param outTradeNo 外部交易号
     */
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_PAYMENT_PAY)
    protected void payOrder(String outTradeNo, Message message, Channel channel) {
        if (!StringUtils.isEmpty(outTradeNo)) {
            orderService.handlePay(outTradeNo);
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

    /**
     * 接受库存减库存结果
     */
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_WARE_ORDER, durable = "true", exclusive = "false", autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_WARE_ORDER, type = "direct"),
            key = MqConst.ROUTING_WARE_ORDER
    ))
    public void updateOrderStatus(String json, Message message, Channel channel) {
        if (!StringUtils.isEmpty(json)) {
            Map map = JSON.parseObject(json, Map.class);
            String orderId = (String) map.get("orderId");
            String status = (String) map.get("status");
            OrderInfo orderInfo = orderInfoMapper.selectById(Long.parseLong(orderId));
            if (orderInfo == null) {
                throw new GmallException("查询不到订单" + orderId, null);
            }
            //判断订单状态
            if (!OrderStatus.PAID.name().equals(orderInfo.getOrderStatus())) {
                throw new GmallException("订单状态异常" + orderInfo, null);
            }

            //减库存成功
            if ("DEDUCTED".equals(status)) {
                orderInfo.setOrderStatus(OrderStatus.WAITING_DELEVER.name());
                orderInfo.setProcessStatus(ProcessStatus.WAITING_DELEVER.name());
                orderInfoMapper.updateById(orderInfo);
            } else {
                //减库存失败
                orderInfo.setProcessStatus(ProcessStatus.STOCK_EXCEPTION.name());
                orderInfoMapper.updateById(orderInfo);
            }
        }

        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }

/*    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_REFUND, durable = "true", exclusive = "false", autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_REFUND),
            key = MqConst.ROUTING_PAYMENT_REFUND
    ))
    public void refundOrder(Long orderId, Message message, Channel channel) {
        if (orderId == null) {
            throw new GmallException("OrderId为空", null);
        }

        orderService.refund(orderId);
        channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    }*/
}
