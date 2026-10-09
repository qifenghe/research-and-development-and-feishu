export interface SopDifference {
 entityId:string;fieldPath:string;type:'ADDED'|'REMOVED'|'CHANGED'|'MOVED';
 before:unknown;after:unknown;derived:boolean;
}
type Node=Record<string,any>;
/** Assign UI keys to new entities; never mutate the immutable source. */
export function prepareSopSnapshot<T>(snapshot:T):T & Node {
 const copy=structuredClone(snapshot) as T & Node;
 const identify=(items:Node[]|undefined)=>{
  for(const item of items??[]){
   if(!item.id){if(!item.key)throw new Error('新增工艺项缺少标识，请重新添加');item.id=item.key;}
  }
 };
 identify(copy.majorProcesses);
 for(const major of copy.majorProcesses??[]){
  identify(major.steps);identify(major.inputs);identify(major.outputs);
  for(const step of major.steps??[]){
   identify(step.materials);identify(step.outputs);identify(step.controlPoints);
   for(const point of step.controlPoints??[])identify(point.measurements);
  }
 }
 return copy;
}
const escape=(value:string)=>value.replaceAll('~','~0').replaceAll('/','~1');
const numberText=(value:unknown)=>typeof value==='string'&&/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value.trim());
function equalNumber(a:unknown,b:unknown){
 const canonical=(v:unknown)=>{
  let text=String(v).trim(),negative=text.startsWith('-');text=text.replace(/^[+-]/,'');
  const [whole='',fraction='']=text.split('.');
  const integer=whole.replace(/^0+(?=\d)/,'')||'0',decimal=fraction.replace(/0+$/,'');
  return `${negative&&(integer!=='0'||decimal)?'-':''}${integer}${decimal?'.'+decimal:''}`;
 };
 return canonical(a)===canonical(b);
}
/** Live presentation only. Server recalculates and validates before saving. */
export function sopDraftDifferences(before:Node,after:Node):SopDifference[]{
 const result:SopDifference[]=[];
 const walk=(a:any,b:any,path:string,entityId:string,numeric=false)=>{
  if(a===b)return;
  if(numeric&&numberText(a)&&numberText(b)&&equalNumber(a,b))return;
  if(a==null&&b==null)return;
  const emit=(type:SopDifference['type'])=>result.push({entityId,fieldPath:path,type,before:a??null,after:b??null,derived:path.includes('/yield/')||path.endsWith('/yield')||path==='/batchYieldPercent'});
  if(Array.isArray(a)||Array.isArray(b)){
   const index=(list:any[])=>new Map(list.map(item=>[String(item.id??item.key),item]));
   const left=index(a??[]),right=index(b??[]);
   for(const key of new Set([...left.keys(),...right.keys()]))walk(left.get(key),right.get(key),`${path}/${escape(key)}`,key);
  }else if(a&&b&&typeof a==='object'&&typeof b==='object'){
   for(const key of new Set([...Object.keys(a),...Object.keys(b)])){
    if(key==='key')continue;
    walk(a[key],b[key],`${path}/${escape(key)}`,entityId,
     (a.valueType==='NUMBER'||b.valueType==='NUMBER')&&['actualValue','targetValue','lowerLimit','upperLimit'].includes(key));
   }
  }else emit(a===undefined?'ADDED':b===undefined?'REMOVED':path.endsWith('/sequence')?'MOVED':'CHANGED');
 };
 for(const key of ['balanceToleranceKg','majorProcesses','batchYieldPercent'])walk(before[key],after[key],`/${key}`,'');
 return result;
}
