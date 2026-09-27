import test from "node:test";
import assert from "node:assert/strict";
import { PricingBasisSession, utilizationFromPercent, canEditPricingBasis } from "../apps/pc/src/components/pricing/pricingBasisSession.ts";
const fixture=(id="rev")=>({draft:{schemaVersion:1,sourceRevisionId:id,header:{productName:"测试"},materials:[],packaging:[]},version:1,calculation:{materials:[],issues:[]},confirmedBy:null,confirmedAt:null,stale:false,source:{}} as any);
test("edit entrance uses server owner permission, not the version author",()=>{
 assert.equal(canEditPricingBasis([{code:"CONFIRM_PRICING_PACKAGING"}]),true);
 assert.equal(canEditPricingBasis([]),false);
});
test("percent entry distinguishes blank from zero and converts 95 to 0.95",()=>{
 assert.equal(utilizationFromPercent(null),null);assert.equal(utilizationFromPercent(0),0);assert.equal(utilizationFromPercent(95),.95);
});
test("saving preserves payload and stale responses cannot overwrite a newly selected file",async()=>{
 let release:any;let saved:any;
 const api:any={load:async(id:string)=>fixture(id),save:async(_id:string,_v:number,d:any)=>{saved=d;return new Promise(r=>release=r);}};
 const s=new PricingBasisSession(api);await s.load("a",true);s.draft!.header!.productName="修改";
 const pending=s.save();assert.equal(s.busy,true);await s.load("b",true);release({...fixture("a"),draft:saved,version:2});await pending;
 assert.equal(s.draft!.sourceRevisionId,"b");assert.equal(s.dirty,false);
});
test("failed save keeps edits and readonly or dirty data cannot generate",async()=>{
 let calls=0;const api:any={load:async()=>fixture(),save:async()=>{throw new Error("版本冲突");},confirm:async()=>{calls++;}};
 const s=new PricingBasisSession(api);await s.load("a",true);s.draft!.header!.productName="修改";
 await assert.rejects(s.save(),/版本冲突/);assert.equal(s.draft!.header!.productName,"修改");assert.equal(s.dirty,true);
 await assert.rejects(s.generate(),/保存/);assert.equal(calls,0);
 await s.load("a",false);await assert.rejects(s.save(),/只读/);
});
test("generation confirms saved version once and ignores repeated clicks",async()=>{
 let finish:any;let confirmations=0;let generations=0;
 const api:any={load:async()=>fixture(),confirm:async()=>{confirmations++;return {...fixture(),version:2,confirmedBy:"研发"};},generate:async(_id:string,v:number)=>{assert.equal(v,2);generations++;await new Promise(r=>finish=r);}};
 const s=new PricingBasisSession(api);await s.load("a",true);const first=s.generate();await Promise.resolve();await Promise.resolve();await assert.rejects(s.generate(),/处理中/);finish();await first;
 assert.equal(confirmations,1);assert.equal(generations,1);
});
test("reset source fetches current revision and keeps local edits when fetch fails",async()=>{
 let latest=fixture("old");latest.source=latest.draft;
 const api:any={load:async()=>latest};const s=new PricingBasisSession(api);await s.load("a",true);
 latest=fixture("new");latest.source=latest.draft;latest.version=3;
 await s.resetSource();assert.equal(s.draft!.sourceRevisionId,"new");assert.equal(s.view!.version,3);
 s.draft!.header!.productName="保留输入";api.load=async()=>{throw new Error("网络错误");};
 await assert.rejects(s.resetSource(),/网络错误/);assert.equal(s.draft!.header!.productName,"保留输入");
});
