// Dedicated isolated acceptance only. Never deletes or changes existing records.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const base='http://127.0.0.1:5193/api/v1';
const dir=path.join(root,'data/pricing-acceptance');fs.mkdirSync(dir,{recursive:true});
const stamp=Date.now();
async function call(actor,method,url,body,expected='0') {
 const response=await fetch(base+url,{method,headers:{'Content-Type':'application/json',...(actor?{Authorization:`Bearer ${actor.accessToken}`}:{})},body:body===undefined?undefined:JSON.stringify(body)});
 const envelope=await response.json();assert.equal(envelope.code,expected,`${method} ${url}: ${response.status} ${JSON.stringify(envelope)}`);return envelope.data;
}
const login=name=>call(null,'POST','/auth/login',{username:name,password:'123456'});
const engineer=await login('rnd_engineer'),director=await login('rnd_director'),assistant=await login('rnd_assistant'),tester=await login('tester'),finance=await login('finance');
const request=await call(assistant,'POST','/sample-requests',{productName:`核价验收香卤大肠头-${stamp}`,productType:'酱卤肉制品',customerName:'内部验收专用',specification:'500g/袋，20袋/箱',applicationScenario:'核价快照验收',flavorRequirement:'仅验证软件，不作为生产工艺',creatorName:assistant.user.name});
const approved=await call(director,'POST',`/sample-requests/${request.id}/approve`,{reviewerName:director.user.name});
const task=approved.task;
await call(director,'POST',`/rnd-tasks/${task.id}/assign`,{assigneeName:engineer.user.name,productOwnerName:engineer.user.name,dueDate:'2026-12-31'});
await call(engineer,'POST',`/rnd-tasks/${task.id}/accept`,{acceptedBy:engineer.user.name});
const form=await call(engineer,'POST',`/rnd-tasks/${task.id}/experiment-form/draft`,{operatorName:engineer.user.name,summary:'核价验收：100/.95领料，89主料出成；保留原始100kg',materials:[{stage:'卤制',sequence:1,materialCode:'QA-MAIN',materialName:'大肠头',weightKg:100,materialCategory:'RAW',primaryMaterial:true,utilizationRate:1}],processSteps:[{sequence:1,processName:'卤制',beforeWeightKg:100,afterWeightKg:89}],finishedOutputWeightKg:89,finishedOutputQuantity:178,finishedOutputUnit:'袋'});
const planPath=`/experiment-forms/${form.id}/process-plan`;
const initial=await call(engineer,'GET',planPath);
const savedPlan=await call(engineer,'PUT',planPath,{versionNo:initial.versionNo,status:'DRAFT',majorProcesses:[{sequence:1,processCode:'QA-COOK',processName:'卤制',yieldBasis:'PRIMARY_INPUT',remark:'验收示例，非生产参数',inputs:[],outputs:[],steps:[{sequence:1,stepCode:'BOIL',stepName:'煮制',stepType:'NORMAL',equipment:'测试设备',instruction:'软件验收示例',materials:[{sequence:1,materialRole:'PRIMARY',materialCode:'QA-MAIN',materialName:'大肠头',materialState:'SOLID',weightKg:100,sourceType:'EXTERNAL'}],outputs:[{id:`qa-out-${stamp}`,sequence:1,outputType:'FINISHED',outputName:'卤大肠头',materialState:'SOLID',weightKg:89,primaryOutput:true,continueFlow:false}],controlPoints:[]}]}]});
assert.equal(savedPlan.batchYieldPercent,89);
const revision=await call(engineer,'POST',planPath+'/submit',{versionNo:savedPlan.versionNo,confirmed:true});
const handoff=await call(engineer,'POST',`/experiment-forms/${form.id}/submit-test`,{testerName:tester.user.name});
await call(tester,'POST',`/test-assignments/${handoff.testAssignment.id}/pass`,{testerName:tester.user.name,comment:'软件验收专用'});
const pricing=await call(assistant,'POST',`/sample-versions/${approved.version.id}/pricing-files`);
const endpoint=`/pricing-files/${pricing.id}/basis`;
let view=await call(engineer,'GET',endpoint);assert.equal(view.version,0);assert.equal(view.confirmedBy,null);assert.equal(view.draft.sourceRevisionId,revision.id);
assert.equal(view.draft.materials[0].stepName,'1. 煮制');
const draft=structuredClone(view.draft);
Object.assign(draft.header,{companyName:'江西龙汇肉制品有限责任公司',documentNumber:`QA-${stamp}`,version:'A/0',effectiveDate:'2026-09-27',ownerName:engineer.user.name,authorName:engineer.user.name});
for(const m of draft.materials)Object.assign(m,{weightBasis:'AFTER_PROCESS',usageKg:100,utilizationRate:.95,confirmationBasis:'验收手算100÷95%'});
Object.assign(draft,{mainOutputKg:89,mainScopeBasis:'对应大肠头净出成，验收专用',finishedNetKg:89,unitWeightKg:.5,bagsPerBox:20,actualBags:178});
for(const p of draft.packaging)Object.assign(p,{quantity:p.materialName==='透明胶带'?0:p.materialName==='通用外箱'||p.materialName.endsWith('外箱标签')?9:178,quantityUnit:p.materialName.includes('标签')?'张':'个',internalCode:'003',remark:'0.99',adjustmentReason:p.materialName==='透明胶带'?'本次验收不用胶带':'验收人工确认，备注不参与换算'});
if(!draft.packaging.length)draft.packaging.push({id:'qa-bag',materialName:'包装袋',quantity:178,quantityUnit:'个',internalCode:'003',remark:'0.99',adjustmentReason:'验收人工确认'});
view=await call(engineer,'PUT',endpoint,{expectedVersion:0,draft});
const restored=await call(engineer,'GET',endpoint);assert.deepEqual(restored.draft,draft);
assert.equal(view.calculation.materials[0].requisitionKg,105.263157894737);assert.equal(view.calculation.mainYieldPercent,84.55);assert.equal(view.calculation.referenceBags,178);assert.equal(view.calculation.referenceBoxes,9);assert.equal(view.calculation.remainingKg,0);
await call(engineer,'PUT',endpoint,{expectedVersion:0,draft},'PRICING_BASIS_CONFLICT');
await call(engineer,'POST',endpoint+'/generate',{expectedVersion:view.version},'PRICING_BASIS_CONFIRM_REQUIRED');
const preview=await fetch(base+endpoint+'/preview',{method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${engineer.accessToken}`},body:JSON.stringify({expectedVersion:view.version})});assert.equal(preview.status,200);
fs.writeFileSync(path.join(dir,'acceptance-preview.xlsx'),Buffer.from(await preview.arrayBuffer()));
if(process.argv.includes('--draft-only')) {
 const summary={result:'DRAFT_READY',taskId:task.id,pricingId:pricing.id,url:`http://127.0.0.1:5193/admin/pricing/${pricing.id}`};
 fs.writeFileSync(path.join(dir,'draft.json'),JSON.stringify(summary,null,2));console.log(JSON.stringify(summary,null,2));process.exit(0);
}
view=await call(engineer,'POST',endpoint+'/confirm',{expectedVersion:view.version});
const confirmedVersion=view.version;
view=await call(engineer,'PUT',endpoint,{expectedVersion:confirmedVersion,draft});assert.equal(view.confirmedBy,null);
view=await call(engineer,'POST',endpoint+'/confirm',{expectedVersion:view.version});
const generated=await call(engineer,'POST',endpoint+'/generate',{expectedVersion:view.version});assert.equal(generated.status,'PENDING_PRICING_REVIEW');
async function download(actor){const r=await fetch(base+`/pricing-files/${pricing.id}/download`,{headers:{Authorization:`Bearer ${actor.accessToken}`}});assert.equal(r.status,200);return Buffer.from(await r.arrayBuffer());}
const bytes=await download(engineer);assert.equal(bytes.subarray(0,2).toString(),'PK');fs.writeFileSync(path.join(dir,'acceptance-confirmed.xlsx'),bytes);
await call(engineer,'PUT',endpoint,{expectedVersion:view.version,draft},'PRICING_BASIS_ARCHIVED');
await call(finance,'GET',endpoint,undefined,'PRICING_FILE_NOT_AVAILABLE_FOR_FINANCE');
await call(director,'POST',`/pricing-files/${pricing.id}/review`,{decision:'APPROVE',comment:'验收审核通过'});
assert.deepEqual(await download(finance),bytes);
const after=await call(engineer,'GET',planPath);assert.equal(after.batchYieldPercent,89);assert.equal(after.majorProcesses[0].steps[0].materials[0].weightKg,100);
const summary={result:'PASS',taskId:task.id,formId:form.id,revisionId:revision.id,pricingId:pricing.id,mainYieldPercent:84.55,referenceBags:178,referenceBoxes:9,sha256:createHash('sha256').update(bytes).digest('hex'),url:`http://127.0.0.1:5193/admin/pricing/${pricing.id}`};
fs.writeFileSync(path.join(dir,'acceptance.json'),JSON.stringify(summary,null,2));console.log(JSON.stringify(summary,null,2));
