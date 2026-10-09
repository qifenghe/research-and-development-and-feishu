export interface MaterialCatalogItem { id:string; code:string; name:string; category:string; specification?:string; unit:string; status:string; version:number; classificationId?:string }
export interface MaterialClassification { id:string; parentId?:string|null; name:string; businessType:string; sortOrder:number; status:string; version:number }
export interface MaterialIdentity { id?:string; materialCode?:string; materialName?:string; catalogMaterialId?:string; catalogSpecification?:string; catalogUnit?:string; formulaMaterialId?:string; sourceStepOutputId?:string; refreshCatalogSnapshot?:boolean; selectionToken?:string }
let selectionSerial=0;
export function selectCatalogMaterial<T extends MaterialIdentity>(row:T,item:MaterialCatalogItem,purpose:'ingredient'|'packaging'='ingredient') {
 if(item.status!=='ACTIVE'||(purpose==='packaging'?item.category!=='PACKAGING':item.category==='PACKAGING'||item.unit!=='kg'))throw new Error(purpose==='packaging'?'请选择启用的包装物':'请选择启用的kg原辅料');
 return {...row,materialCode:item.code,materialName:item.name,catalogMaterialId:item.id,catalogSpecification:item.specification,
  catalogUnit:item.unit,formulaMaterialId:undefined,sourceStepOutputId:undefined,refreshCatalogSnapshot:true,selectionToken:`${Date.now()}-${++selectionSerial}`};
}
/** Apply only acknowledged identity; never replace edits made while the request was in flight. */
export function mergeMaterialAcknowledgement<T extends MaterialIdentity>(local:T,sent:MaterialIdentity,ack:MaterialIdentity):T {
 const sameSelection=['selectionToken','catalogMaterialId','materialCode','materialName','catalogSpecification','catalogUnit','sourceStepOutputId','refreshCatalogSnapshot'].every(key=>local[key as keyof MaterialIdentity]===sent[key as keyof MaterialIdentity]);
 if(!sameSelection)return {...local,id:ack.id};
 return {...local,id:ack.id,materialCode:ack.materialCode??'',materialName:ack.materialName??local.materialName,catalogMaterialId:ack.catalogMaterialId,
  catalogSpecification:ack.catalogSpecification,catalogUnit:ack.catalogUnit,refreshCatalogSnapshot:false};
}
export async function loadIngredientOptions<T>(load:()=>Promise<T>,loadCategories:()=>Promise<MaterialClassification[]>){
 const [data,categories]=await Promise.all([load(),loadCategories().catch(()=>[] as MaterialClassification[])]);
 return {data,categories};
}
export function clearCatalogReference<T>(row:T) {return {...row,catalogMaterialId:undefined,catalogSpecification:undefined,catalogUnit:undefined,formulaMaterialId:undefined,refreshCatalogSnapshot:false};}
export function materialLinkLabel(row:MaterialIdentity){return row.catalogMaterialId?'已关联物料库':'待关联物料';}
export class LatestMaterialQuery {
 private serial=0;
 invalidate(){this.serial++;}
 async run<T>(load:()=>Promise<T>,apply:(result:T)=>void){const serial=++this.serial;try{const result=await load();if(serial===this.serial)apply(result);}catch(error){if(serial===this.serial)throw error;}}
}
export function classificationOptions(items:MaterialClassification[],assignable=false){
 const result:Array<{value:string;label:string}>=[];
 const roots=items.filter(x=>!x.parentId).sort((a,b)=>a.sortOrder-b.sortOrder||a.name.localeCompare(b.name));
 for(const root of roots){
  if(assignable&&root.status!=='ACTIVE')continue;
  result.push({value:root.id,label:root.name+(root.status==='INACTIVE'?'（停用）':'')});
  for(const child of items.filter(x=>x.parentId===root.id).sort((a,b)=>a.sortOrder-b.sortOrder||a.name.localeCompare(b.name))){
   if(assignable&&child.status!=='ACTIVE')continue;
   result.push({value:child.id,label:`${root.name} / ${child.name}${child.status==='INACTIVE'?'（停用）':''}`});
  }
 }
 return result;
}
