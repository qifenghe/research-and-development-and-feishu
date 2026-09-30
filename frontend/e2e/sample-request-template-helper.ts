import {expect,type Page} from '@playwright/test';
import {sampleRequestTemplateGroups} from '../packages/shared/src/sample-request-template';
export async function exerciseRequest(page:Page,mobile:boolean){
 const base=mobile?(process.env.MOBILE_BASE_URL??'http://127.0.0.1:5194/m/').replace(/\/m\/?$/,''):(process.env.PC_BASE_URL??'http://127.0.0.1:5193');
 const prefix=mobile?'/m':'/admin';
 await page.goto(base+prefix+'/login');
 if(mobile){await page.locator('input[name=username]').fill('rnd_assistant');await page.locator('input[name=password]').fill('123456');}
 else{await page.getByLabel('账号',{exact:true}).fill('rnd_assistant');await page.getByLabel('密码',{exact:true}).fill('123456');}
 await page.getByRole('button',{name:/登.*录/}).click();
 await expect(page).not.toHaveURL(/login$/);
 await page.goto(base+prefix+(mobile?'/request/new':'/demand/new'));
 await page.getByRole('button',{name:'提交需求',exact:true}).click();
 await expect(page.getByText('请选择是否提供标样',{exact:true})).toBeVisible();
 const name='模板验收-'+(mobile?'移动':'电脑')+'-'+Date.now();
 const values:Record<string,string>={customerName:'模板验收客户',productName:name,specification:'500g/袋，10袋/件',cutShape:'20×30×40 mm',mainMaterialRequirement:'牛腩\n去除筋膜',flavorRequirement:'酱香',textureRequirement:'软烂',packagingRequirement:'真空袋',storageRequirement:'冷冻',applicationScenario:'餐饮',customerTargetPrice:'18元/袋，未税',monthlyDemand:'3吨/月',specialRequirements:'无'};
 for(const group of sampleRequestTemplateGroups)for(const f of group.fields){if(values[f.key])await page.getByPlaceholder(f.placeholder,{exact:true}).fill(values[f.key]);}
 await page.getByRole('radio',{name:/否$/}).click();
 await page.getByPlaceholder(mobile?'如：预制菜':'如：预制菜、冷冻即热菜',{exact:true}).fill('预制菜');
 await expect(page.getByText('请填写客户名称',{exact:true})).toHaveCount(0);
 await expect(page.getByText('请选择是否提供标样',{exact:true})).toHaveCount(0);
 await expect(page.getByText(/is required/)).toHaveCount(0);
 await page.screenshot({path:'test-results/request-'+(mobile?'mobile':'pc')+'.png',fullPage:true});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/sample-requests')&&r.request().method()==='POST');
 await page.getByRole('button',{name:'提交需求',exact:true}).click();
 const r=await response;expect(r.ok()).toBe(true);const created=(await r.json()).data;
 const token=await page.evaluate(()=>localStorage.getItem('rnd_access_token'));
 const headers={Authorization:'Bearer '+token};
 const stored=(await (await page.request.get(base+'/api/v1/sample-requests/'+created.id,{headers})).json()).data;
 expect(stored.referenceSampleProvided).toBe(false);expect(stored.cutShape).toBe(values.cutShape);expect(stored.referenceMarketPrice??null).toBeNull();expect(stored.monthlyDemand).toBe('3吨/月');
 const login=await page.request.post(base+'/api/v1/auth/login',{data:{username:'rnd_director',password:'123456'}});
 const auth=(await login.json()).data;const directorHeaders={Authorization:'Bearer '+auth.accessToken};
 const approved=await page.request.post(base+'/api/v1/sample-requests/'+created.id+'/approve',{headers:directorHeaders,data:{reviewerName:'赵总监'}});
 expect(approved.ok()).toBe(true);
 const tasks=(await (await page.request.get(base+'/api/v1/rnd-tasks',{headers:directorHeaders})).json()).data;
 const task=(Array.isArray(tasks)?tasks:tasks.items).find((t:any)=>t.productName===name);
 expect(task).toBeTruthy();
 const detail=(await (await page.request.get(base+'/api/v1/rnd-tasks/'+task.id+'/detail',{headers:directorHeaders})).json()).data;
 expect(detail.sourceRequest.customerTargetPrice).toBe('18元/袋，未税');expect(detail.sourceRequest.referenceSampleProvided).toBe(false);
 await page.goto(base+prefix+'/login');
 if(mobile){await page.locator('input[name=username]').fill('rnd_director');await page.locator('input[name=password]').fill('123456');}
 else{await page.getByLabel('账号',{exact:true}).fill('rnd_director');await page.getByLabel('密码',{exact:true}).fill('123456');}
 await page.getByRole('button',{name:/登.*录/}).click();await expect(page).not.toHaveURL(/login$/);
 await page.goto(base+prefix+(mobile?'/tasks/':'/rnd/tasks/')+task.id);
 await expect(page.getByText('18元/袋，未税',{exact:true})).toBeVisible();
 await page.reload();await expect(page.getByText('20×30×40 mm',{exact:true})).toBeVisible();
}
