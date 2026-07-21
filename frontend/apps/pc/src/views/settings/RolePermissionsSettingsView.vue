<template>
  <div>
    <a-page-header title="角色权限" sub-title="一个角色可拥有多个业务权限；账号只分配一个角色" />
    <a-alert
      type="info"
      show-icon
      style="margin-bottom: 16px"
      message="权限以业务动作配置。研发人员审核核价时，仍会额外校验其是否为该产品负责人；财务仅可见已审核并移交的核价文件。"
    />

    <a-row :gutter="16">
      <a-col :span="8">
        <a-card title="角色列表" :loading="loadingRoles">
          <template #extra><a-button type="primary" size="small" @click="openRoleEditor()">新建角色</a-button></template>
          <a-list :data-source="roles" :split="true">
            <template #renderItem="{ item }">
              <a-list-item class="role-item" :class="{ active: selectedRole === item.roleCode }" @click="selectRole(item.roleCode)">
                <a-list-item-meta :description="item.description || '未填写角色说明'">
                  <template #title>
                    <a-space>
                      <span>{{ item.roleName }}</span>
                      <a-tag v-if="item.systemBuiltin" color="blue">系统</a-tag>
                      <a-tag :color="item.status === 'ACTIVE' ? 'green' : 'default'">{{ item.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag>
                    </a-space>
                  </template>
                </a-list-item-meta>
                <template #actions>
                  <a-button type="link" size="small" @click.stop="openRoleEditor(item)">编辑</a-button>
                  <a-button v-if="!item.systemBuiltin" type="link" size="small" @click.stop="toggleRole(item)">
                    {{ item.status === 'ACTIVE' ? '停用' : '启用' }}
                  </a-button>
                </template>
              </a-list-item>
            </template>
          </a-list>
        </a-card>
      </a-col>

      <a-col :span="16">
        <a-card :title="selectedRoleName ? `${selectedRoleName} · 权限清单` : '请选择角色'" :loading="loadingPermissions">
          <template #extra>
            <a-space v-if="selectedRole">
              <a-button @click="setAll(true)">全选</a-button>
              <a-button @click="setAll(false)">清空</a-button>
              <a-button type="primary" :loading="saving" @click="savePermissions">保存权限</a-button>
            </a-space>
          </template>
          <a-empty v-if="!selectedRole" description="从左侧选择一个角色后配置权限" />
          <a-collapse v-else v-model:activeKey="activeModules">
            <a-collapse-panel v-for="group in groupedCapabilities" :key="group.moduleCode" :header="group.moduleName">
              <a-table :columns="permissionColumns" :data-source="group.items" row-key="actionCode" size="small" :pagination="false">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'enabled'">
                    <a-switch v-model:checked="enabledActions[record.actionCode]" checked-children="允许" un-checked-children="拒绝" />
                  </template>
                  <template v-else-if="column.key === 'scope'">
                    <span class="scope-text">{{ scopeText(record) }}</span>
                  </template>
                </template>
              </a-table>
            </a-collapse-panel>
          </a-collapse>
        </a-card>
      </a-col>
    </a-row>

    <a-modal v-model:open="editor.open" :title="editor.editing ? '编辑角色' : '新建角色'" :confirm-loading="savingRole" @ok="saveRole">
      <a-form layout="vertical">
        <a-form-item label="角色编码" required extra="用于系统识别，创建后不可更改，例如 RND_VIEWER。">
          <a-input v-model:value="editor.roleCode" :disabled="editor.editing" placeholder="例如 RND_VIEWER" />
        </a-form-item>
        <a-form-item label="角色名称" required><a-input v-model:value="editor.roleName" placeholder="例如 研发查看员" /></a-form-item>
        <a-form-item label="角色说明"><a-textarea v-model:value="editor.description" :rows="3" placeholder="该角色可以负责什么工作" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { message } from "ant-design-vue";
import type { PermissionCapability, RoleDefinition } from "@rnd/shared";
import { api } from "../../services/api";

const roles = ref<RoleDefinition[]>([]);
const catalog = ref<PermissionCapability[]>([]);
const selectedRole = ref("");
const enabledActions = reactive<Record<string, boolean>>({});
const loadingRoles = ref(false);
const loadingPermissions = ref(false);
const saving = ref(false);
const savingRole = ref(false);
const activeModules = ref<string[]>([]);
const editor = reactive({ open: false, editing: false, roleCode: "", roleName: "", description: "" });

const permissionColumns = [
  { title: "业务动作", dataIndex: "actionName", key: "actionName", width: 250 },
  { title: "访问范围", key: "scope" },
  { title: "允许", key: "enabled", width: 100 },
];

const selectedRoleName = computed(() => roles.value.find((role) => role.roleCode === selectedRole.value)?.roleName ?? "");
const groupedCapabilities = computed(() => {
  const map = new Map<string, { moduleCode: string; moduleName: string; items: PermissionCapability[] }>();
  catalog.value.forEach((item) => {
    const group = map.get(item.moduleCode) ?? { moduleCode: item.moduleCode, moduleName: item.moduleName, items: [] };
    group.items.push(item);
    map.set(item.moduleCode, group);
  });
  return [...map.values()];
});

function scopeText(item: PermissionCapability) {
  if (item.pathPattern.includes("pricing-files") && item.actionName.includes("审核")) return "研发人员仅限本人负责产品；总监可审核全部";
  if (item.pathPattern.includes("pricing-files") && item.actionName.includes("查看")) return "财务仅限已审核并移交的文件";
  return `${item.httpMethod} ${item.pathPattern}`;
}

async function loadRoles() {
  loadingRoles.value = true;
  try {
    roles.value = await api.settings.roles();
    if (!selectedRole.value && roles.value.length) await selectRole(roles.value[0].roleCode);
  } finally { loadingRoles.value = false; }
}

async function selectRole(roleCode: string) {
  selectedRole.value = roleCode;
  loadingPermissions.value = true;
  try {
    const [config, available] = await Promise.all([api.settings.rolePermissions(roleCode), api.settings.permissionCatalog()]);
    catalog.value = available;
    Object.keys(enabledActions).forEach((key) => delete enabledActions[key]);
    const current = new Set(config.permissions.filter((rule) => rule.enabled).map((rule) => `${rule.httpMethod} ${rule.pathPattern}`));
    available.forEach((item) => { enabledActions[item.actionCode] = current.has(`${item.httpMethod} ${item.pathPattern}`); });
    activeModules.value = groupedCapabilities.value.map((group) => group.moduleCode);
  } catch (error) { message.error(error instanceof Error ? error.message : "加载权限失败"); }
  finally { loadingPermissions.value = false; }
}

function setAll(value: boolean) { catalog.value.forEach((item) => { enabledActions[item.actionCode] = value; }); }

async function savePermissions() {
  if (!selectedRole.value) return;
  saving.value = true;
  try {
    await api.settings.saveRolePermissions(selectedRole.value, {
      permissions: catalog.value.map((item) => ({
        httpMethod: item.httpMethod,
        pathPattern: item.pathPattern,
        enabled: Boolean(enabledActions[item.actionCode]),
        description: item.actionName,
        sortOrder: item.sortOrder,
      })),
    });
    message.success("角色权限已保存");
  } catch (error) { message.error(error instanceof Error ? error.message : "保存权限失败"); }
  finally { saving.value = false; }
}

function openRoleEditor(role?: RoleDefinition) {
  Object.assign(editor, role
    ? { open: true, editing: true, roleCode: role.roleCode, roleName: role.roleName, description: role.description ?? "" }
    : { open: true, editing: false, roleCode: "", roleName: "", description: "" });
}

async function saveRole() {
  if (!editor.roleCode.trim() || !editor.roleName.trim()) { message.warning("请填写角色编码和角色名称"); return; }
  savingRole.value = true;
  try {
    if (editor.editing) await api.settings.updateRole(editor.roleCode, { roleName: editor.roleName, description: editor.description });
    else await api.settings.createRole({ roleCode: editor.roleCode, roleName: editor.roleName, description: editor.description });
    message.success(editor.editing ? "角色已更新" : "角色已创建");
    editor.open = false;
    await loadRoles();
    await selectRole(editor.roleCode.trim().toUpperCase());
  } catch (error) { message.error(error instanceof Error ? error.message : "保存角色失败"); }
  finally { savingRole.value = false; }
}

async function toggleRole(role: RoleDefinition) {
  try {
    if (role.status === "ACTIVE") await api.settings.disableRole(role.roleCode);
    else await api.settings.enableRole(role.roleCode);
    message.success("角色状态已更新");
    await loadRoles();
  } catch (error) { message.error(error instanceof Error ? error.message : "更新角色失败"); }
}

onMounted(loadRoles);
</script>

<style scoped>
.role-item { cursor: pointer; border-radius: 6px; padding: 10px 8px; }
.role-item.active { background: #eaf3ff; }
.scope-text { color: #7b8794; font-size: 12px; }
</style>
