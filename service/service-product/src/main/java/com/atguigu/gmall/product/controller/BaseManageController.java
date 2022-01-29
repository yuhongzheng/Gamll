package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.product.service.ManageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@Api(value = "aaa")
@RequestMapping("/admin/product")
public class BaseManageController {
    @Autowired
    ManageService manageService;

    @ApiOperation("查询所有一级分类")
    @GetMapping("getCategory1")
    public Result getCategory1(){
        return Result.ok(manageService.getCategory1());
    }

    @ApiOperation("查询所有二级分类，根据一级分类ID")
    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable("category1Id") Long category1Id){
        return Result.ok(manageService.getCategory2(category1Id));
    }

    @ApiOperation("查询所有三级分类，根据二级分类ID")
    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable("category2Id") Long category2Id){
        return Result.ok(manageService.getCategory3(category2Id));
    }

    @ApiOperation(value = "查询一级id、二级id和三级id的所有的平台标签和其所有的属性值列表")
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result attrInfoList(@PathVariable("category1Id") Long category1Id,
                               @PathVariable("category2Id") Long category2Id,
                               @PathVariable("category3Id") Long category3Id){

        return Result.ok(manageService.getAttrInfoList(category1Id, category2Id, category3Id));
    }

    @ApiOperation(value = "根据平台属性id回显所有平台属性值")
    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList(@PathVariable("attrId") Long attrId){
        return Result.ok(manageService.getAttrValueList(attrId));
    }

    @PostMapping("saveAttrInfo")
    @ApiOperation(value = "新增平台属性或者修改平台属性值")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo attrInfo){
        manageService.saveAttrInfo(attrInfo);
        return Result.ok();
    }
}
