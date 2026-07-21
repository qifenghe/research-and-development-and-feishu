import type { ApiClient } from "./client";
import type {
  DictionaryConfig,
  FormFieldConfig,
  PermissionCapability,
  RoleDefinition,
  RolePermissionConfig,
  UserAccount,
  WorkflowConfig,
} from "../types";

export function createSettingsApi(client: ApiClient) {
  return {
    users: () => client.get<UserAccount[]>("/settings/users"),
    saveUser: (idOrUsername: string, payload: Record<string, unknown>) =>
      client.put<UserAccount>(`/settings/users/${idOrUsername}`, payload),
    enableUser: (id: string) => client.post<UserAccount>(`/settings/users/${id}/enable`),
    disableUser: (id: string) => client.post<UserAccount>(`/settings/users/${id}/disable`),
    roles: () => client.get<RoleDefinition[]>("/settings/roles"),
    createRole: (payload: Record<string, unknown>) => client.post<RoleDefinition>("/settings/roles", payload),
    updateRole: (roleCode: string, payload: Record<string, unknown>) =>
      client.put<RoleDefinition>(`/settings/roles/${roleCode}`, payload),
    enableRole: (roleCode: string) => client.post<RoleDefinition>(`/settings/roles/${roleCode}/enable`),
    disableRole: (roleCode: string) => client.post<RoleDefinition>(`/settings/roles/${roleCode}/disable`),
    workflows: () => client.get<WorkflowConfig[]>("/settings/workflows"),
    workflow: (workflowCode: string) =>
      client.get<WorkflowConfig>(`/settings/workflows/${workflowCode}`),
    saveWorkflow: (workflowCode: string, payload: Record<string, unknown>) =>
      client.put<WorkflowConfig>(`/settings/workflows/${workflowCode}`, payload),
    initWorkflowDefaults: () =>
      client.post<WorkflowConfig[]>("/settings/workflows/defaults/initialize"),
    dictionaries: () => client.get<DictionaryConfig[]>("/settings/dictionaries"),
    dictionary: (category: string) =>
      client.get<DictionaryConfig>(`/settings/dictionaries/${category}`),
    saveDictionary: (category: string, payload: Record<string, unknown>) =>
      client.put<DictionaryConfig>(`/settings/dictionaries/${category}`, payload),
    initDictionaryDefaults: () =>
      client.post<DictionaryConfig[]>("/settings/dictionaries/defaults/initialize"),
    formFields: () => client.get<FormFieldConfig[]>("/settings/form-fields"),
    formField: (formCode: string) =>
      client.get<FormFieldConfig>(`/settings/form-fields/${formCode}`),
    saveFormField: (formCode: string, payload: Record<string, unknown>) =>
      client.put<FormFieldConfig>(`/settings/form-fields/${formCode}`, payload),
    initFormFieldDefaults: () =>
      client.post<FormFieldConfig[]>("/settings/form-fields/defaults/initialize"),
    rolePermissions: (roleCode: string) =>
      client.get<RolePermissionConfig>(`/settings/role-permissions/${roleCode}`),
    permissionCatalog: () => client.get<PermissionCapability[]>("/settings/role-permissions/catalog"),
    saveRolePermissions: (roleCode: string, payload: Record<string, unknown>) =>
      client.put<RolePermissionConfig>(`/settings/role-permissions/${roleCode}`, payload),
    initRolePermissionDefaults: () =>
      client.post<RolePermissionConfig[]>("/settings/role-permissions/defaults/initialize"),
    templates: () => client.get<import("../types").TemplateConfigItem[]>("/settings/templates"),
    saveTemplate: (templateCode: string, payload: Record<string, unknown>) =>
      client.put<import("../types").TemplateConfigItem>(`/settings/templates/${templateCode}`, payload),
  };
}

export type SettingsApi = ReturnType<typeof createSettingsApi>;
