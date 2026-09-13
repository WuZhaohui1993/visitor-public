<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { fetchDashboard, type DashboardSummary } from "@/api/admin";

defineOptions({ name: "Dashboard" });

const router = useRouter();
const loading = ref(true);
const summary = ref<DashboardSummary>({
  todayTotal: 0,
  pending: 0,
  approvedToday: 0,
  rejectedToday: 0,
  activeVisitors: 0,
  integrationFailures: 0,
  trend: []
});

const metrics = computed(() => [
  { label: "今日申请", value: summary.value.todayTotal, tone: "default" },
  { label: "等待审批", value: summary.value.pending, tone: "warning" },
  { label: "今日通过", value: summary.value.approvedToday, tone: "success" },
  { label: "当前有效", value: summary.value.activeVisitors, tone: "success" },
  { label: "集成异常", value: summary.value.integrationFailures, tone: "danger" }
]);

const maxTrend = computed(() => Math.max(1, ...summary.value.trend.map(item => item.total)));

async function load() {
  loading.value = true;
  try {
    summary.value = await fetchDashboard();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "工作台数据加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section v-loading="loading" class="workspace-page dashboard-page">
    <header class="page-heading">
      <div>
        <p class="section-kicker">OVERVIEW</p>
        <h1>工作台</h1>
        <p>聚焦今日访客状态和需要人工处理的集成异常。</p>
      </div>
      <el-button @click="load">刷新数据</el-button>
    </header>

    <div class="metric-band">
      <div v-for="metric in metrics" :key="metric.label" class="metric-item">
        <span>{{ metric.label }}</span>
        <strong :class="`tone-${metric.tone}`">{{ metric.value }}</strong>
      </div>
    </div>

    <div class="dashboard-grid">
      <section class="trend-section">
        <div class="section-heading">
          <div>
            <h2>近 7 日申请</h2>
            <p>按登记日期统计申请、通过和拒绝数量。</p>
          </div>
        </div>
        <div class="trend-chart">
          <div v-for="item in summary.trend" :key="item.date" class="trend-column">
            <div class="bar-track">
              <div class="bar" :style="{ height: `${Math.max(6, item.total / maxTrend * 100)}%` }">
                <span>{{ item.total }}</span>
              </div>
            </div>
            <small>{{ item.date.slice(5) }}</small>
          </div>
        </div>
      </section>

      <aside class="attention-panel">
        <p class="section-kicker">ATTENTION</p>
        <h2>待处理事项</h2>
        <button class="attention-row" type="button" @click="router.push('/records?status=0')">
          <span>等待审批的申请</span>
          <strong>{{ summary.pending }}</strong>
        </button>
        <button class="attention-row danger" type="button" @click="router.push('/records?hikState=FAILED')">
          <span>海康或门禁异常</span>
          <strong>{{ summary.integrationFailures }}</strong>
        </button>
        <p class="attention-note">审批仍由原钉钉责任人完成，后台只提供查看与运维处理。</p>
      </aside>
    </div>
  </section>
</template>

<style scoped lang="scss">
.workspace-page { min-height: 100%; padding: 28px 30px 40px; background: #f5f7f6; color: #16221f; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 24px; }
.page-heading h1 { margin: 6px 0 8px; font-size: 30px; font-weight: 650; }
.page-heading p, .section-heading p { margin: 0; color: #6f7e79; }
.section-kicker { margin: 0; font-size: 11px; font-weight: 750; color: #4e8a77; }
.metric-band { display: grid; grid-template-columns: repeat(5, 1fr); background: white; border-top: 3px solid #176b5b; }
.metric-item { display: grid; gap: 10px; padding: 24px; border-right: 1px solid #e4e9e7; }
.metric-item:last-child { border-right: 0; }
.metric-item span { font-size: 13px; color: #6f7e79; }
.metric-item strong { font-size: 32px; font-weight: 650; }
.tone-success { color: #176b5b; }
.tone-warning { color: #9a6518; }
.tone-danger { color: #b23a3a; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1fr) 330px; gap: 20px; margin-top: 20px; }
.trend-section, .attention-panel { background: white; }
.trend-section { padding: 24px; }
.section-heading h2, .attention-panel h2 { margin: 0 0 6px; font-size: 18px; }
.trend-chart { display: grid; grid-template-columns: repeat(7, 1fr); gap: 16px; height: 260px; margin-top: 24px; padding-top: 12px; border-top: 1px solid #e4e9e7; }
.trend-column { display: grid; grid-template-rows: 1fr auto; gap: 10px; text-align: center; }
.bar-track { display: flex; align-items: flex-end; justify-content: center; min-height: 200px; background: #f7f9f8; border-bottom: 1px solid #dfe6e3; }
.bar { position: relative; width: min(42px, 70%); min-height: 6px; background: #176b5b; transition: height 180ms ease; }
.bar span { position: absolute; top: -22px; left: 50%; font-size: 12px; font-weight: 650; transform: translateX(-50%); }
.trend-column small { color: #73817c; }
.attention-panel { padding: 24px; border-top: 3px solid #1e2f2a; }
.attention-row { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 18px 0; color: #25332f; text-align: left; background: transparent; border: 0; border-bottom: 1px solid #e4e9e7; cursor: pointer; }
.attention-row strong { font-size: 24px; color: #176b5b; }
.attention-row.danger strong { color: #b23a3a; }
.attention-note { margin: 22px 0 0; font-size: 13px; line-height: 1.7; color: #74827d; }
@media (max-width: 1100px) { .metric-band { grid-template-columns: repeat(3, 1fr); } .metric-item:nth-child(3) { border-right: 0; } .dashboard-grid { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .workspace-page { padding: 20px 16px; } .metric-band { grid-template-columns: repeat(2, 1fr); } .metric-item:nth-child(3) { border-right: 1px solid #e4e9e7; } .metric-item:nth-child(even) { border-right: 0; } }
</style>
