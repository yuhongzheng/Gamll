package com.atguigu.gmall.list.client;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.impl.ListFeignDegradeClient;
import com.atguigu.gmall.model.list.Goods;
import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.IOException;

@FeignClient(value = "service-list", fallback = ListFeignDegradeClient.class)
public interface ListFeignClient {
    @GetMapping("/api/list/inner/createIndex")
    public Result createIndex();

    @GetMapping("/api/list/inner/upperGoods/{skuId}")
    public Result upperGoods(@PathVariable("skuId") Long skuId);

    @GetMapping("/api/list/inner/lowerGoods/{skuId}")
    public Result lowerGoods(@PathVariable("skuId") Long skuId);

    @PostMapping("/api/list")
    public Result list(@RequestBody SearchParam searchParam);

    @GetMapping("/api/list/inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable("skuId") Long skuId);
}
