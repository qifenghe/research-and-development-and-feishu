import assert from 'node:assert/strict';
import test from 'node:test';
import {waitForMobileContent} from '../apps/mobile/src/utils/scrollReady.ts';
test('saved scroll waits until asynchronous list is rendered and disconnects observer',async()=>{
 let busy=true,changed:()=>void=()=>{},disconnected=false,done=false;
 Object.assign(globalThis,{document:{body:{},querySelector:()=>busy?{}:null},window:{setTimeout,clearTimeout},MutationObserver:class{constructor(callback:()=>void){changed=callback;}observe(){}disconnect(){disconnected=true;}}});
 const pending=waitForMobileContent().then(()=>{done=true;});
 await Promise.resolve();assert.equal(done,false);changed();await Promise.resolve();assert.equal(done,false);
 busy=false;changed();await pending;assert.equal(done,true);assert.equal(disconnected,true);
});
test('ready or failed list does not block restoration',async()=>{
 Object.assign(globalThis,{document:{querySelector:()=>null}});await waitForMobileContent();
});
