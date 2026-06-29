import { ref, type Ref } from "vue";

export interface AdminContextPanel {
  title: string;
  subtitle?: string;
  statusLabel?: string;
  statusTone?: "default" | "processing" | "success" | "warning" | "error";
  rows: Array<{ label: string; value: string }>;
}

const panel: Ref<AdminContextPanel | null> = ref(null);

export function useAdminContext() {
  function setPanel(next: AdminContextPanel | null) {
    panel.value = next;
  }

  function clearPanel() {
    panel.value = null;
  }

  return {
    panel,
    setPanel,
    clearPanel,
  };
}
