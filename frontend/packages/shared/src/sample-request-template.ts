export interface SampleRequestTemplateFields {
 customerName: string;
 productName: string;
 referenceSampleProvided: boolean|null;
 specification: string;
 cutShape: string;
 mainMaterialRequirement: string;
 flavorRequirement: string;
 textureRequirement: string;
 packagingRequirement: string;
 storageRequirement: string;
 applicationScenario: string;
 customerTargetPrice: string;
 monthlyDemand: string;
 specialRequirements: string;
 referenceMarketPrice: string;
 additionalNotes: string;
}
export interface SampleRequestField {key:keyof SampleRequestTemplateFields;label:string;placeholder:string;required:boolean}
export function sampleRequestMaxLength(key:keyof SampleRequestTemplateFields):number {
 const limits:Partial<Record<keyof SampleRequestTemplateFields,number>>={productName:200,customerName:200,specification:100,applicationScenario:500,flavorRequirement:500};
 return limits[key]??2000;
}
export const sampleRequestTemplateGroups:Array<{title:string;fields:SampleRequestField[]}>=[
  {
    "title": "基本信息",
    "fields": [
      {
        "key": "customerName",
        "label": "客户名称",
        "placeholder": "填写客户名称",
        "required": true
      },
      {
        "key": "productName",
        "label": "产品名称",
        "placeholder": "填写产品名称",
        "required": true
      },
      {
        "key": "referenceSampleProvided",
        "label": "是否提供标样",
        "placeholder": "请选择",
        "required": true
      },
      {
        "key": "specification",
        "label": "产品规格",
        "placeholder": "如：500g/袋，10袋/件",
        "required": true
      }
    ]
  },
  {
    "title": "产品要求",
    "fields": [
      {
        "key": "cutShape",
        "label": "块型",
        "placeholder": "如：大块、片状、条状；20×30×40 mm",
        "required": true
      },
      {
        "key": "mainMaterialRequirement",
        "label": "主要原料要求",
        "placeholder": "如：牛腩、鸡腿肉、肥瘦比例、指定部位",
        "required": true
      },
      {
        "key": "flavorRequirement",
        "label": "味型",
        "placeholder": "如：香辣、麻辣、酱香、五香",
        "required": true
      },
      {
        "key": "textureRequirement",
        "label": "口感",
        "placeholder": "如：软烂、Q弹、脆嫩、有嚼劲",
        "required": true
      }
    ]
  },
  {
    "title": "包装与使用",
    "fields": [
      {
        "key": "packagingRequirement",
        "label": "包装要求",
        "placeholder": "如：真空袋/盒装；500g×10袋/件",
        "required": true
      },
      {
        "key": "storageRequirement",
        "label": "储存方式",
        "placeholder": "如：冷冻、冷藏、常温及具体要求",
        "required": true
      },
      {
        "key": "applicationScenario",
        "label": "应用场景",
        "placeholder": "如：餐饮门店、团餐、电商、商超",
        "required": true
      }
    ]
  },
  {
    "title": "商务及补充",
    "fields": [
      {
        "key": "customerTargetPrice",
        "label": "客户目标价格",
        "placeholder": "如：18元/袋、36元/kg；注明含税/未税",
        "required": true
      },
      {
        "key": "monthlyDemand",
        "label": "需求量（每月）",
        "placeholder": "如：5000袋/月、3吨/月",
        "required": true
      },
      {
        "key": "specialRequirements",
        "label": "其他特殊要求",
        "placeholder": "如：禁用原料、添加剂要求、过敏原；无则填“无”",
        "required": true
      },
      {
        "key": "referenceMarketPrice",
        "label": "行情/参考价格",
        "placeholder": "选填，如：市场同类产品约20元/袋",
        "required": false
      },
      {
        "key": "additionalNotes",
        "label": "其他补充说明",
        "placeholder": "选填",
        "required": false
      }
    ]
  }
];
export function emptySampleRequestTemplate():SampleRequestTemplateFields {return {customerName:'',productName:'',referenceSampleProvided:null,specification:'',cutShape:'',mainMaterialRequirement:'',flavorRequirement:'',textureRequirement:'',packagingRequirement:'',storageRequirement:'',applicationScenario:'',customerTargetPrice:'',monthlyDemand:'',specialRequirements:'',referenceMarketPrice:'',additionalNotes:''};}
export function validateSampleRequestTemplate(value:SampleRequestTemplateFields):Record<string,string>{
 const errors:Record<string,string>={};
 for(const group of sampleRequestTemplateGroups)for(const field of group.fields){
  const raw=value[field.key];
  if(field.key==='referenceSampleProvided'){if(typeof raw!=='boolean')errors[field.key]='请选择是否提供标样';continue;}
  const text=typeof raw==='string'?raw.trim():'';
  if(field.required&&!text)errors[field.key]='请填写'+field.label;
  else if(text.length>sampleRequestMaxLength(field.key))errors[field.key]=field.label+'不能超过'+sampleRequestMaxLength(field.key)+'字';
 }
 return errors;
}
