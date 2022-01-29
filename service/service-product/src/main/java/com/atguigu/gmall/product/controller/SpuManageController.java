package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.SpuInfo;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController // @ResponseBody + @Controller
@RequestMapping("admin/product")
public class SpuManageController {
    //http://api.gmall.com/admin/product/1/10?category3Id=61

    @Autowired
    private ManageService manageService;

    @GetMapping("{page}/{size}")
    public Result getSpuInfoPage(@PathVariable Long page,
                                 @PathVariable Long size,
                                 SpuInfo spuInfo) {
        // 创建一个Page 对象
        Page<SpuInfo> spuInfoPage = new Page<>(page, size);
        // 获取数据
        manageService.getSpuInfoPage(spuInfoPage, spuInfo);
        // 将获取到的数据返回即可！
        return Result.ok(spuInfoPage);
    }

    @GetMapping("baseSaleAttrList")
    public Result baseSaleAttrList() {
        return Result.ok(manageService.getBaseSaleAttrList());
    }

    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){
        manageService.saveSpuInfo(spuInfo);
        return Result.ok();
    }

    @GetMapping("spuSaleAttrList/{spuId}")
    public Result getSpuSaleAttrList(@PathVariable Long spuId){
        return Result.ok(this.manageService.getSpuSaleAttrList(spuId));
    }
}
