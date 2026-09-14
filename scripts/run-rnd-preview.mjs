// Local persistent trial: node scripts/run-rnd-preview.mjs build|start|status|stop
import fs from 'node:fs';
import path from 'node:path';
import net from 'node:net';
import {fileURLToPath} from 'node:url';
import {spawn, spawnSync} from 'node:child_process';

const script = fileURLToPath(import.meta.url);
const root = path.resolve(path.dirname(script), '..');
const data = path.join(root, 'data/preview');
const pidFile = path.join(data, 'supervisor.pid');
const javaHome = process.env.JAVA_HOME || '/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home';
const env = {...process.env, JAVA_HOME: javaHome, RND_PREVIEW_DATA_DIR: data, RND_BACKEND_URL: 'http://127.0.0.1:8082'};
const apps = ['pc', 'mobile'].map(name => path.join(root, 'frontend/apps', name));
const jar = path.join(root, 'backend/target/rnd-sample-system-0.1.0-SNAPSHOT.jar');
const command = process.argv[2] || 'status';
fs.mkdirSync(data, {recursive:true});
function supervisorPid() {
  if (!fs.existsSync(pidFile)) return null;
  const pid = Number(fs.readFileSync(pidFile, 'utf8'));
  if (!Number.isInteger(pid) || pid <= 1) return null;
  const actual = spawnSync('/bin/ps', ['-p', String(pid), '-o', 'command='], {encoding:'utf8'});
  if (actual.error || actual.status !== 0) return null;
  return actual.stdout.includes(script) && actual.stdout.includes('serve') ? pid : null;
}
async function portAvailable(port) {
  await new Promise((resolve,reject) => { const server=net.createServer(); server.once('error',reject); server.listen(port,'127.0.0.1',()=>server.close(resolve)); });
}
function run(cmd, args, cwd) {
  const result = spawnSync(cmd,args,{cwd,env,stdio:'inherit'});
  if (result.error || result.status !== 0) throw new Error(`${cmd} failed: ${result.error?.message || result.status}`);
}
async function health() {
  for (const [label,url] of [['后端','http://127.0.0.1:8082/actuator/health'],['PC','http://127.0.0.1:5183/admin/'],['手机','http://127.0.0.1:5184/m/']]) {
    try { const result=await fetch(url,{signal:AbortSignal.timeout(2500)}); console.log(`${label}: ${result.status} ${url}`); }
    catch { console.log(`${label}: 未就绪 ${url}`); }
  }
}
if (command === 'build') {
  if (supervisorPid()) throw new Error('请先 stop，再构建并 start，避免覆盖正在使用的产物。');
  run('mvn',['-q','-DskipTests','package'],path.join(root,'backend'));
  for (const app of apps) run(path.join(app,'node_modules/.bin/vite'),['build'],app);
} else if (command === 'start') {
  if (supervisorPid()) { console.log('试用环境已运行。'); await health(); }
  else {
    if (!fs.existsSync(jar) || apps.some(app=>!fs.existsSync(path.join(app,'dist/index.html')))) throw new Error('请先运行 build。');
    for (const port of [8082,5183,5184]) await portAvailable(port);
    const log = fs.openSync(path.join(data,'supervisor.log'),'a');
    const child = spawn(process.execPath,[script,'serve'],{cwd:root,env,detached:true,stdio:['ignore',log,log]});
    child.unref(); fs.closeSync(log);
    console.log('试用环境正在后台启动，约需10秒。PC：http://127.0.0.1:5183/admin/');
  }
} else if (command === 'serve') {
  fs.writeFileSync(pidFile,String(process.pid));
  let stopping = false;
  const children = new Set();
  const timers = new Set();
  const definitions = [
    ['backend',path.join(javaHome,'bin/java'),['-jar',jar,'--spring.profiles.active=preview'],path.join(root,'backend')],
    ['pc',path.join(apps[0],'node_modules/.bin/vite'),['preview','--host','127.0.0.1','--port','5183','--strictPort'],apps[0]],
    ['mobile',path.join(apps[1],'node_modules/.bin/vite'),['preview','--host','127.0.0.1','--port','5184','--strictPort'],apps[1]],
  ];
  const stop = () => {
    if (stopping) return; stopping=true;
    for(const timer of timers) clearTimeout(timer);
    for(const child of children) { try { child.kill('SIGTERM'); } catch {} }
    if (fs.existsSync(pidFile) && fs.readFileSync(pidFile,'utf8') === String(process.pid)) fs.unlinkSync(pidFile);
  };
  function launch(definition, attempts=0) {
    if (stopping) return;
    const [name,cmd,args,cwd] = definition;
    const log=fs.openSync(path.join(data,`${name}.log`),'a');
    const child=spawn(cmd,args,{cwd,env,stdio:['ignore',log,log]}); fs.closeSync(log); children.add(child);
    child.on('error',error=>console.error(name,error.message));
    child.on('exit',code=>{
      children.delete(child);
      if(stopping) return;
      console.error(`${name} exited (${code})`);
      if(attempts >= 3) { console.error('重启失败，请查看日志。'); stop(); return; }
      const timer=setTimeout(()=>{timers.delete(timer);launch(definition,attempts+1);},3000); timers.add(timer);
    });
  }
  process.on('SIGTERM',stop); process.on('SIGINT',stop);
  for(const definition of definitions) launch(definition);
} else if(command === 'stop') {
  const pid=supervisorPid();
  if(pid) { process.kill(pid,'SIGTERM'); console.log('已请求停止试用服务；数据库和成果文件保留。'); }
  else console.log('没有找到本项目的试用服务。');
} else if(command === 'status') await health();
else throw new Error('支持 build / start / status / stop');
