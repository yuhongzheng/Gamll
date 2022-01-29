package com.atguigu.gmall.item.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.item.service.ItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/item")
public class ItemApiController {
    @Autowired
    ItemService itemService;

    @GetMapping("{skuId}")
    public Result getItem(@PathVariable("skuId") Long skuId){
        return Result.ok(itemService.getBySkuId(skuId));
    }
}
