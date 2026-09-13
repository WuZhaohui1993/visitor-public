<script setup lang="ts">
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { changeAdminPassword } from "@/api/admin";
import { useUserStoreHook } from "@/store/modules/user";

const loading = ref(false);
const form = reactive({ currentPassword: "", newPassword: "", confirmPassword: "" });

async function submit() {
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.error("两次输入的新密码不一致");
    return;
  }
  loading.value = true;
  try {
    await changeAdminPassword(form.currentPassword, form.newPassword);
    ElMessage.success("密码已修改，请重新登录");
    useUserStoreHook().logOut();
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || "修改密码失败");
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="password-page">
    <section class="password-form">
      <img src="/logo.png" alt="系统标识" />
      <p class="eyebrow">FIRST SIGN IN</p>
      <h1>首次登录修改密码</h1>
      <p>新密码至少 12 位，并同时包含大小写字母、数字和符号。</p>
      <el-input v-model="form.currentPassword" type="password" show-password size="large" placeholder="当前密码" />
      <el-input v-model="form.newPassword" type="password" show-password size="large" placeholder="新密码" />
      <el-input v-model="form.confirmPassword" type="password" show-password size="large" placeholder="再次输入新密码" />
      <el-button type="primary" size="large" :loading="loading" @click="submit">保存并重新登录</el-button>
    </section>
  </main>
</template>

<style scoped>
.password-page { display: grid; min-height: 100vh; place-items: center; padding: 24px; background: #f2f5f3; }
.password-form { display: grid; gap: 18px; width: min(100%, 440px); padding: 40px; background: white; border-top: 4px solid #176b5b; box-shadow: 0 18px 60px rgb(20 55 45 / 10%); }
.password-form img { width: 48px; height: 48px; object-fit: contain; }
.password-form h1 { margin: 0; font-size: 30px; color: #16221f; }
.password-form p { margin: 0; line-height: 1.7; color: #6f7e79; }
.password-form .eyebrow { font-size: 12px; font-weight: 700; letter-spacing: 0; color: #5f9d89; }
</style>
