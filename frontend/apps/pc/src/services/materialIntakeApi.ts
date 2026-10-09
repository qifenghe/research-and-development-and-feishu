import {client} from './api';
export interface IntakeLine {temporaryMaterialId:string;code:string;name:string;classificationId:string;specification?:string;totalWeightKg?:number|null;quantity?:number;quantityUnit?:string;materialRole?:string;rowIds:string[];catalogMaterialId?:string}
export interface IntakeRequest {id:string;formId:string;trialId?:string;sourceVersion:number;applicantId:string;applicantName:string;status:'DRAFT'|'PENDING'|'APPROVED'|'RETURNED';version:number;comment?:string;reviewerName?:string;lines:IntakeLine[]}
export const materialIntakeApi={
 list:(query='',status='')=>client.get<IntakeRequest[]>(`/material-intakes?query=${encodeURIComponent(query)}&status=${status}`),
 create:(body:{formId:string;trialId?:string;sourceVersion:number;lines:{temporaryMaterialId:string;code:string}[]})=>client.post<IntakeRequest>('/material-intakes',body),
 submit:(id:string,expectedVersion:number)=>client.post<IntakeRequest>(`/material-intakes/${id}/submit`,{expectedVersion}),
 approve:(id:string,expectedVersion:number,decisions:{lineId:string;mode:string;catalogMaterialId?:string}[])=>client.post<IntakeRequest>(`/material-intakes/${id}/approve`,{expectedVersion,decisions}),
 reject:(id:string,expectedVersion:number,comment:string)=>client.post<IntakeRequest>(`/material-intakes/${id}/return`,{expectedVersion,comment}),
};
