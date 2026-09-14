// Real HTTP acceptance on the dedicated local trial, never on production.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const comparisonCase=process.argv.includes('--comparison');
const caseSuffix=comparisonCase?'-comparison':'';
const finalWeight=comparisonCase?63:72;
const file=path.join(root,`data/preview/acceptance${caseSuffix}.json`);
const base='http://127.0.0.1:5183/api/v1';
const state=fs.existsSync(file)?JSON.parse(fs.readFileSync(file,'utf8')):{};
const persist=()=>fs.writeFileSync(file,JSON.stringify(state,null,2));
async function request(actor,method,url,body) {
  const response=await fetch(base+url,{method,headers:{'Content-Type':'application/json',Origin:'http://127.0.0.1:5183',...(actor?{Authorization:`Bearer ${actor.accessToken}`}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
  const result=await response.json();
  if(!response.ok||result.code!=='0') throw new Error(`${method} ${url}: ${response.status} ${result.code} ${result.message}`);
  return result.data;
}
async function login(username) {return request(null,'POST','/auth/login',{username,password:'123456'});}
const engineer=await login('rnd_engineer'), director=await login('rnd_director'), assistant=await login('rnd_assistant'), tester=await login('tester');
const stage=async(key,work)=>{if(!state[key]){state[key]=await work();persist();}return state[key];};
const req=await stage('request',()=>request(assistant,'POST','/sample-requests',{productName:comparisonCase?'香卤牛腩（对比验证）':'工艺联调牛腩（试用验证）',productType:'冷冻即热菜',customerName:'内部研发验证',specification:'1kg/袋',applicationScenario:'工艺联调',flavorRequirement:'验证主料得率及SOP',creatorName:assistant.user.name}));
const approved=await stage('approved',()=>request(director,'POST',`/sample-requests/${req.id}/approve`,{reviewerName:director.user.name}));
const task=approved.task;
await stage('assigned',()=>request(director,'POST',`/rnd-tasks/${task.id}/assign`,{assigneeName:engineer.user.name,productOwnerName:engineer.user.name,dueDate:'2026-12-31'}));
await stage('accepted',()=>request(engineer,'POST',`/rnd-tasks/${task.id}/accept`,{acceptedBy:engineer.user.name}));
const form=await stage('form',()=>request(engineer,'POST',`/rnd-tasks/${task.id}/experiment-form/draft`,{operatorName:engineer.user.name,summary:`100kg→90kg→${finalWeight}kg；预期最终主料得率${finalWeight}%`,materials:[{stage:'原料',sequence:1,materialCode:'BEEF-TRIAL',materialName:'牛腩',weightKg:100,materialCategory:'RAW',primaryMaterial:true,utilizationRate:1}],processSteps:[{sequence:1,processName:'修割与熟制',beforeWeightKg:100,afterWeightKg:finalWeight}],finishedOutputWeightKg:finalWeight,finishedOutputQuantity:finalWeight,finishedOutputUnit:'袋'}));
const planPath=`/experiment-forms/${form.id}/process-plan`;
function major(seq,source,input,output) {
 return {sequence:seq,processCode:seq===1?'TRIM':'COOK',processName:seq===1?'修割':'熟制',yieldBasis:'PRIMARY_INPUT',remark:'正常修割及熟制损耗',inputs:[],outputs:[],steps:[{sequence:1,stepCode:seq===1?'TRIM':'BOIL',stepName:seq===1?'修割':'煮制',stepType:'NORMAL',parameter1Name:'温度',parameter1Value:seq===1?'10':'95',parameter1Unit:'℃',parameter2Name:'时间',parameter2Value:'40',parameter2Unit:'min',equipment:'夹层锅',instruction:'按工艺参数执行并记录称重',materials:[{sequence:1,materialRole:'PRIMARY',materialCode:'BEEF-TRIAL',materialName:'牛腩',materialState:'SOLID',weightKg:input,sourceType:source?'STEP_OUTPUT':'EXTERNAL',sourceStepOutputId:source}],outputs:[{id:`trial-out-${seq}`,sequence:1,outputType:seq===1?'INTERMEDIATE':'FINISHED',outputName:'牛腩',materialState:'SOLID',weightKg:output,primaryOutput:true,continueFlow:seq===1}],controlPoints:seq===1?[]:[{id:'trial-control',sequence:1,controlType:'QUALITY',importance:'CRITICAL',itemName:'研发示例中心温度',lowerLimit:75,upperLimit:85,unit:'℃',method:'探针测温',confirmedBy:'__SESSION_CONFIRMATION_REQUESTED__',measurements:[{id:'trial-measurement',sequence:1,measuredValue:80,result:'PASS',measuredAt:'2026-09-14T21:00:00'}]}]}]};
}
await stage('planSaved',async()=>{
 const current=await request(engineer,'GET',planPath);
 const majors=[major(1,null,100,90),major(2,'trial-out-1',90,finalWeight)];
 delete majors[1].steps[0].materials[0].materialCode;
 if (comparisonCase) {
   for (const item of majors) for(const step of item.steps) {
     for(const output of step.outputs) output.id += '-comparison';
     for(const material of step.materials) if(material.sourceStepOutputId) material.sourceStepOutputId += '-comparison';
     for(const point of step.controlPoints) { point.id += '-comparison'; for(const measurement of point.measurements) measurement.id += '-comparison'; }
   }
 }
 const saved=await request(engineer,'PUT',planPath,{versionNo:current.versionNo,status:'DRAFT',majorProcesses:majors});
 assert.equal(saved.batchYieldPercent,finalWeight);assert.equal(saved.majorProcesses[1].yield.totalInputWeightKg,90);
 assert.equal(saved.majorProcesses[1].steps[0].controlPoints[0].confirmedBy,engineer.user.name);
 return saved;
});
const revision=await stage('revision',async()=>{
 const check=await request(engineer,'GET',planPath+'/submission-check');assert.equal(check.ready,true);
 return request(engineer,'POST',planPath+'/submit',{versionNo:state.planSaved.versionNo,confirmed:true});
});
for(const [key,type] of [['formula','FORMULA_XLSX'],['sop','SOP_DOCX']]){
 const artifact=await stage(key,()=>request(engineer,'POST',`${planPath}/revisions/${revision.id}/artifacts`,{artifactType:type}));
 assert.equal(artifact.status,'READY');
 const response=await fetch(`${base}${planPath}/revisions/${revision.id}/artifacts/${artifact.id}/download`,{headers:{Authorization:`Bearer ${engineer.accessToken}`}});
 assert.equal(response.status,200);const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.subarray(0,2).toString(),'PK');
 fs.writeFileSync(path.join(root,`data/preview/acceptance${caseSuffix}-${key}.${key==='sop'?'docx':'xlsx'}`),bytes);
}
const comparison=await request(engineer,'GET',planPath+'/revisions/comparison');
assert.ok(comparison.some(row=>row.formId===form.id&&row.processCode==='COOK'&&row.yieldPercent===(comparisonCase?70:80)));
if(comparisonCase) {
 const group=comparison.find(row=>row.formId===form.id&&row.processCode==='COOK').groupKey;
 assert.deepEqual(comparison.filter(row=>row.groupKey===group).map(row=>row.yieldPercent).sort(),[70,80]);
 state.verifiedAt=new Date().toISOString();persist();console.log('PASS: 两产品同口径熟制得率80%与70%，差10个百分点。');process.exit(0);
}
await stage('handoff',()=>request(engineer,'POST',`/experiment-forms/${form.id}/submit-test`,{testerName:tester.user.name}));
await stage('testPassed',()=>request(tester,'POST',`/test-assignments/${state.handoff.testAssignment.id}/pass`,{testerName:tester.user.name,comment:'内部流程联调通过'}));
const pricing=await stage('pricing',()=>request(assistant,'POST',`/sample-versions/${approved.version.id}/pricing-files`));
await stage('packaging',async()=>{
 const items=await request(engineer,'GET',`/pricing-files/${pricing.id}/packaging-items`);
 return request(engineer,'PUT',`/pricing-files/${pricing.id}/packaging-items`,{items});
});
const downloaded=await fetch(`${base}/pricing-files/${pricing.id}/download`,{headers:{Authorization:`Bearer ${engineer.accessToken}`}});
assert.equal(downloaded.status,200);const pricingBytes=Buffer.from(await downloaded.arrayBuffer());assert.equal(pricingBytes.subarray(0,2).toString(),'PK');
fs.writeFileSync(path.join(root,'data/preview/acceptance-pricing.xlsx'),pricingBytes);
state.verifiedAt=new Date().toISOString();persist();
console.log(JSON.stringify({result:'PASS',taskId:task.id,formId:form.id,revisionId:revision.id,finalYield:72,pricingId:pricing.id,artifacts:['formula','sop','pricing']},null,2));
