import test from 'node:test';
import assert from 'node:assert/strict';
import {createSSRApp} from '../apps/pc/node_modules/vue/index.mjs';
import {renderToString} from '../apps/pc/node_modules/vue/server-renderer/index.js';
import {createServer} from '../apps/pc/node_modules/vite/dist/node/index.js';
import {fileURLToPath} from 'node:url';
for(const target of ['pc','mobile'])test(`${target} requirement summary distinguishes no sample from missing historical answer`,async()=>{
 const root=fileURLToPath(new URL(`../apps/${target}`,import.meta.url));
 const server=await createServer({root,configFile:root+'/vite.config.ts',server:{middlewareMode:true},appType:'custom',logLevel:'silent'});
 try{
 const path=target==='pc'?'/src/components/requests/RequestRequirementSummary.vue':'/src/components/RequestRequirementSummary.vue';
 const component=(await server.ssrLoadModule(path)).default;
 const request={referenceSampleProvided:false,cutShape:'20×30×40 mm',customerTargetPrice:'18元/袋，未税',monthlyDemand:'3吨/月'};
 const html=await renderToString(createSSRApp(component,{request}));
 assert.match(html,/>否</);assert.match(html,/20×30×40 mm/);assert.match(html,/18元\/袋，未税/);assert.match(html,/3吨\/月/);
 const old=await renderToString(createSSRApp(component,{request:{...request,referenceSampleProvided:null}}));
 assert.match(old,/未填写（历史需求）/);assert.doesNotMatch(old,/>否</);
 }finally{await server.close();}
});
