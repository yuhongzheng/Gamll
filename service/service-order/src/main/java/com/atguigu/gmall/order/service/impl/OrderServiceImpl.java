package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.SneakyThrows;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Value("${ware.url}")
    private String wareUrl;

    @Override
    public Map<String, Object> trade(String userId) {
        Map<String, Object> map = new HashMap<>();

        //获取用户收货地址
        CompletableFuture<Void> userAddressFuture = CompletableFuture.runAsync(() -> {
            List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
            map.put("userAddressList", userAddressList);
        });

        //获取购物车信息
        CompletableFuture<Void> cartInfoFuture = CompletableFuture.runAsync(() -> {
            List<CartInfo> cartInfoList = cartFeignClient.getCartCheckedList(userId);
            List<OrderDetail> orderDetailList = cartInfoList.stream().map(cartInfo -> new OrderDetail(null,
                    cartInfo.getSkuId(), cartInfo.getSkuName(),
                    cartInfo.getImgUrl(), cartInfo.getSkuPrice(), cartInfo.getSkuNum(), null, new Date(),
                    null, null, null)
            ).collect(Collectors.toList());
            OrderInfo orderInfo = new OrderInfo();
            orderInfo.setOrderDetailList(orderDetailList);
            orderInfo.sumTotalAmount();
            map.put("detailArrayList", orderDetailList);
            map.put("totalNum", orderDetailList.size());
            map.put("totalAmount", orderInfo.getTotalAmount());
        });

        //获取下单凭证
        map.put("tradeNo", getTradeNo(userId));
        CompletableFuture.allOf(userAddressFuture, cartInfoFuture).join();
        return map;
    }

    /**
     * 下单，保存订单数据
     * <p>
     * 1.检查库存
     * 2. 获取价格
     * 3. 防止重复回退提交
     * 4. 清除购物车
     * 5. 保存订单数据
     *
     * @return 订单号(ID)
     */
    @Override
    @SneakyThrows
    public Long saveOrderInfo(OrderInfo orderInfo, String tradeNo) {
        //是否重复提交
        if (StringUtils.isEmpty(tradeNo) || !tradeNo.equals(getTradeNo(orderInfo.getUserId().toString()))) {
            throw new GmallException("不能重复下单", null);
        }

        //删除缓存
        redisTemplate.delete(getTradeNoKey(orderInfo.getUserId().toString()));
        ArrayList<String> errorList = new ArrayList<>();

        //检查价格
        //如果缓存中的价格和数据库中的价格不符，需要重新加载缓存
        CompletableFuture<Void> priceFuture = CompletableFuture.runAsync(() -> {
            for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                if (orderDetail.getOrderPrice().compareTo(skuPrice) != 0) {
                    errorList.add("价格有变");
                    String userId = orderInfo.getUserId().toString();
                    cartFeignClient.loadCartCache(userId);
                    break;
                }
            }
        });

        //检查库存
        CompletableFuture<Void> stockFuture = CompletableFuture.runAsync(() -> {
            for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
                if (!checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum())) {
                    errorList.add("库存不足：" + orderDetail.getSkuName());
                    break;
                }
            }
        });

        CompletableFuture.allOf(priceFuture, stockFuture).join();
        if (errorList.size() > 0) {
            throw new GmallException(String.join("\n", errorList), null);
        }

        //存入数据库
        Long orderId = saveDB(orderInfo);

        //发送延迟消息
       sendDelayMessage(orderId);

        //清除购物车
        //orderInfo.getOrderDetailList().forEach(orderDetail -> cartFeignClient.deleteCart(orderDetail.getSkuId().toString()));

        return orderId;
    }

    //发送延迟消息，如果超时未支付关闭订单
    private void sendDelayMessage(Long orderId) {
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL, MqConst.ROUTING_ORDER_CANCEL, orderId,
                message -> {
//                    message.getMessageProperties().setDelay(MqConst.DELAY_TIME);
                    message.getMessageProperties().setDelay(25 * 1000);
                    return message;
                });
    }

    /**
     * 关闭订单。如果支付不关闭。同时关闭交易
     */
    @Override
    @Transactional
    public void execExpiredOrder(Long orderId) {
        if (orderId == null) {
            throw new GmallException("订单ID为空：" + orderId, null);
        }

        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new GmallException("查不到订单：" + orderId, null);
        }

        //判断订单状态
        if (!orderInfo.getOrderStatus().equals(OrderStatus.UNPAID.name()) || !orderInfo.getProcessStatus().equals(ProcessStatus.UNPAID.name())) {
            return;
        }

        //查看交易状态
        PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getId());
        if (paymentInfo != null) {
            if (PaymentStatus.PAID.name().equals(paymentInfo.getPaymentStatus())) {
                return; //用户已支付
            }else{
                Boolean success = paymentFeignClient.closePay(orderInfo.getId());
                if (!success) {
                    return;
                }
            }
        }

        //更新订单状态
        changeOrderStatus(orderId, ProcessStatus.CLOSED);
    }

    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderId));
        orderInfo.setOrderDetailList(orderDetailList);
        return orderInfo;
    }

    /**
     * 处理用户支付后的订单更新
     *
     * @param outTradeNo 外部交易号
     */
    @Override
    public void handlePay(String outTradeNo) {
        //判断订单状态
        OrderInfo orderInfo = orderInfoMapper.selectOne(new QueryWrapper<OrderInfo>().eq("out_trade_no", outTradeNo));
        if (orderInfo == null) {
            throw new GmallException("查询不到订单信息：" + outTradeNo, null);
        }
        if (!OrderStatus.UNPAID.name().equals(orderInfo.getOrderStatus())) {
            throw new GmallException("订单状态有误：" + orderInfo, null);
        }

        //修改订单状态
        orderInfo.setOrderStatus(OrderStatus.PAID.name());
        orderInfo.setProcessStatus(ProcessStatus.PAID.name());
        orderInfoMapper.updateById(orderInfo);

        //通知库存系统, 等待接收异步消息
        //查询订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderInfo.getId()));
        orderInfo.setOrderDetailList(orderDetailList);
        String orderTaskJson = JSON.toJSONString(parseOrderTask(orderInfo));
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_WARE_STOCK, MqConst.ROUTING_WARE_STOCK, orderTaskJson);
    }

    /**
     * 拆分订单
     *  @param orderId
     * @param maps    [map(wareId: ?, skuIds: list)]
     * @return 返回JSON字符串集合
     */
    @Override
    @Transactional
    public Object splitOrder(String orderId, List<Map> maps) {
        //查询订单状态
        OrderInfo orderInfo = orderInfoMapper.selectById(Long.parseLong(orderId));
        if (orderInfo == null) {
            throw new GmallException("订单不存在" + orderId, null);
        }
        if (!OrderStatus.PAID.name().equals(orderInfo.getOrderStatus())) {
            throw new GmallException("订单状态非法：" + orderInfo, null);
        }

        List<OrderDetail> orderDetailList = orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id", orderInfo.getId()));
        orderInfo.setOrderDetailList(orderDetailList);

        List<OrderInfo> subOrderInfoList = new ArrayList<>(maps.size());
        //拆单
        for (Map map : maps) {
            String wareId = (String) map.get("wareId");
            List<String> skuIdStrs = (List<String>) map.get("skuIds");
            List<Long> skuIds = skuIdStrs.stream().map(str -> Long.parseLong(str)).collect(Collectors.toList());
            OrderInfo subOrderInfo = new OrderInfo();
            BeanUtils.copyProperties(orderInfo, subOrderInfo);
            subOrderInfo.setId(null);
            subOrderInfo.setCreateTime(new Date());
            subOrderInfo.setWareId(wareId);
            subOrderInfo.setOrderStatus(OrderStatus.PAID.name());
            subOrderInfo.setCreateTime(new Date());
            subOrderInfo.setProcessStatus(ProcessStatus.NOTIFIED_WARE.name());
            subOrderInfo.setParentOrderId(orderInfo.getId());

            //获取子订单信息
            List<OrderDetail> subDetailList = orderInfo.getOrderDetailList().stream().
                    filter(orderDetail -> skuIds.contains(orderDetail.getSkuId()))
                    .collect(Collectors.toList());
            subOrderInfo.setOrderDetailList(subDetailList);
            subOrderInfo.sumTotalAmount();

            //保存到数据库
            orderInfoMapper.insert(subOrderInfo);//不再重新创建orderDetail记录
            subOrderInfoList.add(subOrderInfo);
        }

        //更改订单状态
        orderInfo.setOrderStatus(OrderStatus.SPLIT.name());
        orderInfo.setProcessStatus(ProcessStatus.SPLIT.name());
        orderInfoMapper.updateById(orderInfo);

        return subOrderInfoList.stream().map(this::parseOrderTask).collect(Collectors.toList());
    }

    //退款
    @Override
    @Transactional
    public Boolean refund(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if (orderInfo == null) {
            throw new GmallException("查询不到订单：" + orderId, null);
        }

        if (OrderStatus.CLOSED.name().equals(orderInfo.getOrderStatus())
                || OrderStatus.FINISHED.name().equals(orderInfo.getOrderStatus())
                || OrderStatus.UNPAID.name().equals(orderInfo.getOrderStatus())) {
            throw new GmallException("订单状态异常：" + orderInfo, null);
        } else if (OrderStatus.DELEVERED.name().equals(orderInfo.getOrderStatus())) {
            return false;
        }

        //订单发送同步请求到库存
        boolean success = this.closeWare(orderId);
        if (!success) {
            return false;
        }

        //更新订单状态
        if (OrderStatus.PAID.name().equals(orderInfo.getOrderStatus())
                || OrderStatus.WAITING_DELEVER.name().equals(orderInfo.getOrderStatus())) {
            orderInfo.setOrderStatus(OrderStatus.CLOSED.name());
            orderInfoMapper.updateById(orderInfo);
        } else if (OrderStatus.SPLIT.name().equals(orderInfo.getOrderStatus())) {
            OrderInfo subOrderInfo = new OrderInfo();
            subOrderInfo.setOrderStatus(OrderStatus.CLOSED.name());
            orderInfoMapper.update(subOrderInfo, new UpdateWrapper<OrderInfo>().eq("parent_order_id", orderInfo.getId()));
        }

        return true;
    }

    @Override
    public Long saveSeckillOrder(OrderInfo orderInfo) {
        //插入数据库
        Long orderId = saveDB(orderInfo);
        //发送延迟消息
        sendDelayMessage(orderId);
        return orderId;
    }

    private Map parseOrderTask(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();
        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());

        ArrayList<Map> details = new ArrayList<>(orderInfo.getOrderDetailList().size());
        for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
            HashMap<String, Object> detailMap = new HashMap<>();
            detailMap.put("skuId", orderDetail.getSkuId());
            detailMap.put("skuNum", orderDetail.getSkuNum());
            detailMap.put("skuName", orderDetail.getSkuName());
            details.add(detailMap);
        }
        map.put("details", details);

        return map;
    }

    //改变订单状态
    public void changeOrderStatus(Long orderId, ProcessStatus processStatus) {
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfo.setProcessStatus(processStatus.name());
        orderInfoMapper.updateById(orderInfo);
    }

    @Transactional
    protected Long saveDB(OrderInfo orderInfo) {
        //计算总金额
        orderInfo.sumTotalAmount();
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        //订单外部号
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        orderInfo.setCreateTime(new Date());
        //过期时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        orderInfo.setExpireTime(calendar.getTime());
        //订单明细
        String tradeBody = orderInfo.getOrderDetailList().stream().map(OrderDetail::getSkuName).collect(Collectors.joining("\n"));
        if (tradeBody.length() > 200) {
            tradeBody = tradeBody.substring(197) + "...";
        }
        orderInfo.setTradeBody(tradeBody);
        orderInfoMapper.insert(orderInfo);

        for (OrderDetail orderDetail : orderInfo.getOrderDetailList()) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);
        }

        return orderInfo.getId();
    }

    public String getTradeNo(String userId) {
        String tradeNoKey = getTradeNoKey(userId);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        if (redisTemplate.hasKey(tradeNoKey)) {
            return (String) redisTemplate.opsForValue().get(tradeNoKey);
        }

        redisTemplate.opsForValue().set(tradeNoKey, uuid, 1, TimeUnit.DAYS);
        return uuid;
    }

    private String getTradeNoKey(String userId) {
        return RedisConst.USER_KEY_PREFIX + userId + ":tradeNo";
    }

    private boolean checkStock(Long skuId, Integer skuNum) {
        String result = HttpClientUtil.doGet(wareUrl + "/hasStock?skuId=" + skuId + "&num=" + skuNum);
        return "1".equals(result);
    }

    private boolean closeWare(Long orderId) {
        String result = HttpClientUtil.doGet(wareUrl + "/closeWare/" + orderId);
        return Boolean.parseBoolean(result);
    }
}
