<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import {
  createAdminRole,
  fetchAdminPermissions,
  fetchAdminRoles,
  updateAdminRole,
  type AdminPermission,
  type AdminRole
} from "@/api/admin";

defineOptions({ name: "AdminRoles" });

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const roles = ref<AdminRole[]>([]);
const permissions = ref<AdminPermission[]>([]);
const editingId = ref<number | null>(null);
const form = reactive({ code: "", name: "", enabled: true, permissionCodes: [] as string[] });

async function load() {
  loading.value = true;
  try { [roles.value, permissions.value] = await Promise.all([fetchAdminRoles(), fetchAdminPermissions()]); }
  catch (error: any) { ElMessage.error(error?.response?.data?.message || "角色权限加载失败"); }
  finally { loading.value = false; }
}

function openCreate() {
  editingId.value = null;
  Object.assign(form, { code: "", name: "", enabled: true, permissionCodes: [] });
  dialogVisible.value = true;
}

function openEdit(role: AdminRole) {
  editingId.value = role.id;
  Object.assign(form, { code: role.code, name: role.name, enabled: role.enabled, permissionCodes: [...role.permissionCodes] });
  dialogVisible.value = true;
}

async function save() {
  saving.value = true;
  try {
    const data = { code: form.code, name: form.name, enabled: form.enabled, permissionCodes: form.permissionCodes };
    if (editingId.value) await updateAdminRole(editingId.value, data); else await createAdminRole(data);
    ElMessage.success(editingId.value ? "角色已更新" : "角色已创建");
    dialogVisible.value = false;
    await load();
  } catch (error: any) { ElMessage.error(error?.response?.data?.message || "保存失败"); }
  finally { saving.value = false; }
}

onMounted(load);
</script>

<template>
  <section class="workspace-page">
    <header class="page-heading"><div><p class="section-kicker">ROLES & PERMISSIONS</p><h1>角色权限</h1><p>菜单可见性只改善操作体验，所有接口仍由后端权限强制校验。</p></div><el-button type="primary" @click="openCreate">新增角色</el-button></header>
    <div class="role-list" v-loading="loading">
      <div v-for="role in roles" :key="role.id" class="role-row">
        <div><strong>{{ role.name }}</strong><small>{{ role.code }}</small></div>
        <span>{{ role.permissionCodes.length }} 项权限</span>
        <el-tag :type="role.enabled ? 'success' : 'info'" effect="plain">{{ role.enabled ? '启用' : '停用' }}</el-tag>
        <el-button link type="primary" @click="openEdit(role)">配置权限</el-button>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="680px">
      <el-form label-position="top">
        <div class="form-grid"><el-form-item label="角色编码"><el-input v-model="form.code" placeholder="例如 FRONT_DESK" /></el-form-item><el-form-item label="角色名称"><el-input v-model="form.name" /></el-form-item></div>
        <el-form-item label="角色状态"><el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" /></el-form-item>
        <el-form-item label="权限"><el-checkbox-group v-model="form.permissionCodes" class="permission-list"><label v-for="permission in permissions" :key="permission.code" class="permission-row"><el-checkbox :value="permission.code">{{ permission.name }}</el-checkbox><code>{{ permission.code }}</code></label></el-checkbox-group></el-form-item>
      </el-form>
      <template #footer><el-button @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
    </el-dialog>
  </section>
</template>

<style scoped>
.workspace-page{min-height:100%;padding:28px 30px 40px;background:#f5f7f6;color:#16221f}.page-heading{display:flex;align-items:flex-start;justify-content:space-between;padding-bottom:22px}.page-heading h1{margin:6px 0 8px;font-size:30px;font-weight:650}.page-heading p{margin:0;color:#6f7e79}.section-kicker{font-size:11px;font-weight:750;color:#4e8a77}.role-list{background:white;border-top:3px solid #176b5b}.role-row{display:grid;grid-template-columns:minmax(200px,1fr) 130px 100px 100px;gap:20px;align-items:center;padding:20px 24px;border-bottom:1px solid #e4e9e7}.role-row>div{display:grid;gap:4px}.role-row small{color:#788580}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:16px}.permission-list{display:grid;width:100%;grid-template-columns:1fr 1fr;border-top:1px solid #e4e9e7}.permission-row{display:flex;align-items:center;justify-content:space-between;padding:11px 8px;border-bottom:1px solid #edf0ef}.permission-row code{font-size:11px;color:#7a8783}
</style>
