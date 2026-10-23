import './mobile-dom-bootstrap.mts';
import assert from 'node:assert/strict';
import test from 'node:test';
import {createRequire} from 'node:module';
import {dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import {createServer} from '../apps/mobile/node_modules/vite/dist/node/index.js';
import {createPinia,setActivePinia} from '../apps/mobile/node_modules/pinia/dist/pinia.mjs';
import {createRouter,createMemoryHistory} from '../apps/mobile/node_modules/vue-router/dist/vue-router.mjs';
const require=createRequire(new URL('../apps/mobile/package.json',import.meta.url));
let server:any;
test.before(async()=>{const configFile=fileURLToPath(new URL('../apps/mobile/vite.config.ts',import.meta.url));server=await createServer({root:dirname(configFile),configFile,server:{middlewareMode:true},ssr:{noExternal:['vant']},appType:'custom',logLevel:'silent'});});
test.after(async()=>server.close());
test('mobile login presents Longhui identity while preserving the credential form',async()=>{
 const {default:View}=await server.ssrLoadModule('/src/views/LoginView.vue');
 const {createSSRApp}=require('vue');const {renderToString}=require('vue/server-renderer');
 const pinia=createPinia();setActivePinia(pinia);const app=createSSRApp(View);app.use(pinia);app.config.warnHandler=()=>{};
 const html=await renderToString(app);
 assert.match(html,/龙汇研发管理系统/);assert.match(html,/alt="龙汇食品"/);
 assert.match(html,/autocomplete="username"/);assert.match(html,/autocomplete="current-password"/);
 assert.match(html,/type="password"/);assert.doesNotMatch(html,/login-brand__mark/);
});
test('mobile branded shell preserves role-specific sample navigation',async()=>{
 const {default:Layout}=await server.ssrLoadModule('/src/layouts/MobileLayout.vue');
 const {useAuthStore}=await server.ssrLoadModule('/src/stores/auth.ts');
 const {createSSRApp}=require('vue');const {renderToString}=require('vue/server-renderer');
 for(const [role,showSamples]of [['RND_ASSISTANT',false],['RND_ENGINEER',true]] as const){
  const pinia=createPinia();setActivePinia(pinia);useAuthStore(pinia).principal={userId:'test',name:'测试',role};
  const router=createRouter({history:createMemoryHistory('/m/'),routes:[{path:'/todo',name:'todo',component:{render:()=>null},meta:{tab:'todo'}}]});await router.push('/todo');await router.isReady();
  const app=createSSRApp(Layout);app.use(pinia);app.use(router);app.config.warnHandler=()=>{};
  const html=await renderToString(app);assert.match(html,/龙汇研发管理系统/);assert.match(html,/工作台/);
  assert.equal(html.includes('样品'),showSamples);
 }
});
