import type { PricingBasisDraft,PricingBasisView } from "../../../../../packages/shared/src/pricing-basis.ts";
export interface BasisApi {load(id:string):Promise<PricingBasisView>;save(id:string,version:number,draft:PricingBasisDraft):Promise<PricingBasisView>;confirm(id:string,version:number):Promise<PricingBasisView>;generate(id:string,version:number):Promise<unknown>;preview(id:string,version:number):Promise<Blob>;}
const clone=<T>(v:T):T=>JSON.parse(JSON.stringify(v));
export const utilizationFromPercent=(v:number|null|undefined)=>v==null?null:v/100;
export const canEditPricingBasis=(actions:{code:string}[])=>actions.some(a=>a.code==="CONFIRM_PRICING_PACKAGING");
export class PricingBasisSession {
 id="";epoch=0;editable=false;busy=false;view:PricingBasisView|null=null;draft:PricingBasisDraft|null=null;baseline="";
 api:BasisApi;
 constructor(api:BasisApi){this.api=api;}
 get dirty(){return this.draft!=null&&JSON.stringify(this.draft)!==this.baseline;}
 async load(id:string,editable:boolean){const epoch=++this.epoch;this.id=id;this.editable=editable;this.busy=true;this.view=null;this.draft=null;try{const v=await this.api.load(id);if(epoch===this.epoch){this.view=v;this.draft=clone(v.draft);this.baseline=JSON.stringify(v.draft);}}finally{if(epoch===this.epoch)this.busy=false;}}
 guard(saved=false){if(!this.editable)throw new Error("当前为只读");if(this.busy)throw new Error("正在处理中");if(!this.view||!this.draft)throw new Error("尚未加载");if(saved&&this.dirty)throw new Error("请先保存草稿");if(saved&&this.view.stale)throw new Error("来源已更新，请重新取数");}
 async save(){this.guard();const epoch=this.epoch;const draft=clone(this.draft!);this.busy=true;try{const v=await this.api.save(this.id,this.view!.version,draft);if(epoch===this.epoch){this.view=v;this.baseline=JSON.stringify(v.draft);if(JSON.stringify(this.draft)===JSON.stringify(draft))this.draft=clone(v.draft);}}finally{if(epoch===this.epoch)this.busy=false;}}
 async generate(){this.guard(true);const epoch=this.epoch;this.busy=true;try{const v=await this.api.confirm(this.id,this.view!.version);if(epoch!==this.epoch)return;this.view=v;await this.api.generate(this.id,v.version);}finally{if(epoch===this.epoch)this.busy=false;}}
 async preview(){this.guard(true);const epoch=this.epoch;this.busy=true;try{return await this.api.preview(this.id,this.view!.version);}finally{if(epoch===this.epoch)this.busy=false;}}
 async resetSource(){this.guard();const epoch=this.epoch;this.busy=true;try{const v=await this.api.load(this.id);if(epoch===this.epoch){this.view=v;this.baseline=JSON.stringify(v.draft);this.draft=clone(v.source);}}finally{if(epoch===this.epoch)this.busy=false;}}
}
