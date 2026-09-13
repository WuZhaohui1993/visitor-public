<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import dayjs from "dayjs";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  createAdminUser,
  fetchAdminRoles,
  fetchAdminUsers,
  resetAdminPassword,
  updateAdminUser,
  type AdminRole,
  type AdminUser
} from "@/api/admin";

defineOptions({ name: "AdminUsers" });

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const users = ref<AdminUser[]>([]);
const roles = ref<AdminRole[]>([]);
const editingId = ref<number | null>(null);
const form = reactive({ username: "", displayName: "", password: "", enabled: true, roleCodes: [] as string[] });

async function load() {
  loading.value = true;
  try {
    [users.value, roles.value] = await Promise.all([fetchAdminUsers(), fetchAdminRoles()]);
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "管理员数据加载失败");
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, { username: "", displayName: "", password: "", enabled: true, roleCodes: [] });
  dialogVisible.value = true;
}

function openEdit(user: AdminUser) {
  editingId.value = user.id;
  Object.assign(form, { username: user.username, displayName: user.displayName, password: "", enabled: user.enabled, roleCodes: [...user.roleCodes] });
  dialogVisible.value = true;
}

async function save() {
  saving.value = true;
  try {
    if (editingId.value) {
      await updateAdminUser(editingId.value, { displayName: form.displayName, enabled: form.enabled, roleCodes: form.roleCodes });
    } else {
      await createAdminUser({ username: form.username, displayName: form.displayName, password: form.password, roleCodes: form.roleCodes });
    }
    ElMessage.success(editingId.value ? "管理员已更新" : "管理员已创建");
    dialogVisible.value = false;
    await load();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}

async function resetPassword(user: AdminUser) {
  const { value } = await ElMessageBox.prompt("新密码至少 12 位，并包含大小写字母、数字和符号。", `重置 ${user.username} 的密码`, {
    inputType: "password",
    confirmButtonText: "确认重置"
  });
  await resetAdminPassword(user.id, value);
  ElMessage.success("密码已重置，该账号下次登录必须修改密码");
  await load();
}

onMounted(load);
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading">
      <div><p class="section-kicker">ADMIN USERS</p><h1>管理员</h1><p>管理后台账号、启停状态和角色分配。</p></div>
      <el-button type="primary" @click="openCreate">新增管理员</el-button>
    </header>
    <div class="table-surface">
      <el-table v-loading="loading" :data="users">
        <el-table-column label="账号" min-width="180"><template #default="{ row }"><strong>{{ row.username }}</strong><small>{{ row.displayName }}</small></template></el-table-column>
        <el-table-column label="角色" min-width="220"><template #default="{ row }"><div class="tag-list"><el-tag v-for="role in row.roleCodes" :key="role" effect="plain">{{ role }}</el-tag></div></template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">{{ row.enabled ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="登录要求" width="140"><template #default="{ row }">{{ row.mustChangePassword ? '需修改密码' : '正常' }}</template></el-table-column>
        <el-table-column label="最后登录" width="170"><template #default="{ row }">{{ row.lastLoginTime ? dayjs(row.lastLoginTime).format('YYYY-MM-DD HH:mm') : '--' }}</template></el-table-column>
        <el-table-column label="操作" fixed="right" width="160"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link @click="resetPassword(row)">重置密码</el-button></template></el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑管理员' : '新增管理员'" width="520px">
      <el-form label-position="top">
        <el-form-item label="登录账号"><el-input v-model="form.username" :disabled="Boolean(editingId)" placeholder="小写字母、数字、点、下划线或横线" /></el-form-item>
        <el-form-item label="管理员姓名"><el-input v-model="form.displayName" /></el-form-item>
        <el-form-item v-if="!editingId" label="初始密码"><el-input v-model="form.password" type="password" show-password /></el-form-item>
        <el-form-item label="分配角色"><el-select v-model="form.roleCodes" multiple style="width: 100%"><el-option v-for="role in roles.filter(item => item.enabled)" :key="role.code" :label="role.name" :value="role.code" /></el-select></el-form-item>
        <el-form-item v-if="editingId" label="账号状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.workspace-page{min-height:100%;padding:28px 30px 40px;background:#f5f7f6;color:#16221f}.page-heading{display:flex;align-items:flex-start;justify-content:space-between;padding-bottom:22px}.page-heading h1{margin:6px 0 8px;font-size:30px;font-weight:650}.page-heading p{margin:0;color:#6f7e79}.section-kicker{font-size:11px;font-weight:750;color:#4e8a77}.table-surface{padding:16px;background:white;border-top:3px solid #176b5b}.table-surface :deep(.cell){display:grid;gap:4px}.table-surface small{color:#788580}.tag-list{display:flex;gap:6px;flex-wrap:wrap}
</style>
