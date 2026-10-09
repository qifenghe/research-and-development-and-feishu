import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
test('expired stored session cannot open protected SOP adjustment',async({page})=>{
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','expired');localStorage.setItem('rnd_user',JSON.stringify({id:'U',name:'内勤',role:'RND_ASSISTANT'}));});
 let amendmentReads=0;
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/v1/session/me'){await route.fulfill({status:401,json:{code:'SESSION_EXPIRED',message:'登录过期'}});return;}
  if(path.startsWith('/api/v1/sop-amendments'))amendmentReads++;
  await route.fulfill({json:{code:'0',data:{}}});
 });
 await page.goto(base+'/admin/sops/amendments/A');
 await expect(page).toHaveURL(/\/admin\/login/);
 await expect(page.getByRole('heading',{name:'SOP 调整单'})).toHaveCount(0);
 expect(amendmentReads).toBe(0);
});
test('SOP adjustment retains returned comments, freezes review and removes red comparison after publishing',async({page})=>{
 let role='RND_ASSISTANT';
 const source={id:'P',experimentFormId:'F',versionNo:1,status:'SUBMITTED',balanceToleranceKg:0.001,majorProcesses:[{id:'M',key:'M',sequence:1,processCode:'COOK',processName:'熟制',steps:[{id:'S',key:'S',sequence:1,stepName:'煮制',stepType:'NORMAL',instruction:'煮制后沥水',materials:[],outputs:[],controlPoints:[],parameters:[]}],inputs:[],outputs:[]}]};
 let record:any={id:'A',formId:'F',sourceRevisionId:'R1',versionNo:0,status:'DRAFT',reason:'依据车间沥水记录调整',createdById:'U',createdBy:'内勤',snapshot:structuredClone(source),differences:[],history:[]};
 const requests:{action:string;role:string;versionNo:number}[]=[];
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','ui-test');localStorage.setItem('rnd_user',JSON.stringify({id:'U',name:'内勤',role:'RND_ASSISTANT'}));});
 await page.route('**/api/v1/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname;let data:any={};
  if(path==='/api/v1/session/me')data={userId:role==='RND_ASSISTANT'?'U':'D',name:role==='RND_ASSISTANT'?'内勤':'总监',role};
  else if(path==='/api/v1/materials')data={items:[],total:0};
  else if(path==='/api/v1/sops')data={items:[],total:0};
  else if(path==='/api/v1/sops/F/revisions/R1')data={snapshot:source};
  else if(path==='/api/v1/sop-amendments/A'&&req.method()==='GET')data=record;
  else if(path.startsWith('/api/v1/sop-amendments/A')){
   const body=req.postDataJSON(),action=req.method()==='PUT'?'save':path.split('/').pop()!;
   requests.push({action,role,versionNo:body.versionNo});
   expect(body.versionNo).toBe(record.versionNo);
   record={...record,versionNo:record.versionNo+1};
   if(action==='save'){record.snapshot=body.snapshot;record.reason=body.reason;}
   if(action==='submit')record.status='PENDING_REVIEW';
   if(action==='return'){expect(body.comment).toBe('补充实测依据');record.status='RETURNED';record.history.push({versionNo:record.versionNo,action:'RETURNED',actorName:'总监',comment:body.comment,actedAt:'2026-10-07T12:00:00'});}
   if(action==='approve'){expect(body.confirmed).toBe(true);record.status='PUBLISHED';record.publishedRevisionId='R2';}
   data=action==='approve'?{amendment:record,revision:{id:'R2',revisionNo:2}}:record;
  }
  await route.fulfill({json:{code:'0',data}});
 });
 await page.goto(base+'/admin/sops/amendments/A');
 await expect(page.getByRole('heading',{name:'SOP 调整单'})).toBeVisible();
 await expect(page.getByRole('button',{name:'保存草稿',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'查看熟制步骤',exact:true}).click();
 await page.getByRole('button',{name:'编辑',exact:true}).first().click();
 const instruction=page.locator('.section').filter({hasText:'基础操作参数'}).locator('textarea');
 await instruction.fill('煮制后沥水5分钟');
 await expect(page.locator('.differences strong').filter({hasText:'煮制后沥水5分钟'})).toBeVisible();
 await page.getByRole('button',{name:'提交总监审核'}).click();
 await page.getByRole('dialog').getByRole('button',{name:/确.*定/}).click();
 await expect(page.getByText('待审核',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'保存草稿'})).toHaveCount(0);
 role='RND_DIRECTOR';await page.reload();
 await page.getByRole('button',{name:'退回修改'}).click();
 await page.getByPlaceholder('请填写退回原因与修改要求（必填）').fill('补充实测依据');
 await page.getByRole('dialog').getByRole('button',{name:/确.*定/}).click();
 await expect(page.getByText('补充实测依据',{exact:true})).toBeVisible();
 role='RND_ASSISTANT';await page.reload();
 await expect(page.getByRole('button',{name:'保存草稿'})).toBeVisible();
 await page.getByRole('button',{name:'提交总监审核'}).click();await page.getByRole('dialog').getByRole('button',{name:/确.*定/}).click();
 await expect(page.getByText('待审核',{exact:true})).toBeVisible();
 role='RND_DIRECTOR';await page.reload();
 await page.getByRole('button',{name:'批准并纳入正式版本'}).click();await page.getByRole('dialog').getByRole('button',{name:/确.*定/}).click();
 await expect(page.getByText('已发布',{exact:true})).toBeVisible();
 await expect(page.locator('.differences')).toHaveCount(0);
 expect(source.majorProcesses[0].steps[0].instruction).toBe('煮制后沥水');
 expect(requests.map(r=>r.action)).toEqual(['save','submit','return','save','submit','approve']);
 await page.screenshot({path:'test-results/sop-amendment-published.png',fullPage:true});
});
