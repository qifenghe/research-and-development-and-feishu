export interface NavigationGroup {key:string;label:string;children?:NavigationGroup[]}
export function buildAdminNavigation(canRead:(path:string)=>boolean, capabilities:{materials:boolean;sops:boolean}):NavigationGroup[]{
 const item=(key:string,label:string):NavigationGroup=>({key,label});
 const groups:NavigationGroup[]=[
  item('/dashboard','工作台'),
  {key:'demand',label:'样品需求',children:[item('/demand/list','需求列表'),item('/demand/review','需求审核')]},
  {key:'rnd',label:'研发管理',children:[item('/rnd/my-tasks','打样任务'),item('/rnd/pool','待分配任务'),item('/rnd/pending-tests','内部测试'),item('/rnd/stopped','停止项目')]},
  {key:'shipment',label:'寄样管理',children:[item('/shipment/list','寄样与客户反馈')]},
  {key:'outputs',label:'成果文件',children:[item('/sops','SOP 管理'),item('/pricing/list','核价基础文件'),...(!canRead('/pricing/list')?[item('/finance','核价文件接收')]:[]),item('/archive','文件归档'),item('/reports','报表导出')]},
  {key:'reference',label:'基础资料',children:[item('/materials','物料库'),item('/material-intakes/review','物料入库审核')]},
  {key:'settings',label:'系统设置',children:[item('/settings/users','用户管理'),item('/settings/role-permissions','角色权限'),item('/settings','业务配置')]},
 ];
 const visible=(key:string)=>key==='/sops'?capabilities.sops:key==='/materials'?capabilities.materials:canRead(key);
 return groups.flatMap(group=>group.children?([{...group,children:group.children.filter(c=>visible(c.key))}].filter(g=>g.children.length>0)):visible(group.key)?[group]:[]);
}
export function navigationSelection(path:string):{selectedKey:string;openKey:string}{
 const p=path.split('?')[0];
 if(p.startsWith('/demand'))return {selectedKey:p==='/demand/review'?p:'/demand/list',openKey:'demand'};
 if(p.startsWith('/rnd'))return {selectedKey:p==='/rnd/pool'||p==='/rnd/assign'?'/rnd/pool':p==='/rnd/stopped'?p:p==='/rnd/pending-tests'||/\/test$/.test(p)?'/rnd/pending-tests':'/rnd/my-tasks',openKey:'rnd'};
 if(p.startsWith('/shipment'))return {selectedKey:'/shipment/list',openKey:'shipment'};
 if(p.startsWith('/sops'))return {selectedKey:'/sops',openKey:'outputs'};
 if(p.startsWith('/pricing'))return {selectedKey:'/pricing/list',openKey:'outputs'};
 if(['/finance','/archive','/reports'].includes(p))return {selectedKey:p,openKey:'outputs'};
 if(p.startsWith('/material-intakes'))return {selectedKey:'/material-intakes/review',openKey:'reference'};
 if(p.startsWith('/materials'))return {selectedKey:'/materials',openKey:'reference'};
 if(p.startsWith('/settings'))return {selectedKey:['/settings/users','/settings/role-permissions'].includes(p)?p:'/settings',openKey:'settings'};
 return {selectedKey:'/dashboard',openKey:''};
}
