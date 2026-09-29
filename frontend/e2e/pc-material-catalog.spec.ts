import {test,expect,type Page} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
async function login(page:Page,user:string){await page.goto(`${base}/admin/login`);await page.getByLabel('账号',{exact:true}).fill(user);await page.getByLabel('密码',{exact:true}).fill('123456');await page.getByRole('button',{name:/登.*录/}).click();await expect(page.locator('.topbar')).toBeVisible();await page.goto(`${base}/admin/materials`);await expect(page.getByRole('heading',{name:'物料管理'})).toBeVisible();}
test('administrator creates a classification and a material',async({page})=>{
 await login(page,'admin');const stamp=Date.now();const name=`浏览器验收原料${stamp}`;
 await page.getByRole('button',{name:'管理分类',exact:true}).click();
 await page.getByRole('button',{name:'新增分类',exact:true}).click();
  const form=page.getByRole('dialog').last();await form.getByRole('textbox').first().fill(name);await form.getByRole('button',{name:'OK',exact:true}).click();
 await expect(page.getByRole('dialog').first().getByText(name,{exact:true})).toBeVisible();
 await page.getByRole('dialog').first().locator('.ant-modal-close').click();
 await page.getByRole('button',{name:'新增物料',exact:true}).click();const material=page.getByRole('dialog').last();
 await material.getByRole('textbox').nth(0).fill(`QA-${stamp}`);await material.getByRole('textbox').nth(1).fill(`验收牛肉${stamp}`);
 await material.locator('.ant-select').first().click();await page.locator('.ant-select-item-option').filter({hasText:name}).click();
  await material.getByRole('button',{name:'OK',exact:true}).click();await expect(page.getByText(`验收牛肉${stamp}`,{exact:true})).toBeVisible();
 await page.screenshot({path:'test-results/material-catalog-admin.png',fullPage:true});
});
test('assistant can maintain materials but cannot manage classification',async({page})=>{
 await login(page,'rnd_assistant');await expect(page.getByRole('button',{name:'新增物料',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'管理分类',exact:true})).toHaveCount(0);
 const token=await page.evaluate(()=>localStorage.getItem('rnd_access_token'));const r=await page.request.post(`${base}/api/v1/material-classifications`,{headers:{Authorization:`Bearer ${token}`},data:{name:'越权分类',businessType:'RAW'}});expect(r.status()).toBe(403);
});
test('engineer sees searchable library without maintenance buttons',async({page})=>{
 await login(page,'rnd_engineer');await expect(page.getByPlaceholder('搜索物料编码或名称')).toBeVisible();await expect(page.getByRole('button',{name:'新增物料',exact:true})).toHaveCount(0);await expect(page.getByRole('button',{name:'管理分类',exact:true})).toHaveCount(0);
});
