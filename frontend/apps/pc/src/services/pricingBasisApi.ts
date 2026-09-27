import type {PricingBasisDraft,PricingBasisView} from "@rnd/shared";
import {client} from "./api";
const path=(id:string)=>`/pricing-files/${encodeURIComponent(id)}/basis`;
export const pricingBasisApi={
 load:(id:string)=>client.get<PricingBasisView>(path(id)),
 save:(id:string,expectedVersion:number,draft:PricingBasisDraft)=>client.put<PricingBasisView>(path(id),{expectedVersion,draft}),
 confirm:(id:string,expectedVersion:number)=>client.post<PricingBasisView>(`${path(id)}/confirm`,{expectedVersion}),
 preview:(id:string,expectedVersion:number)=>client.post<Blob>(`${path(id)}/preview`,{expectedVersion}),
 generate:(id:string,expectedVersion:number)=>client.post<unknown>(`${path(id)}/generate`,{expectedVersion}),
};
