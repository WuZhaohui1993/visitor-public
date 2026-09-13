<script setup lang="ts">
import { onMounted, ref } from "vue";
import dayjs from "dayjs";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createConfigDraft,
  fetchConfigVersions,
  fetchEffectiveConfig,
  runConfigAction,
  type IntegrationConfig,
  type IntegrationConfigVersion
} from "@/api/admin";
import { hasPerms } from "@/utils/auth";

defineOptions({ name: "IntegrationConfigs" });

const loading = ref(false);
const saving = ref(false);
const activeTab = ref("config");
const activeVersionNo = ref<number | null>(null);
const versions = ref<IntegrationConfigVersion[]>([]);
const config = ref<IntegrationConfig | null>(null);

async function load() {
  loading.value = true;
  try {
    const [effective, history] = await Promise.all([fetchEffectiveConfig(), fetchConfigVersions()]);
    config.value = effective.config;
    activeVersionNo.value = effective.activeVersionNo || null;
    versions.value = history;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "集成配置加载失败");
  } finally {
    loading.value = false;
  }
}

async function saveDraft() {
  if (!config.value) return;
  saving.value = true;
  try {
    const draft = await createConfigDraft(config.value);
    ElMessage.success(`已创建配置草稿 v${draft.versionNo}`);
    activeTab.value = "versions";
    await load();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "创建草稿失败");
  } finally {
    saving.value = false;
  }
}

async function action(version: IntegrationConfigVersion, type: "test" | "submit" | "publish" | "rollback-draft", label: string) {
  await ElMessageBox.confirm(`确认${label}配置版本 v${version.versionNo}？`, label, { type: type === "publish" ? "warning" : "info" });
  try {
    const result = await runConfigAction(version.id, type);
    if (type === "test" && result.testStatus === "FAILED") {
      ElMessage.error(result.testSummary || "连通测试失败");
    } else {
      ElMessage.success(`${label}完成`);
    }
    await load();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || `${label}失败`);
  }
}

