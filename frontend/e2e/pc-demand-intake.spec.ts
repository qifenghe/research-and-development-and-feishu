import {test,expect} from '@playwright/test';
import {sampleRequestTemplateGroups} from '../packages/shared/src/sample-request-template';
import {templateRequirementFixture} from './sample-request-fixture';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';

test('manager does not see director-only review actions',async({page})=>{
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','fixture');localStorage.setItem('rnd_user',JSON.stringify({id:'M',name:'经理',role:'MANAGER'}));});
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  await route.fulfill({json:{code:'0',data:path==='/api/v1/sample-requests'?[{id:'REQ-M',sampleNo:'YP-M',productName:'角色验收',status:'PENDING_REVIEW'}]:path==='/api/v1/session/me'?{userId:'M',name:'经理',role:'MANAGER'}:{}}});
 });
 await page.goto(base+'/admin/demand/review');
 await expect(page.getByText('角色验收',{exact:true})).toBeVisible();
 await expect(page.getByRole('button',{name:'驳回重填',exact:true})).toHaveCount(0);
 await expect(page.getByRole('button',{name:'审核通过',exact:true})).toHaveCount(0);
});

test('sales applicant is editable, recorder is readonly, failed creation keeps input',async({page})=>{
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','fixture');localStorage.setItem('rnd_user',JSON.stringify({id:'A',name:'真实内勤',role:'RND_ASSISTANT'}));});
 let sent:any;
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/v1/sample-requests'&&route.request().method()==='POST') {sent=route.request().postDataJSON();await route.fulfill({status:400,json:{code:'FIXTURE_FAILURE',message:'暂时无法保存'}});return;}
  await route.fulfill({json:{code:'0',data:path==='/api/v1/sample-requests'?[]:path==='/api/v1/session/me'?{userId:'A',name:'真实内勤',role:'RND_ASSISTANT'}:{}}});
 });
 await page.goto(base+'/admin/demand/new');
 await expect(page.getByLabel('销售申请人',{exact:true})).toBeEditable();
 await expect(page.getByLabel('录入人',{exact:true})).toHaveValue('真实内勤');
 await expect(page.getByLabel('录入人',{exact:true})).toHaveAttribute('readonly','');
 for(const group of sampleRequestTemplateGroups) for(const field of group.fields){
  if(field.key==='referenceSampleProvided')await page.getByRole('radio',{name:'否',exact:true}).check();
  else await page.getByLabel(field.label,{exact:true}).fill(String((templateRequirementFixture as any)[field.key]??'无'));
 }
 await page.getByLabel('产品类型',{exact:true}).fill('预制菜');
 await page.getByRole('button',{name:'提交需求',exact:true}).click();
 expect(sent).toBeUndefined();await expect(page.getByText('请填写销售申请人',{exact:true})).toBeVisible();
 await page.getByLabel('销售申请人',{exact:true}).fill('销售张');
 await page.getByRole('button',{name:'提交需求',exact:true}).click();
 await expect(page.getByText('暂时无法保存',{exact:true})).toBeVisible();
 expect(sent.creatorName).toBe('销售张');expect(sent.recorderName).toBeUndefined();
 await expect(page.getByLabel('销售申请人',{exact:true})).toHaveValue('销售张');
});

test('rejection requires comments and resubmission edits original request with complete review history',async({page})=>{
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','fixture');localStorage.setItem('rnd_user',JSON.stringify({id:'D',name:'总监',role:'SYSTEM_ADMIN'}));});
 let row:any={...templateRequirementFixture,id:'REQ-INTAKE',sampleNo:'YP-INTAKE',productName:'代录验收牛腩',productType:'预制菜',customerName:'验收客户',specification:'500g',applicationScenario:'餐饮',flavorRequirement:'香辣',creatorName:'销售张',recorderName:'真实内勤',reviewVersion:0,status:'PENDING_REVIEW',createdAt:'2026-10-05T12:00:00',canResubmit:true,reviewHistory:[{versionNo:0,action:'SUBMITTED',actorName:'真实内勤',actedAt:'2026-10-05T12:00:00'}]};
 let creates=0,rejects=0,resubmits=0;
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;let data:any={};
  if(path==='/api/v1/session/me')data={userId:'D',name:'总监',role:'SYSTEM_ADMIN'};
  else if(path==='/api/v1/sample-requests'){
   if(route.request().method()==='POST')creates++;
   const status=new URL(route.request().url()).searchParams.get('status');data=status&&status!==row.status?[]:[row];
  }else if(path.endsWith('/reject')){
   const command=route.request().postDataJSON();expect(command.versionNo).toBe(0);expect(command.comment).toBe('请明确块型');rejects++;
   row={...row,status:'REJECTED',reviewVersion:1,reviewHistory:[...row.reviewHistory,{versionNo:1,action:'REJECTED',actorName:'总监',comment:command.comment,actedAt:'2026-10-05T12:05:00'}]};data=row;
  }else if(path.endsWith('/resubmit')){
   const command=route.request().postDataJSON();expect(command.versionNo).toBe(1);expect(command.request.creatorName).toBe('销售李');resubmits++;
   row={...row,...command.request,status:'PENDING_REVIEW',reviewVersion:2,reviewHistory:[...row.reviewHistory,{versionNo:2,action:'RESUBMITTED',actorName:'真实内勤',actedAt:'2026-10-05T12:10:00'}]};data=row;
  }else if(path==='/api/v1/sample-requests/REQ-INTAKE')data=row;
  await route.fulfill({json:{code:'0',data}});
 });
 await page.goto(base+'/admin/demand/review');await page.getByRole('button',{name:'驳回重填',exact:true}).click();
 await page.getByRole('dialog').getByRole('button',{name:'确认驳回',exact:true}).click();
 await expect(page.getByText('请填写驳回批语',{exact:true})).toBeVisible();expect(rejects).toBe(0);
 await page.getByLabel('驳回批语',{exact:true}).fill('请明确块型');await page.getByRole('button',{name:'确认驳回',exact:true}).click();
 await expect(page.getByRole('dialog')).toHaveCount(0);expect(rejects).toBe(1);
 await page.goto(base+'/admin/demand/REQ-INTAKE');await expect(page.getByText('请明确块型',{exact:true})).toBeVisible();
 await page.getByRole('button',{name:'修改并重新提交',exact:true}).click();
 await expect(page).toHaveURL(/REQ-INTAKE\/edit$/);await expect(page.getByText('YP-INTAKE',{exact:false})).toBeVisible();
 await page.getByLabel('销售申请人',{exact:true}).fill('销售李');
 await page.getByRole('button',{name:'重新提交审核',exact:true}).click();
 await expect(page).toHaveURL(/demand\/REQ-INTAKE$/);
 await expect(page.getByText('销售李',{exact:true})).toBeVisible();await expect(page.getByText('请明确块型',{exact:true})).toBeVisible();
 expect(resubmits).toBe(1);expect(creates).toBe(0);expect(row.sampleNo).toBe('YP-INTAKE');
 await page.screenshot({path:'test-results/demand-intake-history.png',fullPage:true});
});
