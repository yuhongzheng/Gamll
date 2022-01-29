package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.execption.GmallException;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {
    @Autowired
    SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    UserFeignClient userFeignClient;

    @Autowired
    OrderFeignClient orderFeignClient;

    @Override
    public void loadSeckillGoodsToRedis(String formatDate) {
        //从数据库中查询秒杀商品
        QueryWrapper<SeckillGoods> wrapper = new QueryWrapper<>();
        wrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", formatDate) //时间
                .eq("status", 1) //状态
                .gt("stock_count", 0); //剩余库存
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(wrapper);

        if (CollectionUtils.isEmpty(seckillGoodsList)) {
            return;
        }

        for (SeckillGoods seckillGoods : seckillGoodsList) {
            //判断缓存中是否存在数据
            Boolean loaded = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).hasKey(seckillGoods.getSkuId().toString());
            if (!loaded) {
                //放入缓存
                redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);

                //将剩余商品数放入缓存
                for (Integer i = 0; i < seckillGoods.getStockCount(); i++) {
                    redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId()).rightPush(i);
                }

//                redisTemplate.boundValueOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId()).set(seckillGoods.getStockCount());
            }

            //发送消息到集群节点
            if (CacheHelper.get(seckillGoods.getSkuId().toString()) == null) {
                redisTemplate.convertAndSend("seckillpush", seckillGoods.getSkuId() + ":" + seckillGoods.getStatus());
            }

        }
    }

    /**
     * 从缓存中取秒杀商品列表
     *
     * @return
     */
    @Override
    public Object findAll() {
        return redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).values();
    }

    @Override
    public Object getSeckillGoods(Long skuId) {
        return redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
    }

    /**
     * 获取秒杀下单码
     *
     * @param skuId
     * @return
     */
    @Override
    public Result getSeckillSkuIdStr(Long skuId, String userId) {
        if (StringUtils.isEmpty(userId)) {
            throw new GmallException("用户未登录", null);
        }

        SeckillGoods seckillGoods = (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
        if (seckillGoods == null) {
            throw new GmallException("未查询到秒杀商品：" + skuId, null);
        }

        Date now = new Date();
        if (!(DateUtil.dateCompare(seckillGoods.getStartTime(), now) && DateUtil.dateCompare(now, seckillGoods.getEndTime()))) {
            throw new GmallException("商品不在时间范围：" + DateUtil.formatDate(now) + "|" + seckillGoods.getStartTime() + " -> " + seckillGoods.getEndTime(), null);
        }

        //生成下单码
        String encrypt = MD5.encrypt(userId);
        return Result.ok(encrypt);
    }

    /**
     * 处理秒杀发送异步消息
     */
    @Override
    public void seckillOrder(Long skuId, String skuIdStr, String userId) {
        if (StringUtils.isEmpty(userId)) {
            throw new GmallException("用户未登录", ResultCodeEnum.LOGIN_AUTH.getCode());
        }

        if (!MD5.encrypt(userId).equals(skuIdStr)) {
            throw new GmallException("非法下单", ResultCodeEnum.SECKILL_ILLEGAL.getCode());
        }

        //判断商品状态
        String state = (String) CacheHelper.get(skuId.toString());
        if (StringUtils.isEmpty(state)) {
            throw new GmallException("非法下单", ResultCodeEnum.SECKILL_ILLEGAL.getCode());
        } else if ("1".equals(state)) {
            UserRecode userRecode = new UserRecode();
            userRecode.setSkuId(skuId);
            userRecode.setUserId(userId);
            rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_SECKILL_USER, MqConst.ROUTING_SECKILL_USER, userRecode);
        } else if ("0".equals(state)) {
            throw new GmallException("抢购结束", ResultCodeEnum.SECKILL_FINISH.getCode());
        } else {
            throw new GmallException("状态错误", ResultCodeEnum.SERVICE_ERROR.getCode());
        }
    }

    /**
     * 处理接到的秒杀消息请求
     */
    @Override
    public void handleUserRecode(UserRecode userRecode) {
        Long skuId = userRecode.getSkuId();
        String userId = userRecode.getUserId();

        //判断用户是否参与过此次秒杀，并记录用户
        Boolean absent = redisTemplate.boundValueOps(RedisConst.SECKILL_USER + userId + ":" + skuId).setIfAbsent("", RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        if (!absent) {
            //参与过秒杀
            return;
        }

        //判断秒杀库存
        String state = (String) CacheHelper.get(userRecode.getSkuId().toString());
        if (StringUtils.isEmpty(state) || !state.equals("1")) {
            //已结束
            return;
        }

        //获取秒杀资格
        Integer index = (Integer) redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + skuId).rightPop();
        if (index == null) {
            //更新map商品状态,已卖光
            rabbitTemplate.convertAndSend("seckillpush", skuId+":0");
            return;
        }

        //预下单，存入redis
        SeckillGoods seckillGoods = (SeckillGoods) this.getSeckillGoods(skuId);
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setNum(1);
        orderRecode.setOrderStr(MD5.encrypt(userId + skuId));
        orderRecode.setUserId(userId);
        orderRecode.setSeckillGoods(seckillGoods);
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(userId + ":" + skuId, orderRecode);

        //更新库存
        updateStockDB(seckillGoods);
    }

    /**
     * 查询秒杀是否成功
     */
    @Override
    public Result checkOrder(Long skuId, String userId) {
        //查询用户记录
        Boolean hasKey = redisTemplate.hasKey(RedisConst.SECKILL_USER + userId + ":" + skuId);
        if (hasKey) {
            OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId + ":" + skuId);
            if (orderRecode != null) {
                //预下单成功
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            } else {
                String orderId = (String) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId + ":" + skuId);
                if (StringUtils.isEmpty(orderId)) {
                    //抢单失败
                    return Result.build(null, ResultCodeEnum.SECKILL_FAIL);
                } else {
                    //下订单成功
                    return Result.build(orderId, ResultCodeEnum.SECKILL_ORDER_SUCCESS);
                }
            }
        } else {
            String state = (String) CacheHelper.get(skuId.toString());
            if (StringUtils.isEmpty(state) || !state.equals("1")) {
                //商品售罄或过期
                return Result.build(null, ResultCodeEnum.SECKILL_FAIL);
            } else {
                //排队
                return Result.build(null, ResultCodeEnum.SECKILL_RUN);
            }
        }
    }

    /**
     * 处理秒杀下订单
     */
    @Override
    public Map<String, Object> trade(Long skuId, String userId) {
        //从缓存获取预下单信息
        OrderRecode orderRecode = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId + ":" + skuId);
        if (orderRecode == null) {
            throw new GmallException("查询不到订单信息" + userId + ":" + skuId, null);
        }

        Map<String, Object> result = new HashMap<>();

        //获取用户地址
        CompletableFuture<Void> addressFuture = CompletableFuture.runAsync(() ->
                result.put("userAddressList", userFeignClient.findUserAddressListByUserId(userId)));

        //创建订单
        OrderDetail orderDetail = new OrderDetail(
                null, orderRecode.getSeckillGoods().getSkuId(), orderRecode.getSeckillGoods().getSkuName(),
                orderRecode.getSeckillGoods().getSkuDefaultImg(), orderRecode.getSeckillGoods().getCostPrice(),
                orderRecode.getNum(), null, null, null, null, null
        );
        result.put("detailArrayList", Collections.singletonList(orderDetail));
        result.put("totalAmount", orderDetail.getOrderPrice());

        //返回结果
        addressFuture.join();
        return result;
    }

    @Override
    public Long submitOrder(String userId, OrderInfo orderInfo) {
        //设置购买用户
        if (StringUtils.isEmpty(userId)) {
            throw new GmallException("用户未登录", null);
        }

        orderInfo.setUserId(Long.parseLong(userId));

        //设置交易号,防止多次提交
        boolean success = lockTrade(userId);
        if (!success) {
            throw new GmallException("正在提交订单，请勿多次提交。", null);
        }

        try {
            //保存订单信息
            Long orderId = orderFeignClient.submitSeckillOrder(orderInfo);
            if (orderId == null) {
                throw new GmallException("订单提交失败", null);
            }

            //删除预下单信息
            String key = userId + ":" + orderInfo.getOrderDetailList().get(0).getSkuId();
            redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(key);

            //保存下单记录到缓存
            redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(key, orderId.toString());
            return orderId;
        } finally {
            //删除交易号
            unlock(userId);
        }
    }


    /**
     * 删除redis中秒杀相关信息
     *
     * @param date 在此日期之前的数据要删除
     */
    @Override
    public void removeSeckillGoodsFromRedis(Date date) {
        //获取要删除的商品
        Map<String, SeckillGoods> seckillGoodsMap = redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).entries();
        if (seckillGoodsMap == null) {
            return;
        }

        //删除商品信息
        List<Long> skuIds = seckillGoodsMap.entrySet().stream()
                .filter(entry -> DateUtil.dateCompare(entry.getValue().getEndTime(), date))
                .map(Map.Entry::getKey).map(Long::parseLong).collect(Collectors.toList());
        skuIds.forEach(skuId -> redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).delete(skuId));

        //删除map状态位
        skuIds.forEach(skuId -> CacheHelper.remove(skuId.toString()));

        //删除预下单信息
        CompletableFuture<Void> orderFuture = CompletableFuture.runAsync(() -> {
            Set<String> keys = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).keys();
            if (keys != null) {
                keys.stream().map(key -> key.split(":")).filter(array -> skuIds.contains(array[1]))
                        .forEach(key -> redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(key));
            }
        });

        //删除订单信息
        CompletableFuture<Void> orderUserFuture = CompletableFuture.runAsync(() -> {
            Set<String> keys = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).keys();
            if (keys != null) {
                keys.stream().map(key -> key.split(":")).filter(array -> skuIds.contains(array[1]))
                        .forEach(key -> redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).delete(key));
            }
        });

        //删除剩余数量
        CompletableFuture<Void> stockFuture = CompletableFuture.runAsync(() -> {
            skuIds.stream().map(skuId -> RedisConst.SECKILL_STOCK_PREFIX + skuId)
                    .filter(key -> redisTemplate.hasKey(key))
                    .forEach(key -> redisTemplate.delete(key));
        });

        //更新数据库信息
        CompletableFuture<Void> dbFuture = CompletableFuture.runAsync(() -> {
            QueryWrapper<SeckillGoods> wrapper = new QueryWrapper<>();
            wrapper.in("sku_id", skuIds).le("end_time", date).eq("status", 1);
            SeckillGoods seckillGoods = new SeckillGoods();
            seckillGoods.setStatus("2");
            seckillGoodsMapper.update(seckillGoods, wrapper);
        });

        CompletableFuture.allOf(orderFuture, orderUserFuture, stockFuture).join();
    }

    /**
     * 更新数据库库存
     */
    private void updateStockDB(SeckillGoods seckillGoods) {
        Long size = redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX + seckillGoods.getSkuId()).size();
        if (size % 2 == 0) {
            seckillGoods.setStockCount(size.intValue());
            seckillGoodsMapper.updateById(seckillGoods);
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(seckillGoods.getSkuId().toString(), seckillGoods);
        }
    }

    public boolean lockTrade(String userId) {
        String tradeNoKey = getTradeNoKey(userId);
        String uuid = UUID.randomUUID().toString().replaceAll("-", "");
        return redisTemplate.boundValueOps(tradeNoKey).setIfAbsent(uuid, 1, TimeUnit.DAYS);
    }

    public void unlock(String userId) {
        String tradeNoKey = getTradeNoKey(userId);
        redisTemplate.delete(tradeNoKey);
    }

    private String getTradeNoKey(String userId) {
        return RedisConst.USER_KEY_PREFIX + userId + ":tradeNo";
    }

}
