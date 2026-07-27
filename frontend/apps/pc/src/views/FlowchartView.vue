<template>
  <div>
    <a-page-header title="研发样品全流程图" sub-title="按飞书自建应用的角色、状态、通知和归档链路展示" />
    <a-card>
      <pre class="flow">{{ flowText }}</pre>
    </a-card>
  </div>
</template>

<script setup lang="ts">
const flowText = `研发内勤录入需求
  -> 研发总监审核
  -> 任务池
  -> 研发总监分发
  -> 研发人员接受任务
  -> 现场填写实验单（草稿）
  -> 通知内部测试
  -> 测试通过并锁版 / 复打样 / 停止
  -> 寄样反馈
  -> 生成核价文件
  -> 产品负责人 / 研发总监审核核价
  -> 审核通过后自动移交财务核价
  -> 文件归档`;
</script>

<style scoped>
.flow {
  font-size: 15px;
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
}
</style>
