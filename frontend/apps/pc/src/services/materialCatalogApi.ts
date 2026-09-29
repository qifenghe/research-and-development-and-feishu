import type {MaterialCatalogItem,MaterialClassification} from '@rnd/shared';
import {client} from './api';
export interface CatalogAccess {create:boolean;edit:boolean;status:boolean;classifications:boolean}
export interface MaterialPage {items:MaterialCatalogItem[];total:number;page:number;size:number;access?:CatalogAccess}
export interface MaterialWrite {code:string;name:string;category?:string;specification?:string;unit:string;classificationId?:string;expectedVersion?:number}
export interface ClassificationWrite {parentId?:string;name:string;businessType?:string;sortOrder:number;expectedVersion?:number}
export const materialCatalogApi={
 list:(params:Record<string,string|number|undefined>={})=>{const q=new URLSearchParams();for(const [key,value] of Object.entries(params))if(value!==undefined)q.set(key,String(value));return client.get<MaterialPage>(`/materials?${q}`);},
 create:(body:MaterialWrite)=>client.post<MaterialCatalogItem>('/materials',body),
 update:(id:string,body:MaterialWrite)=>client.put<MaterialCatalogItem>(`/materials/${encodeURIComponent(id)}`,body),
 status:(item:MaterialCatalogItem)=>client.post<MaterialCatalogItem>(`/materials/${encodeURIComponent(item.id)}/status`,{status:item.status==='ACTIVE'?'INACTIVE':'ACTIVE',expectedVersion:item.version}),
 classifications:()=>client.get<MaterialClassification[]>('/material-classifications'),
 createClassification:(body:ClassificationWrite)=>client.post<MaterialClassification>('/material-classifications',body),
 updateClassification:(id:string,body:ClassificationWrite)=>client.put<MaterialClassification>(`/material-classifications/${encodeURIComponent(id)}`,body),
 classificationStatus:(item:MaterialClassification)=>client.post<MaterialClassification>(`/material-classifications/${encodeURIComponent(item.id)}/status`,{status:item.status==='ACTIVE'?'INACTIVE':'ACTIVE',expectedVersion:item.version}),
};
