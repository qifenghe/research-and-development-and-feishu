<template>
  <div>
    <a-page-header title="数据模型确认清单" sub-title="按业务关系逐个确认数据模型，再展开字段设计" />
    <a-card>
      <a-collapse>
        <a-collapse-panel v-for="group in groups" :key="group.title" :header="group.title">
          <p>{{ group.scope }}</p>
          <a-list :data-source="group.models" bordered>
            <template #renderItem="{ item }">
              <a-list-item>
                <a-list-item-meta :title="item[0]" :description="`${item[1]} · ${item[2]}`" />
              </a-list-item>
            </template>
          </a-list>
        </a-collapse-panel>
      </a-collapse>
    </a-card>
  </div>
</template>

<script setup lang="ts">
const groups = [
  {
    title: "1. 主业务模型",
    scope: "样品需求、项目主档和版本主线",
    models: [
      ["SampleRequest", "样品需求单", "研发内勤创建，研发总监审核，是流程起点"],
      ["SampleProject", "样品项目/样品主档", "承载版本、任务、实验、测试、寄样、核价和归档"],
      ["SampleVersion", "样品版本", "A0、A1、A2等，所有过程记录必须挂具体版本"],
    ],
  },
  {
    title: "2. 研发任务模型",
    scope: "任务池、分发、现场实验和实验单历史",
    models: [
      ["RndTask", "研发任务", "从任务池分发给研发人员，记录负责人、期限和接受时间"],
      ["ExperimentForm", "打样实验单", "支持草稿、提交、锁定和历史版本"],
    ],
  },
];
</script>
