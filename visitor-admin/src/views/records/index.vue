<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import dayjs from "dayjs";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  fetchRecord,
  fetchRecordAttachment,
  fetchRecords,
  exportRecords,
  runRecordOperation,
  type VisitorRecord
} from "@/api/admin";
import { hasPerms } from "@/utils/auth";

defineOptions({ name: "VisitorRecords" });

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const detailLoading = ref(false);
const operating = ref("");
const exporting = ref(false);
const rows = ref<VisitorRecord[]>([]);
const detail = ref<VisitorRecord | null>(null);
const drawerVisible = ref(false);
const pagination = reactive({ page: 0, size: 20, total: 0 });
const filters = reactive({
  keyword: "",
  status: route.query.status ? Number(route.query.status) : undefined,
  hikState: String(route.query.hikState || ""),
  dateRange: [] as string[]
});

function formatDate(value?: string) {
  return value ? dayjs(value).format("YYYY-MM-DD HH:mm") : "--";
}

function statusType(status?: string) {
  if (status === "SUCCESS") return "success";
  if (status === "FAILED") return "danger";
  if (status === "DISABLED") return "info";
  return "warning";
}

function statusText(status?: string) {
  return ({ SUCCESS: "成功", FAILED: "失败", DISABLED: "已回收", PENDING: "处理中" }[status] || "未开始");
}

async function load(page = pagination.page) {
  loading.value = true;
  try {
    const result = await fetchRecords({
      keyword: filters.keyword || undefined,
      status: filters.status,
      hikState: filters.hikState || undefined,
      from: filters.dateRange?.[0] ? `${filters.dateRange[0]}T00:00:00` : undefined,
      to: filters.dateRange?.[1] ? `${dayjs(filters.dateRange[1]).add(1, "day").format("YYYY-MM-DD")}T00:00:00` : undefined,
      page,
      size: pagination.size
    });
    rows.value = result.items;
    pagination.page = result.page;
    pagination.total = result.totalElements;
    void router.replace({ query: { status: filters.status, hikState: filters.hikState || undefined } });
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "访客台账加载失败");
  } finally {
    loading.value = false;
  }
}

function reset() {
  filters.keyword = "";
  filters.status = undefined;
  filters.hikState = "";
  filters.dateRange = [];
  void load(0);
}

function currentFilters() {
  return {
    keyword: filters.keyword || undefined,
    status: filters.status,
    hikState: filters.hikState || undefined,
    from: filters.dateRange?.[0]
      ? `${filters.dateRange[0]}T00:00:00`
      : undefined,
    to: filters.dateRange?.[1]
      ? `${dayjs(filters.dateRange[1]).add(1, "day").format("YYYY-MM-DD")}T00:00:00`
      : undefined
  };
}

async function exportLedger() {
  await ElMessageBox.confirm(
    "导出文件包含访客手机号和身份证号明文，请按敏感数据要求保管。确认继续？",
    "导出访客台账",
    { type: "warning", confirmButtonText: "确认导出" }
  );
  exporting.value = true;
  try {
    const data = await exportRecords(currentFilters());
    const XLSX = await import("xlsx");
    const sheet = XLSX.utils.json_to_sheet(
      data.map(row => ({
        登记编号: row.recordNo,
        访客姓名: row.visitorName,
        手机号: row.phone,
        身份证号: row.idCardNo,
        被访人: row.visitedUserName || "",
        被访部门: row.visitedDeptName || "",
        来访事由: row.visitReason || "",
        计划入场: formatDate(row.plannedEntryTime),
        计划离场: formatDate(row.plannedExitTime),
        审批状态: row.statusText,
        海康身份: statusText(row.hikSyncStatus),
        门禁权限: statusText(row.hikAccessStatus),
        登记时间: formatDate(row.createTime)
      }))
    );
    sheet["!cols"] = [
      { wch: 22 },
      { wch: 12 },
      { wch: 15 },
      { wch: 22 },
      { wch: 12 },
      { wch: 18 },
      { wch: 24 },
      { wch: 18 },
      { wch: 18 },
      { wch: 12 },
      { wch: 12 },
      { wch: 12 },
      { wch: 18 }
    ];
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, sheet, "访客台账");
    XLSX.writeFile(
      workbook,
      `访客台账_${dayjs().format("YYYYMMDD_HHmmss")}.xlsx`
    );
    ElMessage.success(`已导出 ${data.length} 条记录`);
  } catch (error: any) {
    if (error !== "cancel" && error !== "close") {
      ElMessage.error(error?.response?.data?.message || "访客台账导出失败");
    }
  } finally {
    exporting.value = false;
  }
}

async function openDetail(row: VisitorRecord) {
  drawerVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = await fetchRecord(row.bizId);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "访客详情加载失败");
  } finally {
    detailLoading.value = false;
  }
}

