import {test,expect} from '@playwright/test';
const base=process.env.PC_BASE_URL??'http://127.0.0.1:5193';
test('Longhui identity is readable on login and administrative header',async({page})=>{
 await page.goto(base+'/admin/login');
 await expect(page).toHaveTitle('龙汇研发管理系统');
 const loginLogo=page.getByRole('img',{name:'龙汇食品'});
 await expect(loginLogo).toBeVisible();
 await expect(page.locator('.login-card .ant-alert')).toHaveCSS('background-color','rgb(237, 244, 240)');
 expect(await loginLogo.evaluate((img:HTMLImageElement)=>img.complete&&img.naturalWidth>0)).toBe(true);
 await page.screenshot({path:'test-results/longhui-login.png',fullPage:true});
 await page.evaluate(()=>{localStorage.setItem('rnd_access_token','theme-fixture');localStorage.setItem('rnd_user',JSON.stringify({id:'U',name:'内勤',role:'RND_ASSISTANT'}));});
 await page.route('**/api/v1/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  const data=path==='/api/v1/session/me'?{userId:'U',name:'内勤',role:'RND_ASSISTANT'}:{items:[],total:0};
  await route.fulfill({json:{code:'0',data}});
 });
 await page.goto(base+'/admin/403');
 const ink=await page.locator('.topbar').evaluate(el=>{const style=getComputedStyle(el,'::after');return {image:style.backgroundImage,pointerEvents:style.pointerEvents};});
 expect(ink.image).toContain('longhui-ink');
 expect(ink.pointerEvents).toBe('none');
 const artUrl=ink.image.match(/url\(["']?([^"')]+)/)?.[1];
 expect(artUrl).toBeTruthy();
 expect((await page.request.get(artUrl!)).ok()).toBe(true);
 await expect(page.locator('.brand-title')).toHaveText('龙汇研发管理系统');
 await expect(page.getByRole('img',{name:'龙汇食品'})).toBeVisible();
 const rectangles=await page.locator('.brand').evaluate(el=>{
  const logo=el.querySelector('img')!.getBoundingClientRect(),title=el.querySelector('.brand-title')!.getBoundingClientRect();
  return {logoRight:logo.right,titleLeft:title.left,logoBottom:logo.bottom,headerBottom:el.closest('header')!.getBoundingClientRect().bottom};
 });
 expect(rectangles.logoRight).toBeLessThan(rectangles.titleLeft);
 expect(rectangles.logoBottom).toBeLessThanOrEqual(rectangles.headerBottom);
 await page.screenshot({path:'test-results/longhui-header.png',fullPage:true});
});
