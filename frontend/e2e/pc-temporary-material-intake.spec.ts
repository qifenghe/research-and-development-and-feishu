import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
test('director sees intake under reference navigation and must enter a return comment',async({page})=>{
 await page.addInitScript(()=>{localStorage.setItem('rnd_access_token','intake-fixture');localStorage.setItem('rnd_user',JSON.stringify({id:'D',name:'总监',role:'RND_DIRECTOR'}));});
 let returns=0;let row={id:'MI1',formId:'F1',sourceVersion:2,applicantName:'研发',status:'PENDING',version:1,lines:[{temporaryMaterialId:'TMP1',code:'NEW1',name:'试验牛肉',specification:'20mm',totalWeightKg:10}]};
 await page.route('**/api/v1/**',async route=>{const path=new URL(route.request().url()).pathname;let data:any={};
 if(path==='/api/v1/session/me')data={userId:'D',name:'总监',role:'RND_DIRECTOR'};
 else if(path==='/api/v1/material-intakes')data=[row];
 else if(path.endsWith('/return')){returns++;const body=route.request().postDataJSON();expect(body.comment).toBe('请补充准确规格');row={...row,status:'RETURNED',version:2};data=row;}
 else if(path==='/api/v1/materials'||path==='/api/v1/sops')data={items:[],total:0};
 await route.fulfill({json:{code:'0',data}});});
 await page.goto(base+'/admin/material-intakes/review');
 await expect(page.getByRole('heading',{name:'物料入库审核'})).toBeVisible();
 await page.getByRole('button',{name:'查看审核'}).click();
 await page.getByRole('button',{name:'驳回重填'}).click();
 await expect(page.getByText('请填写退回批语',{exact:true})).toBeVisible();expect(returns).toBe(0);
 await page.getByPlaceholder('退回批语（驳回必填）').fill('请补充准确规格');
 await page.getByRole('button',{name:'驳回重填'}).click();await expect.poll(()=>returns).toBe(1);
 await page.screenshot({path:'test-results/material-intake-review.png',fullPage:true});
});