async function operate(operation: "hikvision-retry" | "access-retry" | "revoke", label: string) {
  if (!detail.value) return;
  await ElMessageBox.confirm(
    operation === "revoke"
      ? `确认回收 ${detail.value.recordNo} 的海康身份和门禁权限？`
      : `确认对 ${detail.value.recordNo} 执行“${label}”？`,
    label,
    { type: operation === "revoke" ? "warning" : "info", confirmButtonText: "确认执行" }
  );
  operating.value = operation;
  try {
    detail.value = await runRecordOperation(detail.value.bizId, operation);
    ElMessage.success(`${label}已提交`);
    await load();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || `${label}失败`);
  } finally {
    operating.value = "";
  }
}

async function openAttachment(type: string) {
  if (!detail.value) return;
  try {
    const blob = await fetchRecordAttachment(detail.value.bizId, type);
    const url = URL.createObjectURL(blob);
    window.open(url, "_blank", "noopener,noreferrer");
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "附件加载失败");
  }
}

onMounted(() => load(0));
</script>

<template>
  <section class="workspace-page records-page">
    <header class="page-heading">
      <div>
        <p class="section-kicker">VISITOR LEDGER</p>
        <h1>访客台账</h1>
        <p>查看登记、审批、海康身份和门禁权限的完整状态。</p>
      </div>
      <div class="heading-actions">
        <span class="record-count">共 {{ pagination.total }} 条</span>
        <el-button v-if="hasPerms('record:export')" :loading="exporting" @click="exportLedger">导出 XLSX</el-button>
      </div>
    </header>

    <form class="filter-band" @submit.prevent="load(0)">
      <el-input v-model="filters.keyword" clearable placeholder="姓名、手机号、登记编号、被访人" />
      <el-select v-model="filters.status" clearable placeholder="审批状态">
        <el-option label="等待审批" :value="0" />
        <el-option label="审批通过" :value="1" />
        <el-option label="审批拒绝" :value="2" />
      </el-select>
      <el-select v-model="filters.hikState" clearable placeholder="集成状态">
        <el-option label="处理中" value="PENDING" />
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILED" />
        <el-option label="已回收" value="DISABLED" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
      <div class="filter-actions">
        <el-button type="primary" native-type="submit">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </form>

    <div class="table-surface">
      <el-table v-loading="loading" :data="rows" height="calc(100vh - 310px)" @row-dblclick="openDetail">
        <el-table-column label="登记编号" min-width="180">
          <template #default="{ row }">
            <button type="button" class="record-link" @click="openDetail(row)">{{ row.recordNo }}</button>
            <small>{{ formatDate(row.createTime) }}</small>
          </template>
        </el-table-column>
        <el-table-column label="访客" min-width="150">
          <template #default="{ row }">
            <strong>{{ row.visitorName }}</strong>
            <small>{{ row.phone }}</small>
          </template>
        </el-table-column>
        <el-table-column label="被访人" min-width="160">
          <template #default="{ row }">
            <span>{{ row.visitedUserName || "--" }}</span>
            <small>{{ row.visitedDeptName || "--" }}</small>
          </template>
        </el-table-column>
        <el-table-column label="计划来访" min-width="180">
          <template #default="{ row }">
            <span>{{ formatDate(row.plannedEntryTime) }}</span>
            <small>至 {{ formatDate(row.plannedExitTime) }}</small>
          </template>
        </el-table-column>
        <el-table-column label="审批" min-width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'" effect="plain">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="海康身份" min-width="110">
          <template #default="{ row }"><el-tag :type="statusType(row.hikSyncStatus)" effect="plain">{{ statusText(row.hikSyncStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="门禁权限" min-width="110">
          <template #default="{ row }"><el-tag :type="statusType(row.hikAccessStatus)" effect="plain">{{ statusText(row.hikAccessStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="90">
          <template #default="{ row }"><el-button link type="primary" @click="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
      <el-pagination
        :current-page="pagination.page + 1"
        :page-size="pagination.size"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="value => load(value - 1)"
      />
    </div>

    <el-drawer v-model="drawerVisible" size="min(720px, 92vw)" destroy-on-close>
      <template #header>
        <div v-if="detail" class="drawer-heading">
          <div><small>登记编号</small><h2>{{ detail.recordNo }}</h2></div>
          <el-tag :type="detail.status === 1 ? 'success' : detail.status === 2 ? 'danger' : 'warning'" effect="plain">{{ detail.statusText }}</el-tag>
        </div>
      </template>
      <div v-loading="detailLoading" class="record-detail" v-if="detail">
        <section>
          <h3>访客信息</h3>
          <dl class="detail-grid">
            <div><dt>姓名</dt><dd>{{ detail.visitorName }}</dd></div>
            <div><dt>手机号</dt><dd>{{ detail.phone }}</dd></div>
            <div class="wide"><dt>身份证号</dt><dd>{{ detail.idCardNo }}</dd></div>
            <div><dt>被访人</dt><dd>{{ detail.visitedUserName || "--" }}</dd></div>
            <div><dt>部门</dt><dd>{{ detail.visitedDeptName || "--" }}</dd></div>
            <div class="wide"><dt>来访事由</dt><dd>{{ detail.visitReason || "--" }}</dd></div>
            <div class="wide"><dt>计划时间</dt><dd>{{ formatDate(detail.plannedEntryTime) }} 至 {{ formatDate(detail.plannedExitTime) }}</dd></div>
          </dl>
        </section>

        <section>
          <h3>身份附件</h3>
          <div class="attachment-links">
            <button v-if="detail.hasIdCardFront" type="button" @click="openAttachment('id-card-front')">身份证正面</button>
            <button v-if="detail.hasIdCardBack" type="button" @click="openAttachment('id-card-back')">身份证反面</button>
            <button v-if="detail.hasFacePhoto" type="button" @click="openAttachment('face-photo')">人脸照片</button>
          </div>
        </section>

        <section>
          <h3>集成状态</h3>
          <div class="sync-row">
            <div><span>海康身份</span><el-tag :type="statusType(detail.hikSyncStatus)" effect="plain">{{ statusText(detail.hikSyncStatus) }}</el-tag></div>
            <p v-if="detail.hikSyncError">{{ detail.hikSyncError }}</p>
          </div>
          <div class="sync-row">
            <div><span>门禁权限</span><el-tag :type="statusType(detail.hikAccessStatus)" effect="plain">{{ statusText(detail.hikAccessStatus) }}</el-tag></div>
            <p v-if="detail.hikAccessError">{{ detail.hikAccessError }}</p>
          </div>
        </section>

        <section v-if="hasPerms('record:operate')" class="operation-section">
          <h3>运维操作</h3>
          <div class="operation-actions">
            <el-button :loading="operating === 'hikvision-retry'" @click="operate('hikvision-retry', '重试海康身份')">重试海康身份</el-button>
            <el-button :loading="operating === 'access-retry'" @click="operate('access-retry', '重试门禁权限')">重试门禁权限</el-button>
            <el-button type="danger" plain :loading="operating === 'revoke'" @click="operate('revoke', '回收通行权限')">回收通行权限</el-button>
          </div>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<style scoped lang="scss">
