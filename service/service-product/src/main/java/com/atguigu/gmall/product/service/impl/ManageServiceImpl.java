package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.common.cache.GmallCache;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.list.SearchAttr;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ManageServiceImpl implements ManageService {
    @Autowired
    BaseCategory1Mapper category1Mapper;
    @Autowired
    BaseCategory2Mapper category2Mapper;
    @Autowired
    BaseCategory3Mapper category3Mapper;
    @Autowired
    BaseAttrInfoMapper attrInfoMapper;
    @Autowired
    BaseAttrValueMapper attrValueMapper;
    @Autowired
    SpuInfoMapper spuInfoMapper;
    @Autowired
    BaseSaleAttrMapper saleAttrMapper;
    @Autowired
    SpuImageMapper spuImageMapper;
    @Autowired
    SpuSaleAttrMapper spuSaleAttrMapper;
    @Autowired
    SpuSaleAttrValueMapper spuSaleAttrValueMapper;
    @Autowired
    SkuInfoMapper skuInfoMapper;
    @Autowired
    SkuImageMapper skuImageMapper;
    @Autowired
    SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    @Autowired
    SkuAttrValueMapper skuAttrValueMapper;
    @Autowired
    BaseCategoryViewMapper categoryViewMapper;
    @Autowired
    BaseTrademarkMapper baseTrademarkMapper;


    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    RedissonClient redissonClient;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Override
    public List<BaseCategory1> getCategory1() {
        return category1Mapper.selectList(null);
    }

    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        return category2Mapper.selectList(new QueryWrapper<BaseCategory2>().eq("category1_id", category1Id));
    }

    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        return category3Mapper.selectList(new QueryWrapper<BaseCategory3>().eq("category2_id", category2Id));
    }

    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        return attrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }

    @Override
    public List<BaseAttrValue> getAttrValueList(Long attrId) {
        return attrValueMapper.selectList(new QueryWrapper<BaseAttrValue>()
                .eq("attr_id", attrId).select("id", "value_name"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveAttrInfo(BaseAttrInfo attrInfo) {
        //如果有id，则为修改否则为新增平台属性
        if(attrInfo.getId() == null){
            attrInfoMapper.insert(attrInfo);
        }

        //删除所有属性值后新增
        attrValueMapper.delete(new QueryWrapper<BaseAttrValue>().eq("attr_id", attrInfo.getId()));

        if(attrInfo.getAttrValueList() != null) {
            for (BaseAttrValue attrValue : attrInfo.getAttrValueList()) {
                attrValue.setAttrId(attrInfo.getId());
                attrValueMapper.insert(attrValue);
            }
        }
    }

    @Override
    public void getSpuInfoPage(Page<SpuInfo> page, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category3_id", spuInfo.getCategory3Id());
        queryWrapper.orderByDesc("id");
        spuInfoMapper.selectPage(page, queryWrapper);
    }

    @Override
    public List<BaseSaleAttr> getBaseSaleAttrList() {
        return saleAttrMapper.selectList(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSpuInfo(SpuInfo spuInfo) {
        //spu_info  spu_image  spu_sale_attr  spu_sale_attr_value
        spuInfoMapper.insert(spuInfo);

        for (SpuImage image : spuInfo.getSpuImageList()) {
            image.setSpuId(spuInfo.getId());
            spuImageMapper.insert(image);
        }

        for (SpuSaleAttr spuSaleAttr : spuInfo.getSpuSaleAttrList()) {
            spuSaleAttr.setSpuId(spuInfo.getId());
            spuSaleAttrMapper.insert(spuSaleAttr);

            for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttr.getSpuSaleAttrValueList()) {
                spuSaleAttrValue.setSpuId(spuInfo.getId());
                spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                spuSaleAttrValueMapper.insert(spuSaleAttrValue);
            }
        }
    }

    @Override
    public IPage<SkuInfo> getSpuImageList(Integer current, Integer limit) {
        return skuInfoMapper.selectPage(new Page(current, limit), null);
    }

    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        return spuImageMapper.selectList(new QueryWrapper<SpuImage>().eq("spu_id", spuId));
    }

    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        return spuSaleAttrMapper.getSpuSaleAttrListBySpuId(spuId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveSkuInfo(SkuInfo skuInfo) {
        //sku_info  sku_image  sku_attr_value  sku_sale_attr_value
        skuInfoMapper.insert(skuInfo);

        if(skuInfo.getSkuImageList() != null){
            for (SkuImage skuImage : skuInfo.getSkuImageList()) {
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }

        if(skuInfo.getSkuAttrValueList() != null){
            for (SkuAttrValue skuAttrValue : skuInfo.getSkuAttrValueList()) {
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }

        if(skuInfo.getSkuSaleAttrValueList() != null){
            for (SkuSaleAttrValue skuSaleAttrValue : skuInfo.getSkuSaleAttrValueList()) {
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
                skuSaleAttrValueMapper.insert(skuSaleAttrValue);
            }
        }
    }

    @Override
    public void onSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        this.skuInfoMapper.updateById(skuInfo);
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_UPPER, skuId);
    }

    @Override
    public void cancelSale(Long skuId) {
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(0);
        this.skuInfoMapper.updateById(skuInfo);
        rabbitTemplate.convertAndSend(MqConst.EXCHANGE_DIRECT_GOODS, MqConst.ROUTING_GOODS_LOWER, skuId);
    }

    @Override
    @GmallCache(prefix = RedisConst.SKUKEY_PREFIX)
    public SkuInfo getSkuInfo(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if(skuInfo == null){
            return new SkuInfo();
        }
        skuInfo.setSkuImageList(skuImageMapper.selectList(new QueryWrapper<SkuImage>().eq("sku_id", skuId)));
        return skuInfo;
    }

    @Override
    @GmallCache(prefix = "getCategoryViewByCategory3Id:")
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return categoryViewMapper.selectById(category3Id);
    }

    @Override
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if(null != skuInfo) {
            return skuInfo.getPrice();
        }
        return new BigDecimal("0");
    }

    @Override
    @GmallCache(prefix = "getSpuSaleAttrListCheckBySku:")
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId, spuId);
    }

    @Override
    @GmallCache(prefix = "getSkuValueIdsMap:")
    public Map getSkuValueIdsMap(Long spuId) {
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);
        Map map = new HashMap();
        for (Map innerMap : mapList) {
            map.put(innerMap.get("value_ids"), innerMap.get("sku_id"));
        }
        return map;
    }

    @Override
    @Deprecated
    public SkuInfo getSkuInfoRedisson(Long skuId){
        if(skuId == null){
            return new SkuInfo();
        }

        String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
        SkuInfo skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
        if(skuInfo != null){
            return skuInfo;
        }

        String lockKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKULOCK_SUFFIX;
        RLock lock = redissonClient.getLock(lockKey);
        try {
            boolean success = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);
            if(success){
                try {
                    skuInfo = getSkuInfo(skuId);
                    redisTemplate.opsForValue().set(skuKey, skuInfo);
                } catch (Exception e) {
                    e.printStackTrace();
                }finally {
                    lock.unlock();
                }
            }else{
                skuInfo = getSkuInfoRedisson(skuId);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        return skuInfo;
    }

    @Override
    @GmallCache(prefix = "category")
    public List<Map> getBaseCategoryList() {
        return attrInfoMapper.selectBaseCategoryList();
    }

    @Override
    public BaseTrademark getTrademarkByTmId(Long tmId) {
        return baseTrademarkMapper.selectById(tmId);
    }

    @Override
    public List<SearchAttr> getAttrList(Long skuId) {
        return attrInfoMapper.selectSearchAttrListBySkuId(skuId);
    }

}
