package com.atguigu.gmall.model.list;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchAttr {
    // 平台属性Id
    @Field(type = FieldType.Long)
    private Long attrId;
    // 平台属性值名称
    @Field(type = FieldType.Keyword)
    private String attrValue;
    // 平台属性名
    @Field(type = FieldType.Keyword)
    private String attrName;


}
