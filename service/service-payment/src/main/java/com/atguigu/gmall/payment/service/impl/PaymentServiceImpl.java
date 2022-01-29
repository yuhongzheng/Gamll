package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class PaymentServiceImpl extends ServiceImpl<PaymentInfoMapper, PaymentInfo> implements PaymentService {

    @Override
    public void savePaymentInfo(OrderInfo orderInfo) {
        //检查订单是否存在
        int count = this.count(new QueryWrapper<PaymentInfo>().eq("order_id", orderInfo.getId())
                .eq("payment_type", PaymentType.ALIPAY.name()));
        if (count > 0) {
            return;
        }


        //插入数据库
        PaymentInfo paymentInfo = new PaymentInfo(orderInfo.getOutTradeNo(), orderInfo.getId(), PaymentType.ALIPAY.name(),
                null, orderInfo.getTotalAmount(), orderInfo.getTradeBody(), PaymentStatus.UNPAID.name(), new Date(),
                null, null);

        this.save(paymentInfo);
    }

    //更据订单ID和支付类型查找
    @Override
    public PaymentInfo getOne(Long orderId, PaymentType paymentType){
        return this.getOne(new QueryWrapper<PaymentInfo>().eq("order_id", orderId).eq("payment_type", paymentType.name()));
    }

    //更据外部订单号和支付方式查找
    @Override
    public PaymentInfo getOne(String outTradeNo, PaymentType paymentType){
        return this.getOne(new QueryWrapper<PaymentInfo>().eq("out_trade_no", outTradeNo).eq("payment_type", paymentType));
    }

    @Override
    public PaymentInfo getPaymentInfoByOrderId(Long orderId) {
        return getOne(new QueryWrapper<PaymentInfo>().eq("order_id", orderId));
    }
}
