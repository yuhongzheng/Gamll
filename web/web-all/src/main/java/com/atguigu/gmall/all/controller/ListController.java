package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchAttr;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ListController {
    @Autowired
    private ListFeignClient listFeignClient;

    @GetMapping("list.html")
    public String search(SearchParam searchParam, Model model) {
        Result<Map> result = listFeignClient.list(searchParam);
        //拼接上次搜索 URL
        String urlParam = getUrlParam(searchParam);
        //处理商标数据
        String trademark = getTradeMark(searchParam);
        List<SearchAttr> propsParamList = getPropsParamList(searchParam);
        Map orderMap = getOrderMap(searchParam);

        model.addAttribute("orderMap", orderMap);
        model.addAttribute("urlParam", urlParam);
        model.addAttribute("propsParamList", propsParamList);
        model.addAttribute("trademark", trademark);
        model.addAttribute("searchParam", searchParam);
        model.addAllAttributes(result.getData());
        return "list/index";
    }

    private Map getOrderMap(SearchParam searchParam) {
        Map map = new HashMap();
        if (!StringUtils.isEmpty(searchParam.getOrder())) {
            String[] split = searchParam.getOrder().split(":");
            if (split.length == 2) {
                map.put("type", split[0]);
                map.put("sort", split[1]);
            }
        }else{
            map.put("type", "1");
            map.put("sort", "asc");
        }

        return map;
    }

    private String getTradeMark(SearchParam searchParam) {
        if (!StringUtils.isEmpty(searchParam.getTrademark())) {
            String[] split = searchParam.getTrademark().split(":");
            if (split.length == 2) {
                return split[1];
            }
        }

        return null;
    }

    private List<SearchAttr> getPropsParamList(SearchParam searchParam) {
        if (searchParam.getProps() == null) {
            return new ArrayList<>();
        }

        return Arrays.stream(searchParam.getProps())
                .map(prop -> prop.split(":"))
                .filter(split -> split.length == 3)
                .map(split -> new SearchAttr(Long.parseLong(split[0]), split[1], split[2]))
                .collect(Collectors.toList());
    }

    private String getUrlParam(SearchParam searchParam) {
        //list.html?category3Id=61&props=
        ArrayList<String> paramList = new ArrayList<>();
        if (!StringUtils.isEmpty(searchParam.getCategory1Id())) {
            paramList.add("category1Id=" + searchParam.getCategory1Id());
        }
        if (!StringUtils.isEmpty(searchParam.getCategory2Id())) {
            paramList.add("category2Id=" + searchParam.getCategory2Id());
        }
        if (!StringUtils.isEmpty(searchParam.getCategory3Id())) {
            paramList.add("category3Id=" + searchParam.getCategory3Id());
        }


        if (!StringUtils.isEmpty(searchParam.getKeyword())) {
            paramList.add("keyword=" + searchParam.getKeyword());
        }

        if (!StringUtils.isEmpty(searchParam.getTrademark())) {
            paramList.add("trademark=" + searchParam.getTrademark());
        }

        if (searchParam.getProps() != null && searchParam.getProps().length > 0) {
            Arrays.stream(searchParam.getProps()).forEach(prop -> paramList.add("props=" + prop));
        }

        return "list.html?" + String.join("&", paramList);
    }

}
