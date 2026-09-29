import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
test('kg weight saves reloads and switching to bags clears counted quantity',async({page,request})=>{
  async function call(actor:any,method:string,path:string,data?:unknown){
    const r=await request.fetch(`${base}/api/v1${path}`,{method,data,headers:actor?{Authorization:`Bearer ${actor.accessToken}`}:{}});
    const body=await r.json();expect(body.code,JSON.stringify(body)).toBe('0');return body.data;
  }
  const login=(username:string)=>call(null,'POST','/auth/login',{username,password:'123456'});
  const assistant=await login('rnd_assistant'),director=await login('rnd_director'),engineer=await login('rnd_engineer');
  const req=await call(assistant,'POST','/sample-requests',{productName:`kg浏览器验收-${Date.now()}`,productType:'预制菜',customerName:'内部验收',specification:'散装kg',applicationScenario:'软件验收',flavorRequirement:'软件验收，非生产数据',creatorName:assistant.user.name});
  const approved=await call(director,'POST',`/sample-requests/${req.id}/approve`,{reviewerName:director.user.name});
  const task=approved.task;
  await call(director,'POST',`/rnd-tasks/${task.id}/assign`,{assigneeName:engineer.user.name,productOwnerName:engineer.user.name,dueDate:'2026-12-31'});
  await call(engineer,'POST',`/rnd-tasks/${task.id}/accept`,{acceptedBy:engineer.user.name});
  await call(engineer,'POST',`/rnd-tasks/${task.id}/experiment-form/draft`,{operatorName:engineer.user.name,summary:'kg浏览器验收',materials:[],processSteps:[],finishedOutputWeightKg:12.345,finishedOutputQuantity:12.345,finishedOutputUnit:'kg'});
  await page.goto(`${base}/admin/login`);
  await page.getByLabel('账号',{exact:true}).fill('rnd_engineer');await page.getByLabel('密码',{exact:true}).fill('123456');await page.getByRole('button',{name:/登.*录/}).click();await expect(page.locator('.topbar')).toBeVisible();
  await page.goto(`${base}/admin/rnd/tasks/${task.id}/experiment`);
  const card=page.locator('.ant-card').filter({has:page.locator('.ant-card-head-title',{hasText:'成品产出'})});
  const fields=card.locator('.ant-input-number-input');await expect(fields.nth(0)).toHaveValue(/^12\.3450?$/);await expect(fields.nth(1)).toHaveValue(/^12\.3450?$/);await expect(fields.nth(1)).toBeDisabled();
  await fields.nth(0).fill('13.125');await fields.nth(0).blur();await expect(fields.nth(1)).toHaveValue(/^13\.1250?$/);
  await expect.poll(async()=>{const d=await call(engineer,'GET',`/rnd-tasks/${task.id}/detail`);return d.currentExperimentForm?.finishedOutputQuantity;}).toBe(13.125);
  await page.reload();await expect(fields.nth(1)).toHaveValue(/^13\.1250?$/);
  await card.locator('.ant-select').click();await page.locator('.ant-select-item-option').filter({hasText:/^袋$/}).click();
  await expect(fields.nth(1)).toHaveValue('');await expect(fields.nth(1)).toBeEnabled();
  await expect(page.getByText('平均每kg重量',{exact:true})).toHaveCount(0);
});