.workspace-page { min-height: 100%; padding: 28px 30px 40px; background: #f5f7f6; color: #16221f; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; padding-bottom: 22px; }
.page-heading h1 { margin: 6px 0 8px; font-size: 30px; font-weight: 650; }
.page-heading p { margin: 0; color: #6f7e79; }
.section-kicker { margin: 0; font-size: 11px; font-weight: 750; color: #4e8a77; }
.record-count { padding-top: 20px; font-size: 13px; color: #63736e; }
.heading-actions { display: flex; gap: 14px; align-items: center; padding-top: 12px; }
.heading-actions .record-count { padding-top: 0; }
.filter-band { display: grid; grid-template-columns: minmax(220px, 1.4fr) 160px 160px minmax(280px, 1fr) auto; gap: 12px; align-items: center; padding: 16px; background: white; border-top: 3px solid #176b5b; }
.filter-actions { display: flex; }
.table-surface { padding: 0 16px 16px; margin-top: 14px; background: white; }
.table-surface :deep(.el-table) { --el-table-header-bg-color: #f0f4f2; --el-table-row-hover-bg-color: #f3f8f6; }
.table-surface :deep(.cell) { display: grid; gap: 4px; }
.table-surface strong { font-weight: 650; }
.table-surface small { color: #7a8783; }
.record-link { padding: 0; font-weight: 650; color: #176b5b; text-align: left; background: none; border: 0; cursor: pointer; }
.el-pagination { justify-content: flex-end; padding-top: 16px; }
.drawer-heading { display: flex; width: 100%; align-items: center; justify-content: space-between; padding-right: 16px; }
.drawer-heading small { color: #7a8783; }
.drawer-heading h2 { margin: 4px 0 0; font-size: 20px; }
.record-detail { display: grid; gap: 26px; padding: 0 4px 30px; }
.record-detail section { padding-top: 4px; border-top: 1px solid #e1e7e4; }
.record-detail h3 { margin: 18px 0; font-size: 15px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 18px 26px; margin: 0; }
.detail-grid .wide { grid-column: 1 / -1; }
.detail-grid dt { margin-bottom: 5px; font-size: 12px; color: #7b8884; }
.detail-grid dd { margin: 0; line-height: 1.6; }
.attachment-links { display: flex; gap: 10px; flex-wrap: wrap; }
.attachment-links button { padding: 9px 12px; color: #176b5b; background: #edf6f2; border: 0; border-radius: 6px; cursor: pointer; }
.sync-row { padding: 14px 0; border-bottom: 1px solid #edf0ef; }
.sync-row > div { display: flex; align-items: center; justify-content: space-between; }
.sync-row p { margin: 10px 0 0; font-size: 13px; line-height: 1.6; color: #a23737; }
.operation-actions { display: flex; flex-wrap: wrap; gap: 10px; }
@media (max-width: 1150px) { .filter-band { grid-template-columns: 1fr 1fr; } }
@media (max-width: 720px) {
  .workspace-page { padding: 20px 14px; }
  .page-heading { flex-direction: column; gap: 14px; }
  .heading-actions { width: 100%; padding-top: 0; justify-content: space-between; }
  .filter-band { grid-template-columns: 1fr; }
  .detail-grid { grid-template-columns: 1fr; }
  .detail-grid .wide { grid-column: auto; }
}
</style>
