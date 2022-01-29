package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.list.repository.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {
    @Autowired
    ProductFeignClient productFeignClient;

    @Autowired
    GoodsRepository goodsRepository;

    @Autowired
    RestHighLevelClient restHighLevelClient;

    @Autowired
    RedisTemplate redisTemplate;


    @Override
    public void upperGoods(Long skuId) {
        Goods goods = new Goods();
        goods.setCreateTime(new Date());

        CompletableFuture<SkuInfo> skuInfoFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            goods.setId(skuId);
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setTitle(skuInfo.getSkuName());
            return skuInfo;
        });

        CompletableFuture<Void> priceFuture = CompletableFuture.runAsync(() -> {
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            goods.setPrice(skuPrice.doubleValue());
        });

        CompletableFuture<Void> tmFuture = skuInfoFuture.thenAcceptAsync(skuInfo -> {
            BaseTrademark trademark = productFeignClient.getTrademark(skuInfo.getTmId());
            goods.setTmId(trademark.getId());
            goods.setTmName(trademark.getTmName());
            goods.setTmLogoUrl(trademark.getLogoUrl());
        });

        CompletableFuture<Void> categoryViewFuture = skuInfoFuture.thenAcceptAsync(skuInfo -> {
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            goods.setCategory1Id(categoryView.getCategory1Id());
            goods.setCategory1Name(categoryView.getCategory1Name());
            goods.setCategory2Id(categoryView.getCategory2Id());
            goods.setCategory2Name(categoryView.getCategory2Name());
            goods.setCategory3Id(categoryView.getCategory3Id());
            goods.setCategory3Name(categoryView.getCategory3Name());
        });

        CompletableFuture<Void> searchAttrListFuture = CompletableFuture.runAsync(() -> {
            List<SearchAttr> attrList = productFeignClient.getAttrList(skuId);
            goods.setAttrs(attrList);
        });

        CompletableFuture.allOf(skuInfoFuture, priceFuture, tmFuture, categoryViewFuture, searchAttrListFuture).join();

        goodsRepository.save(goods);
    }

    @Override
    public void lowerGoods(Long skuId) {
        this.goodsRepository.deleteById(skuId);
    }

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws IOException {
        SearchRequest searchRequest = buildQueryDsl(searchParam);
        SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        System.out.println("response = " + response);
        SearchResponseVo searchResponseVo = parseSearchResult(response);
        searchResponseVo.setPageSize(searchParam.getPageSize());
        searchResponseVo.setPageNo(searchParam.getPageNo());
        long totalPages = (searchResponseVo.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();
        searchResponseVo.setTotalPages(totalPages);
        return searchResponseVo;
    }

    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

        //检索条件
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //关键字
        if(!StringUtils.isEmpty(searchParam.getKeyword())){
            MatchQueryBuilder titleBuilder = QueryBuilders.matchQuery("title", searchParam.getKeyword()).operator(Operator.AND);
            boolQueryBuilder.must(titleBuilder);
        }

        //分类
        if(searchParam.getCategory1Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id", searchParam.getCategory1Id()));
        }
        if(searchParam.getCategory2Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id", searchParam.getCategory2Id()));
        }
        if(searchParam.getCategory3Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id", searchParam.getCategory3Id()));
        }

        //商标 2:华为
        if (!StringUtils.isEmpty(searchParam.getTrademark())) {
            String[] split = searchParam.getTrademark().split(":");
            if (split.length == 2) {
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId", split[0]));
            }
        }

        //平台属性 [23:4G:运行内存]
        if(searchParam.getProps() != null && searchParam.getProps().length > 0){
            Arrays.stream(searchParam.getProps()).forEach(prop -> {
                String[] split = prop.split(":");
                if(split.length == 3){
                    BoolQueryBuilder attrBoolQueryBuilder = QueryBuilders.boolQuery();
                    attrBoolQueryBuilder.filter(QueryBuilders.termQuery("attrs.attrId", split[0]));
                    attrBoolQueryBuilder.filter(QueryBuilders.termQuery("attrs.attrValue", split[1]));
                    NestedQueryBuilder attrNestQueryBuilder = QueryBuilders.nestedQuery("attrs", attrBoolQueryBuilder, ScoreMode.None);
                    boolQueryBuilder.filter(attrNestQueryBuilder);
                }
            });
        }

        //查询
        searchSourceBuilder.query(boolQueryBuilder);

        //高亮
        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.preTags("<span style=color:red>");
            highlightBuilder.postTags("</span>");
            highlightBuilder.field("title");
            searchSourceBuilder.highlighter(highlightBuilder);
        }

        //排序
        String[] orderSplit = {"1", "asc"};
        if (!StringUtils.isEmpty(searchParam.getOrder())) {
            orderSplit =  searchParam.getOrder().split(":");
        }
        String orderField = null;
        SortOrder order = orderSplit[1].toLowerCase().equals("asc")? SortOrder.ASC: SortOrder.DESC;
        switch (orderSplit[0]){
            case "1":
                orderField = "hotScore";
                break;
            case "2":
                orderField = "price";
                break;
        }

        searchSourceBuilder.sort(orderField, order);

        //分页
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        //聚合
        //1.商标
        TermsAggregationBuilder tmAggregationBuilder = AggregationBuilders.terms("tmAgg").field("tmId");
        tmAggregationBuilder.subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"));
        tmAggregationBuilder.subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl"));
        searchSourceBuilder.aggregation(tmAggregationBuilder);

        //2.平台属性值
        NestedAggregationBuilder nestedAggregationBuilder = AggregationBuilders.nested("attrsAgg", "attrs");
        TermsAggregationBuilder attrIdAgg = AggregationBuilders.terms("attrIdAgg").field("attrs.attrId");
        attrIdAgg.subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                .subAggregation((AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue")));
        nestedAggregationBuilder.subAggregation(attrIdAgg);
        searchSourceBuilder.aggregation(nestedAggregationBuilder);

        //属性值结果过滤
        searchSourceBuilder.fetchSource(new String[]{"id","defaultImg","title","price"}, null);

        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);
        System.out.println("dsl: " + searchSourceBuilder.toString());
        return searchRequest;
    }

    private SearchResponseVo parseSearchResult(SearchResponse response) {
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        searchResponseVo.setTotal(response.getHits().getTotalHits());

        //goodsList
        //注意高亮
        SearchHit[] hits = response.getHits().getHits();
        List<Goods> goodsList = Arrays.stream(hits).map(hit -> {
            Goods goods = JSON.parseObject(hit.getSourceAsString(), Goods.class);
            //设置高亮
            if(hit.getHighlightFields().get("title") != null){
                String title = hit.getHighlightFields().get("title").getFragments()[0].toString();
                goods.setTitle(title);
            }
            return goods;
        }).collect(Collectors.toList());
        searchResponseVo.setGoodsList(goodsList);


        Map<String, Aggregation> aggMap = response.getAggregations().asMap();

        //tmList
        ParsedLongTerms tmLongTerms = (ParsedLongTerms) aggMap.get("tmAgg");
        List<SearchResponseTmVo> tmList = tmLongTerms.getBuckets().stream().map(bucket -> {
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            searchResponseTmVo.setTmId(((Terms.Bucket) bucket).getKeyAsNumber().longValue());
            Map<String, Aggregation> tmAggMap = ((Terms.Bucket) bucket).getAggregations().asMap();
            ParsedStringTerms tmNameAgg = (ParsedStringTerms) tmAggMap.get("tmNameAgg");
            searchResponseTmVo.setTmName(tmNameAgg.getBuckets().get(0).getKeyAsString());
            ParsedStringTerms tmUrlLogAgg = (ParsedStringTerms) tmAggMap.get("tmLogoUrlAgg");
            searchResponseTmVo.setTmLogoUrl(tmUrlLogAgg.getBuckets().get(0).getKeyAsString());
            return searchResponseTmVo;
        }).collect(Collectors.toList());
        searchResponseVo.setTrademarkList(tmList);

        //attrsList
        ParsedNested attrAgg = (ParsedNested) aggMap.get("attrsAgg");
        ParsedLongTerms attrIdAgg = (ParsedLongTerms) attrAgg.getAggregations().asMap().get("attrIdAgg");
        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map(idBucket -> {
            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
            searchResponseAttrVo.setAttrId(((Terms.Bucket) idBucket).getKeyAsNumber().longValue());
            Map<String, Aggregation> attrIdAggMap = ((Terms.Bucket) idBucket).getAggregations().asMap();
            //name aggregation
            ParsedStringTerms attrNameAgg = (ParsedStringTerms) attrIdAggMap.get("attrNameAgg");
            searchResponseAttrVo.setAttrName(attrNameAgg.getBuckets().get(0).getKeyAsString());
            //value aggregation list
            ParsedStringTerms attrValueAgg = (ParsedStringTerms) attrIdAggMap.get("attrValueAgg");
            searchResponseAttrVo.setAttrValueList(
                    attrValueAgg.getBuckets().stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
            return searchResponseAttrVo;
        }).collect(Collectors.toList());
        searchResponseVo.setAttrsList(attrsList);

        return searchResponseVo;
    }

    @Override
    public void incrHotScore(Long skuId) {
        //判断redis是否存在key
        String key = "hotScore", member = "sku:" + skuId;
        Double score = redisTemplate.opsForZSet().score(key, member);
        if (score == null) {
            Optional<Goods> optional = goodsRepository.findById(skuId);
            if (!optional.isPresent()) {
                throw new RuntimeException("商品【" + skuId + "】未上架");
            }
            Goods goods = optional.get();
            redisTemplate.opsForZSet().add(key, member, goods.getHotScore().doubleValue());
        }

        score = redisTemplate.opsForZSet().incrementScore(key, member, 1);
        if (score % 10 == 0) {
            Optional<Goods> optional = goodsRepository.findById(skuId);
            if (!optional.isPresent()) {
                throw new RuntimeException("商品【" + skuId + "】未上架");
            }
            Goods goods = optional.get();
            goods.setHotScore(score.longValue());
            goodsRepository.save(goods);
        }
    }
}
