<template>
 <div class="requirement-summary">
  <section v-for="group in sampleRequestTemplateGroups" :key="group.title">
   <h3>{{group.title}}</h3>
   <dl><div v-for="field in group.fields" :key="field.key"><dt>{{field.label}}</dt><dd>{{display(request[field.key])}}</dd></div></dl>
  </section>
 </div>
</template>
<script setup lang="ts">
import {sampleRequestTemplateGroups,type SampleRequest} from '@rnd/shared';
defineProps<{request:SampleRequest}>();
function display(value:unknown){return value===false?'否':value===true?'是':value==null||String(value).trim()===''?'未填写（历史需求）':String(value);}
</script>
<style scoped>
.requirement-summary section+section{margin-top:20px}.requirement-summary h3{font-size:15px;margin:0 0 12px}.requirement-summary dl{margin:0;display:grid;grid-template-columns:1fr 1fr;gap:16px 24px}.requirement-summary dt{color:#86909c;font-size:13px;margin-bottom:5px}.requirement-summary dd{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.6}@media(max-width:700px){.requirement-summary dl{grid-template-columns:1fr}}
</style>
