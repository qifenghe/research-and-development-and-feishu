import type {ProcessArtifact,ProcessPlanDraft} from '@rnd/shared';
import {client} from './api';
import type {SopDifference} from './sopAmendmentDraft';
export interface SopAmendment {
 id:string;formId:string;sourceRevisionId:string;versionNo:number;status:string;
 reason:string;evidenceNote?:string;createdById:string;createdBy:string;
 publishedRevisionId?:string;snapshot:ProcessPlanDraft;differences:SopDifference[];
 history:{versionNo:number;action:string;actorName:string;comment?:string;actedAt:string}[];
}
const base='/sop-amendments',path=(id:string)=>`${base}/${encodeURIComponent(id)}`;
export const sopAmendmentApi={
 list:(keyword='',status='')=>client.get<SopAmendment[]>(`${base}?${new URLSearchParams({keyword,status})}`),
 get:(id:string)=>client.get<SopAmendment>(path(id)),
 create:(formId:string,sourceRevisionId:string,reason:string)=>client.post<SopAmendment>(base,{formId,sourceRevisionId,reason}),
 save:(id:string,body:{versionNo:number;reason:string;evidenceNote?:string;snapshot:ProcessPlanDraft})=>client.put<SopAmendment>(path(id),body),
 submit:(id:string,versionNo:number)=>client.post<SopAmendment>(`${path(id)}/submit`,{versionNo}),
 cancel:(id:string,versionNo:number)=>client.post<SopAmendment>(`${path(id)}/cancel`,{versionNo}),
 returnForChanges:(id:string,versionNo:number,comment:string)=>client.post<SopAmendment>(`${path(id)}/return`,{versionNo,comment}),
 approve:(id:string,versionNo:number)=>client.post<{amendment:SopAmendment;revision:{id:string;revisionNo:number}}>(`${path(id)}/approve`,{versionNo,confirmed:true}),
 source:(formId:string,revisionId:string)=>client.get<{snapshot:ProcessPlanDraft}>(`/sops/${encodeURIComponent(formId)}/revisions/${encodeURIComponent(revisionId)}`),
 artifacts:(formId:string,revisionId:string)=>client.get<ProcessArtifact[]>(`/sops/${encodeURIComponent(formId)}/revisions/${encodeURIComponent(revisionId)}/artifacts`),
 generate:(formId:string,revisionId:string)=>client.post<{id:string;status:string;failureReason?:string;documentVersion:string}>(`/sops/${encodeURIComponent(formId)}/revisions/${encodeURIComponent(revisionId)}/artifacts`,{artifactType:'SOP_XLSX'}),
 download:(formId:string,revisionId:string,artifactId:string)=>client.get<Blob>(`/sops/${encodeURIComponent(formId)}/revisions/${encodeURIComponent(revisionId)}/artifacts/${encodeURIComponent(artifactId)}/download`),
};
