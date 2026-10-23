/** Wait for list hydration before restoring a saved offset against its height. */
export function waitForMobileContent():Promise<void>{
  const busy=()=>!!document.querySelector('main [aria-busy="true"]');
  if(!busy())return Promise.resolve();
  return new Promise(resolve=>{
    const finish=()=>{observer.disconnect();window.clearTimeout(timer);resolve();};
    const observer=new MutationObserver(()=>{if(!busy())finish();});
    const timer=window.setTimeout(finish,15000);
    observer.observe(document.body,{subtree:true,childList:true,attributes:true,attributeFilter:['aria-busy']});
    if(!busy())finish();
  });
}
