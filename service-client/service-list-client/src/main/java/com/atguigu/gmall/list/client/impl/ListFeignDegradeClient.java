package com.atguigu.gmall.list.client.impl;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ListFeignDegradeClient implements ListFeignClient {
    @Override
    public Result createIndex() {
        return null;
    }

    @Override
    public Result upperGoods(Long skuId) {
        return null;
    }

    @Override
    public Result lowerGoods(Long skuId) {
        return null;
    }

    @Override
    public Result list(SearchParam searchParam){
        return null;
    }

    @Override
    public Result incrHotScore(Long skuId) {
        return null;
    }
}
