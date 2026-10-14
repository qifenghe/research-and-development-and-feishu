// Local isolated HTTP acceptance. Creates explicitly labelled software-test records only.
import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {inflateRawSync} from 'node:zlib';

const base='http://127.0.0.1:5195/api/v1';
const output='/private/tmp/longhui-exact-bundle-e2e-20261013';
fs.mkdirSync(output,{recursive:true});
const stamp=Date.now(),state={result:'RUNNING',checks:[]};
const check=(label)=>state.checks.push(label);
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
async function call(actor,method,url,body,expected='0') {
 const r=await fetch(base+url,{method,headers:{'Content-Type':'application/json',...(actor?{Authorization:`Bearer ${actor.accessToken}`}:{})},body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(30000)});
 const data=await r.json();assert.equal(data.code,expected,`${method} ${url}: ${r.status} ${data.code} ${data.message}`);return data.data;
}
async function bytes(actor,url,name) {
 const r=await fetch(base+url,{headers:{Authorization:`Bearer ${actor.accessToken}`},signal:AbortSignal.timeout(30000)});
 assert.equal(r.status,200,`download ${url}`);const value=Buffer.from(await r.arrayBuffer());assert.equal(value.subarray(0,2).toString(),'PK');
 if(name)fs.writeFileSync(path.join(output,name),value);return value;
}
function officeXml(buffer) {
 const parts=[];
 for(let i=0;i+46<buffer.length;i++) {
  if(buffer.readUInt32LE(i)!==0x02014b50)continue;
  const size=buffer.readUInt32LE(i+20),length=buffer.readUInt16LE(i+28),extra=buffer.readUInt16LE(i+30),comment=buffer.readUInt16LE(i+32),offset=buffer.readUInt32LE(i+42);
  const name=buffer.subarray(i+46,i+46+length).toString(),start=offset+30+buffer.readUInt16LE(offset+26)+buffer.readUInt16LE(offset+28);
  if(name.endsWith('.xml')){const data=buffer.subarray(start,start+size);parts.push((buffer.readUInt16LE(i+10)===8?inflateRawSync(data):data).toString());}
  i+=45+length+extra+comment;
 }
 return parts.join('\n');
}
try {
 const login=username=>call(null,'POST','/auth/login',{username,password:'123456'});
 const assistant=await login('rnd_assistant'),director=await login('rnd_director'),engineer=await login('rnd_engineer'),tester=await login('tester'),finance=await login('finance'),admin=await login('admin');
 const classification=await call(admin,'POST','/material-classifications',{name:`软件验收原料-${stamp}`,businessType:'RAW',sortOrder:999});
 const leaf=await call(admin,'POST','/material-classifications',{name:'验收牛类',parentId:classification.id,sortOrder:0});
 const beef=await call(assistant,'POST','/materials',{code:`QA-BEEF-${stamp}`,name:'软件验收牛腩（非生产数据）',classificationId:leaf.id,unit:'kg',specification:'仅软件验收'});
 const request=await call(assistant,'POST','/sample-requests',{productName:`三文件版本验收牛腩-${stamp}`,productType:'酱卤肉制品',customerName:'内部软件验收',specification:'500g/袋，20袋/箱',applicationScenario:'软件验收',flavorRequirement:'非生产标准',creatorName:'软件验收销售甲',referenceSampleProvided:false,cutShape:'块状',mainMaterialRequirement:'软件验收牛腩',textureRequirement:'验收记录',packagingRequirement:'500g/袋，20袋/箱',storageRequirement:'验收冷冻',customerTargetPrice:'仅软件验收',monthlyDemand:'仅软件验收',specialRequirements:'仅软件验收，不作生产标准'});
 const approved=await call(director,'POST',`/sample-requests/${request.id}/approve`,{reviewerName:director.user.name});
 const task=approved.task;state.taskId=task.id;
 await call(director,'POST',`/rnd-tasks/${task.id}/assign`,{assigneeName:engineer.user.name,productOwnerName:engineer.user.name,dueDate:'2026-12-31'});
 await call(engineer,'POST',`/rnd-tasks/${task.id}/accept`,{acceptedBy:engineer.user.name});
 const form=await call(engineer,'POST',`/rnd-tasks/${task.id}/experiment-form/draft`,{operatorName:engineer.user.name,summary:'三文件及历史归集软件验收；不作生产参数',materials:[{stage:'熟制',sequence:1,catalogMaterialId:beef.id,materialCode:beef.code,materialName:beef.name,weightKg:100,materialCategory:'RAW',primaryMaterial:true,utilizationRate:1}],processSteps:[{sequence:1,processName:'软件测试熟制',beforeWeightKg:100,afterWeightKg:89}],finishedOutputWeightKg:89,finishedOutputQuantity:178,finishedOutputUnit:'袋'});
 state.formId=form.id;const planPath=`/experiment-forms/${form.id}/process-plan`,initial=await call(engineer,'GET',planPath);
 const saved=await call(engineer,'PUT',planPath,{versionNo:initial.versionNo,status:'DRAFT',majorProcesses:[{sequence:1,processCode:'QA-COOK',processName:'软件测试熟制',yieldBasis:'PRIMARY_INPUT',inputs:[],outputs:[],steps:[{sequence:1,stepCode:'BOIL',stepName:'软件测试煮制',stepType:'NORMAL',equipment:'验收设备',instruction:'软件验收，不作生产标准',materials:[{sequence:1,catalogMaterialId:beef.id,materialCode:beef.code,materialName:beef.name,materialRole:'PRIMARY',materialState:'SOLID',sourceType:'EXTERNAL',weightKg:100}],outputs:[{id:`qa-output-${stamp}`,sequence:1,outputType:'FINISHED',outputName:'软件验收熟牛腩',materialState:'SOLID',weightKg:89,primaryOutput:true,continueFlow:false}],controlPoints:[]}]}]});
 const r1=await call(director,'POST',planPath+'/submit',{versionNo:saved.versionNo,confirmed:true,changeReason:'软件验收第一版'});state.r1=r1.id;
 async function artifacts(revision,number) {
  const result={};
  for(const [label,type,extension] of [['配方','FORMULA_XLSX','xlsx'],['SOP','SOP_XLSX','xlsx']]) {
   const record=await call(director,'POST',`${planPath}/revisions/${revision.id}/artifacts`,{artifactType:type});assert.equal(record.status,'READY');
   const content=await bytes(assistant,`/sops/${form.id}/revisions/${revision.id}/artifacts/${record.id}/download`,`R${number}-${label}.${extension}`);
   const text=officeXml(content);assert.ok(label==='SOP'?text.includes('编写人')&&text.includes('研发批准'):text.includes('编制人')&&text.includes('审核人')&&text.includes('审核日期'));assert.ok(text.includes(director.user.name));
   if(number===1)assert.ok(text.includes('待审核'),'正式提交不得冒充审核通过');
   else assert.ok(!text.includes('待审核')&&text.includes(assistant.user.name),'调整版必须保留内勤编制及总监实际审核签名');
   result[label]={id:record.id,sha256:hash(content),content};
  }
  return result;
 }
 const first=await artifacts(r1,1);check('第一版配方和 SOP 生成、署名、内勤正式归档下载');
 const handoff=await call(engineer,'POST',`/experiment-forms/${form.id}/submit-test`,{testerName:tester.user.name});
 await call(tester,'POST',`/test-assignments/${handoff.testAssignment.id}/pass`,{testerName:tester.user.name,comment:'软件验收通过'});
 const frozenResearch=await call(engineer,'GET',planPath);
 async function pricing(revision,number,net) {
  const file=await call(assistant,'POST',`/sample-versions/${approved.version.id}/pricing-files`),endpoint=`/pricing-files/${file.id}/basis`;
  let basis=await call(engineer,'GET',endpoint);assert.equal(basis.draft.sourceRevisionId,revision.id);
  const draft=structuredClone(basis.draft);
  Object.assign(draft.header,{documentNumber:`QA-${stamp}-R${number}`,version:`A/${number-1}`,effectiveDate:'2026-10-13',authorName:'不可信客户端署名'});
  for(const m of draft.materials)Object.assign(m,{weightBasis:'AFTER_PROCESS',usageKg:m.originalWeightKg,utilizationRate:.95,confirmationBasis:'软件验收确认，不作生产参数'});
  Object.assign(draft,{mainOutputKg:net,mainScopeBasis:'验收牛腩主料范围',finishedNetKg:net,unitWeightKg:.5,bagsPerBox:20,actualBags:net*2});
  for(const p of draft.packaging)Object.assign(p,{quantity:p.materialName==='透明胶带'?0:p.materialName.includes('箱')?Math.ceil(net*2/20):net*2,quantityUnit:p.materialName.includes('标签')?'张':'个',internalCode:'003',adjustmentReason:'验收人工确认'});
  if(!draft.packaging.length)draft.packaging=[{id:`qa-bag-r${number}`,materialName:'软件验收包装袋',quantity:net*2,quantityUnit:'个',adjustmentReason:'软件验收'}];
  basis=await call(engineer,'PUT',endpoint,{expectedVersion:basis.version,draft});
  assert.equal(basis.draft.signature.compiledBy,engineer.user.name);assert.ok(basis.draft.signature.compiledAt);assert.equal(basis.draft.signature.reviewedBy,null);
  basis=await call(engineer,'POST',endpoint+'/confirm',{expectedVersion:basis.version});
  const generated=await call(engineer,'POST',endpoint+'/generate',{expectedVersion:basis.version});assert.equal(generated.processRevisionId,revision.id);
  await call(engineer,'POST',`/pricing-files/${file.id}/review`,{decision:'APPROVE',comment:'越权验收'},'PRICING_FILE_REVIEW_FORBIDDEN');
  await call(director,'POST',`/pricing-files/${file.id}/review`,{decision:'APPROVE',comment:'软件验收总监审核'});
  const content=await bytes(finance,`/pricing-files/${file.id}/download`,`R${number}-核价.xlsx`),text=officeXml(content);
  assert.ok(text.includes(engineer.user.name)&&text.includes(director.user.name)&&text.includes('研发批准')&&text.includes('2026-10-13'));assert.ok(!text.includes('不可信客户端署名'));
  return {id:file.id,sha256:hash(content),content};
 }
 const p1=await pricing(r1,1,89);state.p1=p1.id;check('第一版核价编制、确认、总监审核、财务下载及客户端伪造署名拒绝');
 await call(assistant,'POST',`/sample-versions/${approved.version.id}/pricing-files`,undefined,'PRICING_FILE_REVIEW_IN_PROGRESS');
 let amendment=await call(assistant,'POST','/sop-amendments',{formId:form.id,sourceRevisionId:r1.id,reason:'软件验收车间中试85kg调整'});
 const snapshot=structuredClone(amendment.snapshot);snapshot.majorProcesses[0].steps[0].instruction='依据软件验收记录沥水5分钟';snapshot.majorProcesses[0].steps[0].outputs[0].weightKg=85;
 amendment=await call(assistant,'PUT',`/sop-amendments/${amendment.id}`,{versionNo:amendment.versionNo,reason:amendment.reason,evidenceNote:'软件验收数据，非真实车间记录',snapshot});
 amendment=await call(assistant,'POST',`/sop-amendments/${amendment.id}/submit`,{versionNo:amendment.versionNo});
 const publication=await call(director,'POST',`/sop-amendments/${amendment.id}/approve`,{versionNo:amendment.versionNo,confirmed:true}),r2=publication.revision;state.r2=r2.id;
 const second=await artifacts(r2,2);const p2=await pricing(r2,2,85);state.p2=p2.id;
 assert.deepEqual(await call(engineer,'GET',planPath),frozenResearch);check('内勤调整、总监审核第二版；锁定研发原记录不变；新工艺可独立核价');
 for(const [label,file] of Object.entries(first))assert.equal(hash(await bytes(assistant,`/sops/${form.id}/revisions/${r1.id}/artifacts/${file.id}/download`)),file.sha256);
 assert.equal(hash(await bytes(assistant,`/pricing-files/${p1.id}/download`)),p1.sha256);check('第二版发布后第一版三个文件 SHA-256 完全不变');
 const allPricing=await call(assistant,'GET','/pricing-files');assert.equal(allPricing.find(f=>f.id===p1.id).processRevisionId,r1.id);assert.equal(allPricing.find(f=>f.id===p2.id).processRevisionId,r2.id);
 const catalog=await call(assistant,'GET',`/sops?keyword=${encodeURIComponent(request.productName)}&page=1&size=20`);assert.equal(catalog.items[0].revisions.length,2);check('产品搜索可见两版正式工艺；核价按准确版本归集');
 for(const revision of [r1,r2])assert.equal((await call(assistant,'GET',`/sops/${form.id}/revisions/${revision.id}/artifacts`)).filter(a=>a.status==='READY').length,2);
 state.hashes={R1:{配方:first['配方'].sha256,SOP:first.SOP.sha256,核价:p1.sha256},R2:{配方:second['配方'].sha256,SOP:second.SOP.sha256,核价:p2.sha256}};
 state.url='http://127.0.0.1:5195/admin/sops';state.productName=request.productName;state.result='PASS';
 console.log(JSON.stringify(state,null,2));
} catch(error) {state.result='FAIL';state.error=error.message;console.error(error.message);process.exitCode=1;}
finally {fs.writeFileSync(path.join(output,'验收结果.json'),JSON.stringify(state,null,2));}
