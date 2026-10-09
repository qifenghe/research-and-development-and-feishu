import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
for(const role of ['RND_ASSISTANT','RND_DIRECTOR','RND_ENGINEER','TESTER','FINANCE','SYSTEM_ADMIN'])test(role+' menu follows consolidated destinations',async({page})=>{
 await page.addInitScript(role=>{localStorage.setItem('rnd_access_token','nav-fixture');localStorage.setItem('rnd_user',JSON.stringify({id:role,name:'导航验收',role}));},role);
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  const allowed=path==='/api/v1/sops'?['RND_DIRECTOR','RND_ENGINEER','TESTER'].includes(role):path==='/api/v1/materials'?role!=='FINANCE'&&role!=='TESTER':true;
  await route.fulfill({status:allowed?200:403,json:{code:allowed?'0':'FORBIDDEN',data:{items:[],total:0,page:1,size:1}}});
 });
 await page.goto(base+'/admin/403');
 const nav=page.locator('.sidebar');
 await expect(nav.getByText('成果文件',{exact:true})).toBeVisible();
 await expect(nav.getByText('模块首页',{exact:true})).toHaveCount(0);
 await expect(nav.getByText('流程图',{exact:true})).toHaveCount(0);
 await nav.getByText('成果文件',{exact:true}).click();
 await expect(nav.getByText('报表导出',{exact:true})).toBeVisible();
 if(role==='FINANCE'){
  await expect(nav.getByText('核价文件接收',{exact:true})).toBeVisible();
  await expect(nav.getByText('核价基础文件',{exact:true})).toHaveCount(0);
  await expect(nav.getByText('样品需求',{exact:true})).toHaveCount(0);
 }else if(role==='RND_DIRECTOR')await expect(nav.getByText('SOP 管理',{exact:true})).toBeVisible();
 if(role==='SYSTEM_ADMIN'){
  await nav.getByText('系统设置',{exact:true}).click();
  await expect(nav.getByText('业务配置',{exact:true})).toBeVisible();
  await expect(nav.getByText('用户管理',{exact:true})).toBeVisible();
 }
});
