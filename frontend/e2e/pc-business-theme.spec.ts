import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
for(const [path,title] of [
 ['/materials','物料管理'],['/sops','SOP 管理'],['/pricing/list','核价文件列表'],
 ['/shipment/list','寄样反馈列表'],['/rnd/my-tasks','我的打样任务'],
 ['/rnd/pool','PC端：研发任务池'],['/rnd/stopped','停止/废弃项目池'],
 ['/demand/list','样品需求列表'],['/demand/review','研发总监：需求审核'],
])test('unified business layout '+path,async({page})=>{
 await page.goto(base+'/admin/login');
 await page.getByLabel('账号',{exact:true}).fill(path==='/sops'?'rnd_director':'admin');
 await page.getByLabel('密码',{exact:true}).fill('123456');
 await page.getByRole('button',{name:/登.*录/}).click();
 await expect(page.locator('.topbar')).toBeVisible();
 await page.goto(base+'/admin'+path);
 await expect(page.locator('.content').getByText(title,{exact:true}).first()).toBeVisible();
 await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
 if(path==='/demand/list')await expect(page.locator('.ant-table-tbody')).not.toContainText(/\d{4}-\d{2}-\d{2}T/);
 if(path==='/pricing/list')await expect(page.locator('.ant-table-tbody').last()).not.toContainText(/DRAFT_PACKAGING|FINANCE_NOTIFIED/);
 if(path==='/sops')await expect(page.locator('.content .ant-alert-error')).toHaveCount(0);
 const header=page.locator('.content .ant-page-header');
 if(await header.count())await expect(header).toHaveCSS('border-bottom-color','rgb(221, 222, 213)');
 const th=page.locator('.content th').first();
 await expect(th).toHaveCSS('background-color','rgb(238, 235, 224)');
 const surface=page.locator(path==='/materials'?'.catalog-body':path==='/sops'?'.catalog-card':'.content .ant-card').first();
 await expect(surface).toHaveCSS('background-color','rgb(248, 246, 239)');
 if(path!=='/sops')await expect(page.locator('.content .ant-table').first()).toHaveCSS('background-color','rgba(0, 0, 0, 0)');
 await expect(th).toHaveCSS('white-space','nowrap');
 if(await page.locator('.content .ant-btn-link:not(:disabled):not(.ant-btn-dangerous)').count())
  await expect(page.locator('.content .ant-btn-link:not(:disabled):not(.ant-btn-dangerous)').first()).toHaveCSS('color','rgb(40, 93, 89)');
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth);
 expect(overflow).toBe(false);
 await page.screenshot({path:`test-results/business-${path.replaceAll('/','-')}.png`,fullPage:true,animations:'disabled'});
});

for(const path of ['/demand/list','/pricing/list','/rnd/pool'])test('detail surface '+path,async({page})=>{
 await page.goto(base+'/admin/login');
 await page.getByLabel('账号',{exact:true}).fill('admin');await page.getByLabel('密码',{exact:true}).fill('123456');
 await page.getByRole('button',{name:/登.*录/}).click();await expect(page.locator('.topbar')).toBeVisible();
 await page.goto(base+'/admin'+path);await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
 await page.locator('.ant-table-tbody').getByRole('button',{name:/^查\s*看$/}).first().click();
 await expect(page).not.toHaveURL(base+'/admin'+path);
 await expect(page.locator('.content .ant-page-header')).toHaveCSS('border-bottom-color','rgb(221, 222, 213)');
 await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
 await expect(page.locator('.content .ant-card').first()).toBeVisible();
 await expect(page.locator('.content .ant-card').first()).toHaveCSS('background-color','rgb(248, 246, 239)');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
 await page.screenshot({path:`test-results/detail-${path.replaceAll('/','-')}.png`,fullPage:true,animations:'disabled'});
});