function statusText(status: string) {
  return ({ DRAFT: "草稿", PENDING_REVIEW: "待复核", PUBLISHED: "当前生效", ARCHIVED: "历史版本" }[status] || status);
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="workspace-page">
    <header class="page-heading">
      <div><p class="section-kicker">INTEGRATION CONFIG</p><h1>集成配置</h1><p>配置先测试、再提交，由另一名管理员复核发布。</p></div>
      <span class="active-version">当前版本 {{ activeVersionNo ? `v${activeVersionNo}` : '服务器配置' }}</span>
    </header>

    <el-tabs v-model="activeTab" class="config-tabs">
      <el-tab-pane label="配置编辑" name="config">
        <el-form v-if="config" label-position="top" class="config-form">
          <section class="config-section">
            <div class="section-heading"><div><h2>钉钉</h2><p>访客搜索、免登认证和审批通知。</p></div><el-switch v-model="config.dingtalk.enabled" active-text="启用" /></div>
            <div class="form-grid">
              <el-form-item label="运行模式"><el-segmented v-model="config.dingtalk.mockMode" :options="[{ label: '正式', value: false }, { label: 'Mock', value: true }]" /></el-form-item>
              <el-form-item label="AgentId"><el-input-number v-model="config.dingtalk.agentId" :controls="false" /></el-form-item>
              <el-form-item label="AppKey"><el-input v-model="config.dingtalk.appKey" /></el-form-item>
              <el-form-item label="AppSecret"><el-input v-model="config.dingtalk.appSecret" type="password" show-password /></el-form-item>
              <el-form-item label="CorpId"><el-input v-model="config.dingtalk.corpId" /></el-form-item>
              <el-form-item label="根部门 ID"><el-input-number v-model="config.dingtalk.rootDeptId" :controls="false" /></el-form-item>
            </div>
          </section>

          <section class="config-section">
            <div class="section-heading"><div><h2>海康与门禁</h2><p>人员、人脸、门禁权限和资源发现参数。</p></div><el-switch v-model="config.hikvision.enabled" active-text="启用" /></div>
            <div class="form-grid">
              <el-form-item class="wide" label="网关地址"><el-input v-model="config.hikvision.baseUrl" /></el-form-item>
              <el-form-item label="AppKey"><el-input v-model="config.hikvision.appKey" /></el-form-item>
              <el-form-item label="AppSecret"><el-input v-model="config.hikvision.appSecret" type="password" show-password /></el-form-item>
              <el-form-item label="UserId"><el-input v-model="config.hikvision.userId" /></el-form-item>
              <el-form-item label="组织编码"><el-input v-model="config.hikvision.orgIndexCode" /></el-form-item>
              <el-form-item label="连接超时（秒）"><el-input-number v-model="config.hikvision.connectTimeoutSeconds" :min="1" :max="60" /></el-form-item>
              <el-form-item label="读取超时（秒）"><el-input-number v-model="config.hikvision.readTimeoutSeconds" :min="1" :max="120" /></el-form-item>
            </div>
            <el-divider content-position="left">门禁资源</el-divider>
            <div class="form-grid">
              <el-form-item label="门禁权限"><el-switch v-model="config.hikvision.access.enabled" active-text="启用" /></el-form-item>
              <el-form-item label="资源发现"><el-switch v-model="config.hikvision.access.autoDiscoverResources" active-text="自动发现" /></el-form-item>
              <el-form-item class="wide" label="资源查询接口"><el-input v-model="config.hikvision.access.resourceQueryPath" /></el-form-item>
              <el-form-item label="资源类型"><el-input v-model="config.hikvision.access.resourceType" /></el-form-item>
              <el-form-item label="查询类型"><el-input v-model="config.hikvision.access.resourceQueryType" /></el-form-item>
            </div>
          </section>

          <section class="config-section">
            <div class="section-heading"><div><h2>对象存储</h2><p>存储提供方类型已锁定，修改连接前会执行读写测试。</p></div><el-tag effect="plain">{{ config.storage.provider }}</el-tag></div>
            <div class="form-grid">
              <el-form-item label="存储桶"><el-input v-model="config.storage.bucket" /></el-form-item>
              <el-form-item label="预览地址"><el-input v-model="config.storage.previewBaseUrl" /></el-form-item>
              <el-form-item v-if="config.storage.provider === 'local'" class="wide" label="本地根目录"><el-input v-model="config.storage.localRoot" /></el-form-item>
              <template v-else>
                <el-form-item class="wide" label="MinIO 地址"><el-input v-model="config.storage.minio.endpoint" /></el-form-item>
                <el-form-item label="Access Key"><el-input v-model="config.storage.minio.accessKey" /></el-form-item>
                <el-form-item label="Secret Key"><el-input v-model="config.storage.minio.secretKey" type="password" show-password /></el-form-item>
              </template>
            </div>
          </section>

          <div class="form-actions"><el-button @click="load">放弃修改</el-button><el-button v-if="hasPerms('config:edit')" type="primary" :loading="saving" @click="saveDraft">保存为草稿</el-button></div>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="版本与发布" name="versions">
        <div class="version-list">
          <div v-for="version in versions" :key="version.id" class="version-row">
            <div><strong>v{{ version.versionNo }}</strong><small>{{ dayjs(version.createTime).format('YYYY-MM-DD HH:mm') }}</small></div>
            <el-tag :type="version.status === 'PUBLISHED' ? 'success' : version.status === 'PENDING_REVIEW' ? 'warning' : 'info'" effect="plain">{{ statusText(version.status) }}</el-tag>
            <div class="test-result"><span>{{ version.testStatus }}</span><small>{{ version.testSummary || '尚未执行连通测试' }}</small></div>
            <div class="version-actions">
              <el-button v-if="version.status === 'DRAFT' && hasPerms('config:test')" link type="primary" @click="action(version, 'test', '测试')">测试</el-button>
              <el-button v-if="version.status === 'DRAFT' && version.testStatus === 'PASSED' && hasPerms('config:submit')" link type="primary" @click="action(version, 'submit', '提交复核')">提交</el-button>
              <el-button v-if="version.status === 'PENDING_REVIEW' && hasPerms('config:publish')" link type="primary" @click="action(version, 'publish', '复核发布')">发布</el-button>
              <el-button v-if="['PUBLISHED','ARCHIVED'].includes(version.status) && hasPerms('config:submit')" link @click="action(version, 'rollback-draft', '提交回滚')">回滚到此版本</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<style scoped>
.workspace-page{min-height:100%;padding:28px 30px 40px;background:#f5f7f6;color:#16221f}.page-heading,.section-heading{display:flex;align-items:flex-start;justify-content:space-between}.page-heading{padding-bottom:16px}.page-heading h1{margin:6px 0 8px;font-size:30px;font-weight:650}.page-heading p,.section-heading p{margin:0;color:#6f7e79}.section-kicker{font-size:11px;font-weight:750;color:#4e8a77}.active-version{padding-top:18px;font-size:13px;color:#61706b}.config-tabs{background:white}.config-tabs :deep(.el-tabs__header){padding:0 24px;margin:0}.config-form{display:grid;gap:0}.config-section{padding:28px 24px;border-bottom:1px solid #e3e8e6}.section-heading{margin-bottom:24px}.section-heading h2{margin:0 0 6px;font-size:18px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 20px}.form-grid .wide{grid-column:1/-1}.form-actions{display:flex;justify-content:flex-end;gap:10px;padding:20px 24px}.version-list{border-top:3px solid #176b5b}.version-row{display:grid;grid-template-columns:100px 120px minmax(240px,1fr) minmax(180px,auto);gap:18px;align-items:center;padding:18px 24px;border-bottom:1px solid #e4e9e7}.version-row>div:first-child,.test-result{display:grid;gap:4px}.version-row small{color:#788580}.version-actions{display:flex;justify-content:flex-end;flex-wrap:wrap}.test-result small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.el-input-number{width:100%}@media(max-width:800px){.workspace-page{padding:20px 14px}.form-grid{grid-template-columns:1fr}.form-grid .wide{grid-column:auto}.version-row{grid-template-columns:80px 100px 1fr}.version-actions{grid-column:1/-1;justify-content:flex-start}}
</style>
