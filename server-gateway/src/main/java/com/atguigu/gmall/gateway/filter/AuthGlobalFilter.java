package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import jodd.net.URLDecoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class AuthGlobalFilter implements GlobalFilter {
    @Autowired
    RedisTemplate redisTemplate;

    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Value("${authUrls.url}")
    private String authUrlsUrl;

    @Override
    /**
     * 1. 任何外部请求不能访问feign的接口方法，feign的方法寻找nacos来路由到微服务模块
     * 2. 处理需要鉴权的请求，处理 /api/-/auth/--的请求
     * 3. 判断是否token盗用
     * 4. 将userId放入请求头
     */
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        String uri = request.getURI().getPath();

        //访问内部方法
        if (antPathMatcher.match("/**/inner/**", uri)) {
            return out(exchange.getResponse(), ResultCodeEnum.PERMISSION);
        }

        String userId = getUserId(request);

        //盗取TOKEN
        if ("-1".equals(userId)) {
            return out(exchange.getResponse(), ResultCodeEnum.PERMISSION);
        }

        if (StringUtils.isEmpty(userId)) {
            if (antPathMatcher.match("/api/**/auth/**", uri)) {
                return out(exchange.getResponse(), ResultCodeEnum.LOGIN_AUTH);
            }

            for (String authUrl : authUrlsUrl.split(",")) {
                if (uri.contains(authUrl)) {
                    return out(exchange.getResponse(), ResultCodeEnum.LOGIN_AUTH);
                }
            }
        }

        //可以通过
        //放入USERID, USERTEMPID到请求头
        String userTempId = getHeaderOrCookie(request, "userTempId");
        if (StringUtils.isEmpty(userId) && StringUtils.isEmpty(userTempId)) {
            return chain.filter(exchange);
        }

        if (!StringUtils.isEmpty(userId)) {
            request.mutate().header("userId", userId).build();
        }
        if (!StringUtils.isEmpty(userTempId)) {
            request.mutate().header("userTempId", userTempId).build();
        }

        return chain.filter(exchange.mutate().request(request).build());
    }


    //以json串返回失败数据
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        Result<Object> result = Result.build(null, resultCodeEnum);
        byte[] bytes = JSON.toJSONString(result).getBytes(StandardCharsets.UTF_8);
        DataBuffer wrap = response.bufferFactory().wrap(bytes);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        return response.writeWith(Mono.just(wrap));
    }

    private String getHeaderOrCookie(ServerHttpRequest request, String key) {
        //首先从请求头中获取
        String value = request.getHeaders().getFirst(key);
        if (StringUtils.isEmpty(value)) {
            //为空则从Cookie中获取
            HttpCookie cookie = request.getCookies().getFirst(key);
            if (cookie != null) {
                value = URLDecoder.decode(cookie.getValue());
            }
        }

        return value;
    }

    private String getUserId(ServerHttpRequest request) {

        String token = getHeaderOrCookie(request, "token");

        //从redis中获取jsonObject
        String key = RedisConst.USER_LOGIN_KEY_PREFIX + token;
        String json = (String) redisTemplate.opsForValue().get(key);
        if (StringUtils.isEmpty(json)) {
            return null;
        }

        JSONObject jsonObject = JSON.parseObject(json, JSONObject.class);
        if (StringUtils.isEmpty(jsonObject.getString("userId"))) {
            return null;
        }

        if (!IpUtil.getGatwayIpAddress(request).equals(jsonObject.getString("ip"))) {
            return "-1";
        }

        return jsonObject.getString("userId");
    }


}
