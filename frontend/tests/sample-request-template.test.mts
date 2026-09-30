import test from 'node:test';
import assert from 'node:assert/strict';
import {emptySampleRequestTemplate,validateSampleRequestTemplate,sampleRequestTemplateGroups} from '../packages/shared/src/sample-request-template.ts';
test('legacy fields reject values beyond persisted capacities',()=>{
 const limits={productName:200,customerName:200,specification:100,applicationScenario:500,flavorRequirement:500};
 for(const [key,max] of Object.entries(limits)){
  const form=emptySampleRequestTemplate();form[key]='字'.repeat(max+1);
  assert.match(validateSampleRequestTemplate(form)[key]??'',new RegExp(String(max)));
  form[key]='字'.repeat(max);assert.equal(validateSampleRequestTemplate(form)[key],undefined);
 }
});
test('blank defaults require all fourteen answers and never submit examples',()=>{
 const form=emptySampleRequestTemplate();assert.equal(form.referenceSampleProvided,null);
 assert.equal(Object.keys(validateSampleRequestTemplate(form)).length,14);
 assert.equal(Object.values(form).filter(x=>x!==''&&x!==null).length,0);
 assert.equal(sampleRequestTemplateGroups.flatMap(g=>g.fields).length,16);
});
test('false is a valid answer and units stay textual while blank and long inputs fail',()=>{
 const form=emptySampleRequestTemplate();
 for(const g of sampleRequestTemplateGroups)for(const f of g.fields)if(f.key!=='referenceSampleProvided'&&f.required)form[f.key]='填写';
 form.referenceSampleProvided=false;form.cutShape='20×30×40 mm';form.customerTargetPrice='18元/袋，未税';form.monthlyDemand='3吨/月';
 assert.deepEqual(validateSampleRequestTemplate(form),{});assert.equal(form.monthlyDemand,'3吨/月');
 form.cutShape='  ';assert.ok(validateSampleRequestTemplate(form).cutShape);
 form.cutShape='块';form.additionalNotes='字'.repeat(2001);assert.ok(validateSampleRequestTemplate(form).additionalNotes);
 form.additionalNotes='字'.repeat(2000);assert.deepEqual(validateSampleRequestTemplate(form),{});
});
