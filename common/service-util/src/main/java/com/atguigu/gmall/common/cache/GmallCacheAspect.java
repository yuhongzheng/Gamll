package com.atguigu.gmall.common.cache;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.RedisConst;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Aspect
public class GmallCacheAspect {

    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    RedissonClient redissonClient;

    @Around("@annotation(com.atguigu.gmall.common.cache.GmallCache)")
    @SneakyThrows
    public Object cacheAroundAdvice(ProceedingJoinPoint joinPoint) {
        //获取前缀
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String prefix = signature.getMethod().getAnnotation(GmallCache.class).prefix();

        //拼写key
        Object[] args = joinPoint.getArgs();
        String valueKey = prefix + Arrays.stream(args).map(Object::toString).collect(Collectors.joining("-"));

        //查看redis缓存
        /*
            如果放入空 Object对象，则不能正常从redis序列化
         */
        Object result = cacheHit(valueKey, signature);

        if (result != null) {
            return result;
        }

        //加锁
        String lockKey = valueKey + RedisConst.SKULOCK_SUFFIX;
        RLock lock = redissonClient.getLock(lockKey);
        boolean success = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
        if (success) {
            try {
                //获取数据
                result = joinPoint.proceed();

                //存入redis
                if (result == null) {
                    result = new Object();
                    redisTemplate.opsForValue().set(valueKey, JSON.toJSONString(result), RedisConst.SKUKEY_TEMPORARY_TIMEOUT, TimeUnit.SECONDS);
                } else {
                    redisTemplate.opsForValue().set(valueKey, JSON.toJSONString(result), RedisConst.SKUKEY_TIMEOUT, TimeUnit.SECONDS);
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            } finally {
                //释放锁
                lock.unlock();
            }
        } else {
            result = cacheAroundAdvice(joinPoint);
        }

        return result;
    }

    /*
        如果放入空 Object对象，则不能正常从redis序列化, 所以需要获取
        使用JSON.toJSONString方法写入的redis字符串只包含属性信息，不包含类别信息，redis字符串如果是{}，则各个属性为空。
     */
    private Object cacheHit(String key, MethodSignature signature) {
        String json = (String) redisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(json)) {
            return null;
        } else {
            return JSON.parseObject(json, signature.getReturnType());
        }
    }
}
