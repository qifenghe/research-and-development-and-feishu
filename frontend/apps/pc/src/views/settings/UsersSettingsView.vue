<template>
  <div>
    <a-page-header title="账号与权限" sub-title="超级管理员统一创建账号，并按单一角色分配系统权限" />
    <a-card>
      <template #extra>
        <a-button type="primary" @click="openCreate">新建账号</a-button>
      </template>
      <a-table :columns="columns" :data-source="rows" row-key="id" :loading="loading">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'role'">
            <a-tag color="blue">{{ roleLabel(record.role) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'status'">
            <a-tag :color="record.status === 'ACTIVE' ? 'green' : 'red'">
              {{ record.status === "ACTIVE" ? "启用" : "停用" }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'lastLoginAt'">
            {{ record.lastLoginAt || "未登录" }}
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" @click="openReset(record)">重置密码</a-button>
              <a-button
                size="small"
                :disabled="isLastActiveSuperAdmin(record)"
                @click="toggle(record.id, record.status !== 'ACTIVE')"
              >
                {{ record.status === "ACTIVE" ? "禁用" : "启用" }}
              </a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="editor.open"
      :title="editor.mode === 'create' ? '新建账号' : '编辑账号'"
      :confirm-loading="saving"
      @ok="save"
    >
      <a-form layout="vertical">
        <a-form-item label="账号" required>
          <a-input v-model:value="editor.username" placeholder="例如 rnd_engineer_02" />
        </a-form-item>
        <a-form-item label="姓名" required>
          <a-input v-model:value="editor.name" placeholder="员工姓名" />
        </a-form-item>
        <a-form-item label="角色" required>
          <a-select v-model:value="editor.role">
            <a-select-option v-for="role in roles.filter((item) => item.status === 'ACTIVE')" :key="role.roleCode" :value="role.roleCode">
              {{ role.roleName }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="部门">
          <a-input v-model:value="editor.departmentName" placeholder="例如 研发部" />
        </a-form-item>
        <a-form-item :label="editor.mode === 'create' ? '初始密码' : '新密码（不填则不修改）'">
          <a-input-password v-model:value="editor.password" placeholder="默认 123456" />
        </a-form-item>
        <a-collapse ghost>
          <a-collapse-panel key="feishu" header="后续飞书账号绑定（可选）">
            <a-form-item label="飞书 ID">
              <a-input v-model:value="editor.feishuUserId" placeholder="例如 ou_xxx" />
            </a-form-item>
          </a-collapse-panel>
        </a-collapse>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import { roleLabel, type RoleDefinition, type UserAccount } from "@rnd/shared";
import { api } from "../../services/api";

const loading = ref(false);
const saving = ref(false);
const rows = ref<UserAccount[]>([]);

const columns = [
  { title: "账号", dataIndex: "username", key: "username" },
  { title: "姓名", dataIndex: "name", key: "name" },
  { title: "角色", dataIndex: "role", key: "role" },
  { title: "部门", dataIndex: "departmentName", key: "departmentName" },
  { title: "最近登录", dataIndex: "lastLoginAt", key: "lastLoginAt" },
  { title: "状态", dataIndex: "status", key: "status" },
  { title: "操作", key: "action", width: 260 },
];

const roles = ref<RoleDefinition[]>([]);

const editor = reactive({
  open: false,
  mode: "create" as "create" | "edit",
  id: "",
  username: "",
  name: "",
  feishuUserId: "",
  role: "RND_ENGINEER",
  departmentName: "研发部",
  password: "",
});

async function load() {
  loading.value = true;
  try {
    const [users, roleDefinitions] = await Promise.all([api.settings.users(), api.settings.roles()]);
    rows.value = users;
    roles.value = roleDefinitions;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(editor, {
    open: true,
    mode: "create",
    id: `new-${Date.now()}`,
    username: "",
    name: "",
    feishuUserId: "",
    role: "RND_ENGINEER",
    departmentName: "研发部",
    password: "123456",
  });
}

function openEdit(record: UserAccount) {
  Object.assign(editor, {
    open: true,
    mode: "edit",
    id: record.id,
    username: record.username ?? "",
    name: record.name,
    feishuUserId: record.feishuUserId ?? "",
    role: record.role,
    departmentName: record.departmentName,
    password: "",
  });
}

function openReset(record: UserAccount) {
  openEdit(record);
  editor.password = "123456";
}

function isLastActiveSuperAdmin(record: UserAccount) {
  return record.role === "SYSTEM_ADMIN"
    && record.status === "ACTIVE"
    && rows.value.filter((user) => user.role === "SYSTEM_ADMIN" && user.status === "ACTIVE").length <= 1;
}

async function save() {
  if (!editor.username.trim() || !editor.name.trim() || !editor.role) {
    message.warning("请填写账号、姓名和角色");
    return;
  }
  saving.value = true;
  try {
    await api.settings.saveUser(editor.id || editor.username, {
      username: editor.username.trim(),
      name: editor.name.trim(),
      feishuUserId: editor.feishuUserId.trim() || null,
      role: editor.role,
      departmentName: editor.departmentName.trim(),
      password: editor.password || undefined,
    });
    message.success(editor.mode === "create" ? "账号已创建" : "账号已更新");
    editor.open = false;
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "保存失败");
  } finally {
    saving.value = false;
  }
}

async function toggle(id: string, enable: boolean) {
  try {
    if (enable) await api.settings.enableUser(id);
    else await api.settings.disableUser(id);
    message.success("已更新用户状态");
    await load();
  } catch (error) {
    message.error(error instanceof Error ? error.message : "更新失败");
  }
}

onMounted(load);
</script>
