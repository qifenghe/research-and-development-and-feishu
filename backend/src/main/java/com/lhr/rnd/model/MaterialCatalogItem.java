package com.lhr.rnd.model;
public record MaterialCatalogItem(String id,String code,String name,String category,String specification,String unit,String status,int version,String classificationId) {
 public MaterialCatalogItem(String id,String code,String name,String category,String specification,String unit,String status,int version){this(id,code,name,category,specification,unit,status,version,null);}
}
