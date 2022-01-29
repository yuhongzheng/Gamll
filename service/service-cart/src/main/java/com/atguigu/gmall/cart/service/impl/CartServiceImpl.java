package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    CartInfoMapper cartInfoMapper;

    @Autowired
    CartAsyncService cartAsyncService;

    @Autowired
    ProductFeignClient productFeignClient;

    /**
     * 1. 使用Redis做缓存，使用MYSQL做持久化。缓存使用hash类型，使用userId区分key，使用skuId区分field
     * 2. 如果缓存为空，则加载缓存。
     * 3. 如果添加过商品，则商品数量增加，更新数据库
     * 4. 如果没有，则新建购物项，插入数据库
     * 5. 将购物项添加到缓存
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        String cartKey = getCartKey(userId);
        if (!redisTemplate.hasKey(cartKey)) {
            this.loadCartInfoToRedis(userId);
        }

        //购物项是否存在
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(cartKey, skuId.toString());
        if (cartInfo == null) {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo = new CartInfo(userId, skuId, skuInfo.getPrice(), skuNum, skuInfo.getSkuDefaultImg(),
                    skuInfo.getSkuName(), 1, new Timestamp(new Date().getTime()),
                    new Timestamp(new Date().getTime()), skuInfo.getPrice(), null);

            cartAsyncService.saveCartInfo(cartInfo);
        } else {
            cartInfo.setSkuNum(cartInfo.getSkuNum() + skuNum);
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(skuId));
            cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
            cartAsyncService.updateCartInfo(cartInfo);
        }

        redisTemplate.opsForHash().put(cartKey, skuId.toString(), cartInfo);
        redisTemplate.expire(cartKey, 7, TimeUnit.DAYS);
    }

    /**
     * 1. 判断是否需要合并数据
     * 2. 从缓存中获取数据，不存在则加载到缓存
     */
    @Override
    public List<CartInfo> cartList(String userId, String userTempId) {
        if (!StringUtils.isEmpty(userId) && !StringUtils.isEmpty(userTempId) && redisTemplate.hasKey(getCartKey(userTempId))) {
            mergeToCartList(userId, userTempId);
        }

        return StringUtils.isEmpty(userId) ? cartList(userTempId) : cartList(userId);
    }

    @Override
    public void checkCart(String userId, String skuId, Integer isChecked) {
        //异步更新数据库
        cartAsyncService.checkCart(userId, skuId, isChecked);

        //更新缓存
        loadCartInfoToRedisIfNotExists(userId, skuId, true);
        CartInfo cartInfo = (CartInfo) redisTemplate.opsForHash().get(getCartKey(userId), skuId.toString());
        cartInfo.setIsChecked(isChecked);
        redisTemplate.opsForHash().put(getCartKey(userId), skuId.toString(), cartInfo);
    }

    @Override
    public void deleteCart(String userId, String skuId) {
        cartAsyncService.deleteCart(userId, skuId);

        loadCartInfoToRedisIfNotExists(userId, skuId, true);
        redisTemplate.opsForHash().delete(getCartKey(userId), skuId.toString());
    }

    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        String cartKey = getCartKey(userId);
        if (!redisTemplate.hasKey(cartKey)) {
            loadCartInfoToRedis(userId);
        }

        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(cartKey);
        return cartInfoList.stream().filter(cartInfo -> cartInfo.getIsChecked() == 1).collect(Collectors.toList());
    }

    /**
     * 1. 如果缓存没有， 加载
     * 2. 保持时间降序
     */
    private List<CartInfo> cartList(String userId) {
        if (!redisTemplate.hasKey(userId)) {
            this.loadCartInfoToRedis(userId);
        }

        List<CartInfo> cartInfoList = redisTemplate.opsForHash().values(getCartKey(userId));
        cartInfoList.sort((c1, c2) -> DateUtil.truncatedCompareTo(c2.getCreateTime(), c1.getCreateTime(), Calendar.SECOND));
        return cartInfoList;
    }


    private void loadCartInfoToRedisIfNotExists(String userId, String skuId, boolean resetExpire) {
        String cartKey = getCartKey(userId);
        if (!redisTemplate.hasKey(cartKey)) {
            loadCartInfoToRedis(userId);
        }
        if(resetExpire){
            redisTemplate.expire(cartKey, 7, TimeUnit.DAYS);
        }
    }

    /**
     * 1. 临时用户只查缓存
     * 2. 合并有新建和增加件数两种情况
     * 3. 处理选中逻辑
     * 4. 处理更新时间
     * 5. 需要更新数据库和缓存
     */
    @Transactional(rollbackFor = Exception.class)
    protected void mergeToCartList(String userId, String userTempId) {
        // skuId -> CartInfo
        List<CartInfo> tempList = redisTemplate.opsForHash().values(getCartKey(userTempId));
        if (!redisTemplate.hasKey(getCartKey(userId))) {
            loadCartInfoToRedis(userId);
        }

        Map<String, CartInfo> cartInfoMap = redisTemplate.opsForHash().entries(getCartKey(userId));
        for (CartInfo temp : tempList) {
            //删除临时购物项

            CartInfo cartInfo = cartInfoMap.get(temp.getSkuId().toString());
            if (cartInfo == null) {
                //新商品
                temp.setUserId(userId);
                cartInfoMapper.insert(temp);
                cartInfo = temp;
            }else{
                //合并
                cartInfo.setSkuNum(cartInfo.getSkuNum() + temp.getSkuNum());
                if (temp.getIsChecked() == 1) {
                    cartInfo.setIsChecked(1);
                }
                if (temp.getUpdateTime().compareTo(cartInfo.getUpdateTime()) > 0) {
                    cartInfo.setUpdateTime(temp.getUpdateTime());
                }
                cartInfoMapper.update(cartInfo, new QueryWrapper<CartInfo>().eq("user_id", userId).eq("sku_id", cartInfo.getSkuId()));
            }

            //更新缓存
            redisTemplate.opsForHash().put(getCartKey(userId), cartInfo.getSkuId().toString(), cartInfo);
        }

        deleteCartList(userTempId);
    }

    private void deleteCartList(String userId) {
        cartAsyncService.deleteCartList(userId);
        if (redisTemplate.hasKey(getCartKey(userId))) {
            redisTemplate.delete(getCartKey(userId));
        }
    }

    public void loadCartInfoToRedis(String userId) {
        String cartKey = getCartKey(userId);
        //先删除，再添加
        redisTemplate.delete(cartKey);

        List<CartInfo> cartInfoList = cartInfoMapper.selectList(new QueryWrapper<CartInfo>().
                eq("user_id", userId));
        cartInfoList.forEach(cartInfo -> {
            //更新价格
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
            redisTemplate.opsForHash().put(cartKey, cartInfo.getSkuId().toString(), cartInfo);
        });

        redisTemplate.expire(cartKey, 7, TimeUnit.DAYS);
    }


    private String getCartKey(String userId) {
        return RedisConst.USER_KEY_PREFIX + userId + RedisConst.USER_CART_KEY_SUFFIX;
    }

}
