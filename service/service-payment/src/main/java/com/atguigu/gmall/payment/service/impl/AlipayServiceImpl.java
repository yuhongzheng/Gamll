package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class AlipayServiceImpl implements AlipayService {
    @Autowired
    private OrderFeignClient orderFeignClient;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private AlipayClient alipayClient;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 处理用户发起的支付请求
     */
    @Override
    public String createAlipay(Long orderId) {
        //查询订单信息
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //判断订单状态
        if (!OrderStatus.UNPAID.name().equals(orderInfo.getOrderStatus())) {
            String message = null;
            if (OrderStatus.PAID.name().equals(orderInfo.getOrderStatus())
                    || OrderStatus.DELEVERED.name().equals(orderInfo.getOrderStatus())
                    || OrderStatus.SPLIT.name().equals(orderInfo.getOrderStatus())
                    || OrderStatus.FINISHED.name().equals(orderInfo.getOrderStatus())
            ) {
                message = "订单已支付";
            } else if (OrderStatus.CLOSED.name().equals(orderInfo.getOrderStatus())) {
                message = "订单已关闭";
            } else {
                message = "订单出错";
            }
            throw new GmallException(message, null);
        }

        //判断支付状态
        PaymentInfo paymentInfo = paymentService.getOne(orderId, PaymentType.ALIPAY);
        if (paymentInfo == null) {
            //保存数据库
            paymentService.savePaymentInfo(orderInfo);
        }

        //向支付宝发起请求，生成二维码
        AlipayTradePagePayRequest alipayTradePagePayRequest = new AlipayTradePagePayRequest();
        alipayTradePagePayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        alipayTradePagePayRequest.setNotifyUrl(AlipayConfig.notify_payment_url);
        HashMap<String, String> map = new HashMap<>();
        map.put("out_trade_no", orderInfo.getOutTradeNo());
        map.put("product_code", "FAST_INSTANT_TRADE_PAY");
//        map.put("total_amount", orderInfo.getTotalAmount().toString());
        map.put("time_expire", DateUtil.formatDate(orderInfo.getExpireTime(), "yyyy-MM-dd HH:mm:ss"));
        map.put("total_amount", "0.01");
        map.put("subject", "test");
        alipayTradePagePayRequest.setBizContent(JSON.toJSONString(map));
        //将页面返回前端
        try {
            return alipayClient.pageExecute(alipayTradePagePayRequest).getBody();
        } catch (AlipayApiException e) {
            e.printStackTrace();
            GmallException exception = new GmallException("向支付宝发起生成支付请求出错", null);
            exception.setStackTrace(e.getStackTrace());
            throw exception;
        }
    }

    /**
     * 处理支付宝异步回调
     * @param paramsMap
     */
    @Override
    public void handleNotify(Map<String, String> paramsMap) {
        //先验证签名
        boolean signVerified = checkSign(paramsMap);

        if (!signVerified) {
            throw new GmallException("签名验证失败" + paramsMap, null);
        }

        //查询支付状态
        AlipayTradeQueryResponse queryResponse = checkTradeStatus(paramsMap.get("out_trade_no"));
        if (!"TRADE_SUCCESS".equals(queryResponse.getTradeStatus())) {
            throw new GmallException("支付未成功" + paramsMap, null);
        }
        //更新状态
        updatePaymentDB(paramsMap, queryResponse, PaymentType.ALIPAY.name());

        //更新数据库订单状态
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY, MqConst.ROUTING_PAYMENT_PAY, paramsMap.get("out_trade_no"));
    }

    /**
     * 处理用户退款请求
     */
    @Override
    public void refund(Long orderId) {
        //查看支付状态
        PaymentInfo paymentInfo = paymentService.getOne(orderId, PaymentType.ALIPAY);
        if (!PaymentStatus.PAID.name().equals(paymentInfo.getPaymentStatus())) {
            String message = null;

            if (PaymentStatus.UNPAID.name().equals(paymentInfo.getPaymentStatus())) {
                message = "订单未支付";
            } else if (PaymentStatus.PAY_FAIL.name().equals(paymentInfo.getPaymentStatus())) {
                message = "订单支付失败";
            } else if (PaymentStatus.ClOSED.name().equals(paymentInfo.getPaymentStatus())) {
                message = "订单支付已关闭";
            } else {
                message = "查询退款交易状态出错";
            }

            throw new GmallException(message, null);
        }

        //查询支付宝订单状态
        AlipayTradeQueryResponse queryResponse = checkTradeStatus(paymentInfo.getOutTradeNo());
        if (queryResponse.getTradeStatus().equals("TRADE_FINISHED") || queryResponse.getTradeStatus().equals("TRADE_CLOSED")) {
            throw new GmallException("交易已关闭", null);
        } else if (!queryResponse.getTradeStatus().equals("TRADE_SUCCESS")) {
            throw new GmallException("交易状态不支持退款", null);
        }

        //查看订单状态
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        String orderStatus = orderInfo.getOrderStatus();
        if(OrderStatus.DELEVERED.name().equals(orderStatus)){
            throw new GmallException("商品已发货", null);
        }else if (OrderStatus.FINISHED.name().equals(orderStatus) || OrderStatus.CLOSED.name().equals(orderStatus)) {
            throw new GmallException("订单已完结", null);
        } else if (!OrderStatus.PAID.name().equals(orderStatus)
                && !OrderStatus.SPLIT.name().equals(orderStatus)
                && !OrderStatus.WAITING_DELEVER.name().equals(orderStatus)) {
            throw new GmallException("获取订单状态失败", null);
        }

        //处理订单
//        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_PAYMENT_REFUND, MqConst.ROUTING_PAYMENT_REFUND, orderId);
        Boolean success = orderFeignClient.refundOrder(orderId);
        if (!success) {
            throw new GmallException("退款业务失败", null);
        }

        //向支付宝发起退款
        BigDecimal refundAmount = new BigDecimal("0.01");
//            BigDecimal refundAmount = new BigDecimal(queryResponse.getTotalAmount());
        AlipayTradeRefundResponse refundResponse = doRefund(paymentInfo.getOutTradeNo(), refundAmount.toPlainString());
        if (!refundResponse.isSuccess()) {
            throw new GmallException("退款失败", null);
        }

        //修改支付状态
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentService.updateById(paymentInfo);
    }

    /**
     * 关闭订单
     */
    @Override
    public Boolean closePay(Long orderId) {
        PaymentInfo paymentInfo = paymentService.getPaymentInfoByOrderId(orderId);

        //支付记录不存在
        if (paymentInfo == null) {
            throw new GmallException("支付记录不存在 OrderId" + orderId, null);
        }

        //查询支付状态
        if (PaymentStatus.PAID.name().equals(paymentInfo.getPaymentStatus())) {
            return false; //用户已支付
        } else if (!PaymentStatus.UNPAID.name().equals(paymentInfo.getPaymentStatus()) && !PaymentStatus.PAY_FAIL.name().equals(paymentInfo.getPaymentStatus())) {
            throw new GmallException("支付状态异常： " + paymentInfo, null);
        }

        //查询支付宝交易记录
        AlipayTradeQueryResponse queryResponse = this.checkTradeStatus(paymentInfo.getOutTradeNo());
        if (queryResponse.isSuccess()) {
            if (queryResponse.getTradeStatus().equals("TRADE_SUCCESS")) {
                return false; //用户已支付
            } else if (queryResponse.getTradeStatus().equals("WAIT_BUYER_PAY")) {
                //关闭支付宝交易记录
                AlipayTradeCloseResponse closeResponse = closeAlipayTrade(paymentInfo.getOutTradeNo().toString());
                if (!closeResponse.isSuccess()) {
                    throw new GmallException("关闭交易失败： orderId " + orderId + "|" + closeResponse, null);
                }
            }
        }

        //更改支付状态
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
        paymentService.updateById(paymentInfo);
        return true;
    }

    /**
     * 支付宝退款请求
     */
    private AlipayTradeRefundResponse doRefund(String outTradeNo, String refundAmount) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        bizContent.put("refund_amount", refundAmount);

        request.setBizContent(bizContent.toString());
        AlipayTradeRefundResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            throw new GmallException(e, "退款失败");
        }

        return response;
    }

    @Transactional
    protected void updatePaymentDB(Map<String, String> paramsMap, AlipayTradeQueryResponse queryResponse, String paymentType) {
        String outTradeNo = paramsMap.get("out_trade_no");
        //查询数据库支付状态是否是未支付
        PaymentInfo paymentInfo = paymentService.getOne(new QueryWrapper<PaymentInfo>().eq("out_trade_no", outTradeNo)
                .eq("payment_type", paymentType));

        if (paymentInfo == null) {
            throw new GmallException("支付记录不存在" + outTradeNo + paymentType, null);
        }

        if (!PaymentStatus.UNPAID.name().equals(paymentInfo.getPaymentStatus())) {
            throw new GmallException("订单已支付" + outTradeNo + paymentType, null);
        }

        //更新数据库支付状态
        paymentInfo.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setCallbackContent(paramsMap.toString());
        paymentInfo.setTradeNo(queryResponse.getTradeNo());

        paymentService.updateById(paymentInfo);
    }

    //验证签名
    private boolean checkSign(Map<String, String> paramsMap) {
        boolean signVerified = false;
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramsMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
            GmallException gmallException = new GmallException("验证签名失败" + paramsMap, null);
            gmallException.setStackTrace(e.getStackTrace());
            throw gmallException;
        }

        return signVerified;
    }

    //支付宝查询请求
    private AlipayTradeQueryResponse checkTradeStatus(String outTradeNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        request.setBizContent(bizContent.toString());
        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            throw new GmallException(e, null);
        }

        return response;
    }

    //关闭支付宝交易
    private AlipayTradeCloseResponse closeAlipayTrade(String outTradeNo) {
        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        JSONObject bizContent = new JSONObject();
        bizContent.put("out_trade_no", outTradeNo);
        request.setBizContent(bizContent.toString());
        AlipayTradeCloseResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            throw new GmallException(e, "关闭支付宝请求失败: " + outTradeNo);
        }

        return response;
    }
}
