<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import dayjs from "dayjs";
import { ElMessage } from "element-plus";
import { fetchAuditLogs, type AuditLog } from "@/api/admin";

defineOptions({ name: "AuditLogs" });

const loading = ref(false);
const rows = ref<AuditLog[]>([]);
const filters = reactive({ keyword: "", result: "", dateRange: [] as string[] });
const page = reactive({ current: 0, size: 20, total: 0 });

async function load(current = page.current) {
  loading.value = true;
  try {
    const result = await fetchAuditLogs({
      keyword: filters.keyword || undefined,
      result: filters.result || undefined,
      from: filters.dateRange?.[0] ? `${filters.dateRange[0]}T00:00:00` : undefined,
      to: filters.dateRange?.[1] ? `${dayjs(filters.dateRange[1]).add(1, "day").format("YYYY-MM-DD")}T00:00:00` : undefined,
      page: current,
      size: page.size
    });
    rows.value = result.items;
    page.current = result.page;
    page.total = result.totalElements;
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "审计日志加载失败");
  } finally {
    loading.value = false;
  }
}

onMounted(() => load(0));
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading">
      <div><p class="section-kicker">AUDIT TRAIL</p><h1>审计日志</h1><p>跟踪登录、敏感资料访问、运维操作和配置变更。</p></div>
    </header>
    <form class="filter-band" @submit.prevent="load(0)">
      <el-input v-model="filters.keyword" clearable placeholder="账号、目标编号、操作摘要" />
      <el-select v-model="filters.result" clearable placeholder="执行结果"><el-option label="成功" value="SUCCESS" /><el-option label="失败" value="FAILED" /></el-select>
      <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
      <el-button type="primary" native-type="submit">查询</el-button>
    </form>
    <div class="table-surface">
      <el-table v-loading="loading" :data="rows" height="calc(100vh - 285px)">
        <el-table-column prop="createTime" label="时间" width="170"><template #default="{ row }">{{ dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss') }}</template></el-table-column>
        <el-table-column prop="actorUsername" label="操作账号" width="130" />
        <el-table-column prop="action" label="动作" min-width="190" />
        <el-table-column label="目标" min-width="190"><template #default="{ row }"><strong>{{ row.targetType || '--' }}</strong><small>{{ row.targetId || '--' }}</small></template></el-table-column>
        <el-table-column prop="summary" label="摘要" min-width="240" show-overflow-tooltip />
        <el-table-column prop="ipAddress" label="来源 IP" width="140" />
        <el-table-column label="结果" width="90"><template #default="{ row }"><el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">{{ row.result === 'SUCCESS' ? '成功' : '失败' }}</el-tag></template></el-table-column>
      </el-table>
      <el-pagination :current-page="page.current + 1" :page-size="page.size" :total="page.total" layout="total, prev, pager, next" @current-change="value => load(value - 1)" />
    </div>
  </section>
</template>

<style scoped>
.workspace-page { min-height: 100%; padding: 28px 30px 40px; background: #f5f7f6; color: #16221f; }
.page-heading { padding-bottom: 22px; }
.page-heading h1 { margin: 6px 0 8px; font-size: 30px; font-weight: 650; }
.page-heading p { margin: 0; color: #6f7e79; }
.section-kicker { font-size: 11px; font-weight: 750; color: #4e8a77; }
.filter-band { display: grid; grid-template-columns: minmax(220px, 1fr) 150px minmax(280px, 1fr) auto; gap: 12px; padding: 16px; background: white; border-top: 3px solid #176b5b; }
.table-surface { padding: 0 16px 16px; margin-top: 14px; background: white; }
.table-surface :deep(.cell) { display: grid; gap: 3px; }
.table-surface small { color: #788580; }
.el-pagination { justify-content: flex-end; padding-top: 16px; }
@media (max-width: 900px) { .filter-band { grid-template-columns: 1fr; } }
</style>
