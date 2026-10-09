import type {ProcessRevisionSummary} from '@rnd/shared';
import {client} from './api';
export interface SopRevision extends ProcessRevisionSummary {canGenerate:boolean}
export interface SopCatalogItem {
  formId:string; taskId:string; productName:string; specification:string; owner:string;
  revisionId:string; revisionNo:number; updatedAt:string; canGenerate:boolean; revisions:SopRevision[];
}
export interface SopCatalogPage {items:SopCatalogItem[];total:number;page:number;size:number}
export const sopCatalogApi={
  list:(keyword='',page=1,size=20)=>client.get<SopCatalogPage>(`/sops?${new URLSearchParams({keyword,page:String(page),size:String(size)})}`),
};
