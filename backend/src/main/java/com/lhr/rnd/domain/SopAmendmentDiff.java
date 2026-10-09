package com.lhr.rnd.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.lhr.rnd.api.BusinessException;
import java.math.BigDecimal;
import java.util.*;

/** Stable entity keys keep insertions and moves from repainting unrelated rows. */
public final class SopAmendmentDiff {
 public record Difference(String entityId,String fieldPath,String type,String before,String after,boolean derived){}
 public List<Difference> compare(JsonNode before,JsonNode after){
  var result=new ArrayList<Difference>();
  for(var field:List.of("balanceToleranceKg","majorProcesses","batchYieldPercent"))walk(before.get(field),after.get(field),"/"+field,"plan",false,false,result);
  return List.copyOf(result);
 }
 private void walk(JsonNode a,JsonNode b,String path,String entity,boolean numeric,boolean derived,List<Difference> result){
  derived=derived||path.endsWith("/yield")||path.endsWith("/batchYieldPercent");
  if(absent(a)&&absent(b))return;
  if(absent(a)||absent(b)){result.add(new Difference(entity,path,absent(a)?"ADDED":"REMOVED",value(a),value(b),derived));return;}
  if(a.isArray()&&b.isArray()){
   var left=index(a);var right=index(b);var keys=new LinkedHashSet<>(left.keySet());keys.addAll(right.keySet());
   for(var key:keys)walk(left.get(key),right.get(key),path+"/"+escape(key),key,false,derived,result);
  }else if(a.isObject()&&b.isObject()){
   var keys=new TreeSet<String>();a.fieldNames().forEachRemaining(keys::add);b.fieldNames().forEachRemaining(keys::add);
   boolean number="NUMBER".equals(b.path("valueType").asText());
   for(var key:keys)walk(a.get(key),b.get(key),path+"/"+escape(key),entity,number&&List.of("actualValue","lowerLimit","upperLimit").contains(key),derived,result);
  }else if(!equal(a,b,numeric))result.add(new Difference(entity,path,path.endsWith("/sequence")?"MOVED":"CHANGED",value(a),value(b),derived));
 }
 private Map<String,JsonNode> index(JsonNode array){
  var result=new LinkedHashMap<String,JsonNode>();
  for(var item:array){String key=item.path("id").asText("");if(key.isBlank())key=item.path("key").asText("");
   if(key.isBlank()||result.putIfAbsent(key,item)!=null)throw new BusinessException("SOP_ENTITY_ID_INVALID","修订条目的标识不能为空或重复");}
  return result;
 }
 private boolean equal(JsonNode a,JsonNode b,boolean numeric){
  if(a.isNumber()&&b.isNumber()||numeric){try{return new BigDecimal(a.asText()).compareTo(new BigDecimal(b.asText()))==0;}catch(NumberFormatException ignored){}}
  return a.equals(b);
 }
 private boolean absent(JsonNode n){return n==null||n.isNull();}
 private String value(JsonNode n){return absent(n)?null:n.isValueNode()?n.asText():n.toString();}
 private String escape(String key){return key.replace("~","~0").replace("/","~1");}
}
