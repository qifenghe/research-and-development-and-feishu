import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
test('SOP catalog searches, opens historical snapshot and exports that exact revision',async({page})=>{
 await page.addInitScript(()=>{
  localStorage.setItem('rnd_access_token','ui-fixture');
  localStorage.setItem('rnd_user',JSON.stringify({id:'U',name:'研发',role:'RND_DIRECTOR'}));
 });
 const revisions=[{id:'R2',revisionNo:2,submittedAt:'2026-09-30T12:00:00',submittedBy:'研发',changeReason:'调整尺寸'},
  {id:'R1',revisionNo:1,submittedAt:'2026-09-29T12:00:00',submittedBy:'研发',changeReason:'首次提交'}];
 const item={formId:'F1',taskId:'T1',productName:'香卤牛腩',specification:'350g',owner:'研发',revisionId:'R2',revisionNo:2,updatedAt:revisions[0].submittedAt,canGenerate:true,revisions};
 const downloads:string[]=[];
 await page.route('**/api/v1/**',async route=>{
  const url=new URL(route.request().url()),path=url.pathname;
  let data:unknown={};
  if(path==='/api/v1/sops')data={items:url.searchParams.get('keyword')==='不存在'?[]:[item],total:url.searchParams.get('keyword')==='不存在'?0:1,page:1,size:20};
  else if(path.endsWith('/artifacts/A1/download')){
   downloads.push(path);await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',body:'fixture-docx'});return;
  }else if(path.endsWith('/artifacts'))data=[{id:'A1',artifactType:'SOP_DOCX',status:'READY',documentVersion:'V1'}];
  else if(path.endsWith('/R1')||path.endsWith('/R2'))data={id:path.split('/').pop(),revisionNo:path.endsWith('/R1')?1:2,snapshot:{majorProcesses:[{key:'M',sequence:1,processName:'前处理',steps:[
   {key:'S0',sequence:1,stepName:'清洗',materials:[],outputs:[{id:'O1',outputName:'洗净牛腩',weightKg:10}],parameters:[],controlPoints:[]},
   {key:'S',sequence:2,stepName:'切块',instruction:path.endsWith('/R1')?'切成20×20毫米':'切成30×30毫米',materials:[{key:'I1',materialName:'洗净牛腩',materialRole:'PRIMARY',weightKg:10,sourceType:'STEP_OUTPUT',sourceStepOutputId:'O1'},{key:'I2',materialName:'水',materialRole:'PROCESS_WATER',weightKg:1,sourceType:'EXTERNAL'}],outputs:[],parameters:[],controlPoints:[]}]}]}};
  else if(path==='/api/v1/materials')data={items:[],total:0};
  else if(path==='/api/v1/session/me')data={userId:'U',name:'研发',role:'RND_DIRECTOR'};
  await route.fulfill({json:{code:'0',data}});
 });
 await page.goto(base+'/admin/sops');
 await expect(page.getByRole('heading',{name:'SOP 管理',exact:true})).toBeVisible();
 await page.getByPlaceholder('搜索产品名称').fill('不存在');await page.getByRole('button',{name:/搜\s*索/}).click();
 await expect(page.getByText('香卤牛腩',{exact:true})).toHaveCount(0);
 await page.getByPlaceholder('搜索产品名称').fill('牛腩');await page.getByRole('button',{name:/搜\s*索/}).click();
 await expect(page.getByText('香卤牛腩',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'历史版本',exact:true}).click();
 await page.getByRole('button',{name:'查看 R1',exact:true}).click();
 await expect(page.getByText('切成20×20毫米')).toBeVisible();
 await expect(page.getByText('切成30×30毫米')).toHaveCount(0);
 await expect(page.getByText('中间产物 · 来自 1.1 清洗')).toBeVisible();
 await expect(page.getByText('外部新投料')).toBeVisible();
 const download=page.waitForEvent('download');
 await page.getByRole('dialog').getByRole('button',{name:'导出 Word',exact:true}).click();
 expect((await download).suggestedFilename()).toContain('R1');
 expect(downloads).toEqual(['/api/v1/experiment-forms/F1/process-plan/revisions/R1/artifacts/A1/download']);
 await page.screenshot({path:'test-results/sop-catalog-detail.png',fullPage:true});
});

test('missing SOP blocks generation until checks pass then generates and downloads',async({page})=>{
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','ui-fixture');localStorage.setItem('rnd_user',JSON.stringify({id:'U',name:'研发',role:'RND_DIRECTOR'}));});
 let ready=false;const generated:string[]=[];let existing=true;
 const revision={id:'R1',revisionNo:1,submittedAt:'2026-10-01T12:00:00',submittedBy:'研发',snapshotHash:'h',canGenerate:true};
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;let data:unknown={};
  if(path==='/api/v1/sops')data={items:[{formId:'F1',taskId:'T1',productName:'生成测试牛腩',specification:'350g',owner:'研发',revisionId:'R1',revisionNo:1,updatedAt:revision.submittedAt,canGenerate:false,revisions:[revision]}],total:1,page:1,size:20};
  else if(path.endsWith('/R1'))data={...revision,snapshot:{majorProcesses:[]}};
  else if(path.endsWith('/export-check'))data={ready,issues:ready?[]:[{code:'STEP_INSTRUCTION_REQUIRED',message:'SOP 操作要求待填写',path:'majors[1].steps[1]'}]};
  else if(path.endsWith('/artifacts')){
   if(route.request().method()==='POST'){generated.push(route.request().postDataJSON().artifactType);data={id:'A2',artifactType:'SOP_DOCX',status:'READY',documentVersion:'V1'};}else data=existing?[{id:'MISSING',artifactType:'SOP_DOCX',status:'READY',documentVersion:'V0'}]:[];
  }else if(path.endsWith('/MISSING/download')){await route.fulfill({status:400,json:{code:'PROCESS_ARTIFACT_FILE_NOT_FOUND',message:'归档文件不存在'}});return;
  }else if(path.endsWith('/A2/download')){await route.fulfill({contentType:'application/vnd.openxmlformats-officedocument.wordprocessingml.document',body:'fixture-docx'});return;}
  else if(path==='/api/v1/session/me')data={userId:'U',name:'研发',role:'RND_DIRECTOR'};
  else if(path==='/api/v1/materials')data={items:[],total:0};
  await route.fulfill({json:{code:'0',data}});
 });
 await page.goto(base+'/admin/sops');await page.getByRole('button',{name:'查看 SOP',exact:true}).click();
 const button=page.getByRole('dialog').getByRole('button',{name:'导出 Word',exact:true});
 await expect(button).toBeEnabled();await button.click();
 await expect(page.getByRole('button',{name:'重新生成并导出',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'重新生成并导出',exact:true}).click();
 await expect(page.getByText('请先在打样中完善：SOP 操作要求待填写')).toBeVisible();expect(generated).toEqual([]);
 ready=true;existing=false;const download=page.waitForEvent('download');await button.click();expect((await download).suggestedFilename()).toContain('R1');expect(generated).toEqual(['SOP_DOCX']);
 await expect(page.getByRole('button',{name:'重新生成并导出',exact:true})).toHaveCount(0);
});
