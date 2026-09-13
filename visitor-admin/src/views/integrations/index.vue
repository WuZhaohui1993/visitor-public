<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  fetchAccessTargets,
  fetchIntegrationStatus,
  refreshAccessTargets,
  type AccessTarget,
  type IntegrationStatus
} from "@/api/admin";
import { hasPerms } from "@/utils/auth";

defineOptions({ name: "Integrations" });

const loading = ref(true);
const refreshing = ref(false);
const targets = ref<AccessTarget[]>([]);
const status = ref<IntegrationStatus | null>(null);

const integrations = computed(() => {
  if (!status.value) return [];
  return [
    { name: "钉钉", description: "通讯录、免登和审批通知", ...status.value.dingtalk },
    { name: "海康身份", description: "人员、人脸与状态回收", ...status.value.hikvision },
    { name: "门禁权限", description: "通行权限下发和回收", ...status.value.accessControl },
    {
      name: "对象存储",
      description: `${status.value.storage.provider} · ${status.value.storage.bucket}`,
      enabled: true,
      configured: status.value.storage.configured,
      mode: status.value.storage.provider.toUpperCase(),
      endpoint: ""
    }
  ];
});

async function load() {
  loading.value = true;
  try {
    [status.value, targets.value] = await Promise.all([
      fetchIntegrationStatus(),
      fetchAccessTargets()
    ]);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "集成状态加载失败");
  } finally {
    loading.value = false;
  }
}

async function refreshTargets() {
  await ElMessageBox.confirm("将从海康重新拉取门禁资源并替换当前缓存，确认继续？", "刷新门禁资源", {
    type: "warning"
  });
  refreshing.value = true;
  try {
    await refreshAccessTargets();
    ElMessage.success("门禁资源缓存已刷新");
    await load();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "刷新失败");
  } finally {
    refreshing.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="workspace-page integration-page">
    <header class="page-heading">
      <div>
        <p class="section-kicker">INTEGRATIONS</p>
        <h1>运行状态</h1>
        <p>确认访客链路依赖的外部系统是否启用、配置完整。</p>
      </div>
      <el-button @click="load">刷新状态</el-button>
    </header>

    <section class="status-list">
      <div v-for="item in integrations" :key="item.name" class="status-row">
        <div class="status-indicator" :class="{ healthy: item.enabled && item.configured }" />
        <div class="status-main">
          <h2>{{ item.name }}</h2>
          <p>{{ item.description }}</p>
        </div>
        <div class="status-meta">
          <span>{{ item.mode }}</span>
          <small>{{ item.endpoint || "本地配置" }}</small>
        </div>
        <el-tag :type="item.enabled && item.configured ? 'success' : item.enabled ? 'warning' : 'info'" effect="plain">
          {{ !item.enabled ? "未启用" : item.configured ? "配置完整" : "配置不完整" }}
        </el-tag>
      </div>
    </section>

    <section class="targets-section">
      <div class="section-heading">
        <div>
          <h2>海康门禁资源</h2>
          <p>当前缓存 {{ status?.cachedAccessTargets || 0 }} 个资源通道。</p>
        </div>
        <el-button v-if="hasPerms('integration:operate')" :loading="refreshing" @click="refreshTargets">刷新资源</el-button>
      </div>
      <el-table :data="targets" empty-text="当前没有缓存的门禁资源">
        <el-table-column prop="sourceName" label="资源名称" min-width="180" />
        <el-table-column prop="sourceResourceType" label="资源类型" width="140" />
        <el-table-column prop="resourceIndexCode" label="授权资源编码" min-width="240" />
        <el-table-column prop="channelNo" label="通道" width="90" />
        <el-table-column prop="sourceIndexCode" label="来源编码" min-width="220" />
      </el-table>
    </section>
  </section>
</template>

<style scoped lang="scss">
.workspace-page { min-height: 100%; padding: 28px 30px 40px; background: #f5f7f6; color: #16221f; }
.page-heading, .section-heading { display: flex; align-items: flex-start; justify-content: space-between; }
.page-heading { padding-bottom: 22px; }
.page-heading h1 { margin: 6px 0 8px; font-size: 30px; font-weight: 650; }
.page-heading p, .section-heading p { margin: 0; color: #6f7e79; }
.section-kicker { margin: 0; font-size: 11px; font-weight: 750; color: #4e8a77; }
.status-list { background: white; border-top: 3px solid #176b5b; }
.status-row { display: grid; grid-template-columns: 12px minmax(200px, 1fr) minmax(180px, .7fr) 110px; gap: 18px; align-items: center; padding: 21px 24px; border-bottom: 1px solid #e4e9e7; }
.status-indicator { width: 10px; height: 10px; background: #a6b0ad; border-radius: 50%; }
.status-indicator.healthy { background: #198765; box-shadow: 0 0 0 4px #e5f3ee; }
.status-main h2 { margin: 0 0 5px; font-size: 16px; }
.status-main p, .status-meta small { margin: 0; color: #74817d; }
.status-meta { display: grid; gap: 4px; }
.status-meta span { font-size: 12px; font-weight: 700; color: #42534e; }
.targets-section { padding: 24px; margin-top: 20px; background: white; }
.section-heading { margin-bottom: 18px; }
.section-heading h2 { margin: 0 0 6px; font-size: 18px; }
@media (max-width: 780px) { .workspace-page { padding: 20px 14px; } .status-row { grid-template-columns: 12px 1fr auto; } .status-meta { display: none; } }
</style>
