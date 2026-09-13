<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useUserStoreHook } from "@/store/modules/user";
import { getLoginCaptcha, type CaptchaResult } from "@/api/user";
import { getTopMenu, initRouter } from "@/router/utils";

const router = useRouter();
const userStore = useUserStoreHook();
const loading = ref(false);
const errorMessage = ref("");
const form = reactive({
  username: "admin",
  password: "",
  captchaId: "",
  captchaCode: ""
});
const captcha = ref<CaptchaResult>({ required: false });

async function refreshCaptcha() {
  if (!form.username.trim()) return;
  try {
    captcha.value = await getLoginCaptcha(form.username.trim());
    form.captchaId = captcha.value.challengeId || "";
    form.captchaCode = "";
  } catch {
    captcha.value = { required: false };
  }
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    errorMessage.value = "请输入管理员账号和密码";
    return;
  }
  if (captcha.value.required && !form.captchaCode.trim()) {
    errorMessage.value = "请输入图形验证码";
    return;
  }
  loading.value = true;
  errorMessage.value = "";
  try {
    const result = await userStore.loginByUsername({
      username: form.username.trim(),
      password: form.password,
      captchaId: form.captchaId,
      captchaCode: form.captchaCode.trim()
    });
    ElMessage.success("登录成功");
    if (result.data.mustChangePassword) {
      await router.replace("/change-password");
      return;
    }
    await initRouter();
    await router.replace(getTopMenu()?.path ?? "/access-denied");
  } catch (error: any) {
    errorMessage.value = error?.response?.data?.message || error?.message || String(error) || "登录失败";
    await refreshCaptcha();
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel">
      <div class="brand-mark">
        <img src="/logo.png" alt="系统标识" />
        <span>访客管理中心</span>
      </div>
      <div class="brand-copy">
        <p class="eyebrow">VISITOR OPERATIONS</p>
        <h1>访客台账、门禁状态与系统配置，统一管理。</h1>
        <p>独立管理端不改变访客登记与审批链路，所有敏感访问和运维操作均留痕。</p>
      </div>
      <p class="environment">管理员入口 · 请通过受控网络访问</p>
    </section>

    <section class="login-panel">
      <form class="login-form" @submit.prevent="submit">
        <div>
          <p class="eyebrow">ADMIN SIGN IN</p>
          <h2>管理员登录</h2>
          <p class="form-note">使用分配给你的后台账号登录。</p>
        </div>

        <label>
          <span>账号</span>
          <el-input
            v-model="form.username"
            size="large"
            autocomplete="username"
            placeholder="请输入管理员账号"
            autofocus
            @blur="refreshCaptcha"
          />
        </label>

        <label v-if="captcha.required">
          <span>验证码</span>
          <div class="captcha-field">
            <el-input
              v-model="form.captchaCode"
              size="large"
              maxlength="5"
              autocomplete="off"
              placeholder="请输入图中字符"
            />
            <button type="button" title="刷新验证码" @click="refreshCaptcha">
              <img :src="captcha.imageData" alt="图形验证码" />
            </button>
          </div>
        </label>

        <label>
          <span>密码</span>
          <el-input
            v-model="form.password"
            size="large"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入管理员密码"
            @keyup.enter="submit"
          />
        </label>

        <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="loading"
          class="submit-button"
        >
          登录
        </el-button>
      </form>
    </section>
  </main>
</template>

<style scoped lang="scss">
.login-page {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(420px, 0.75fr);
  min-height: 100vh;
  color: #16221f;
  background: #f2f5f3;
}

.brand-panel {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 100vh;
  padding: 44px 56px;
  color: #eff8f4;
  background: #0f4f43;
}

.brand-mark {
  display: flex;
  gap: 14px;
  align-items: center;
  font-size: 18px;
  font-weight: 650;
  letter-spacing: 0;

  img {
    width: 42px;
    height: 42px;
    object-fit: contain;
    background: white;
    border-radius: 10px;
  }
}

.brand-copy {
  max-width: 680px;

  h1 {
    max-width: 640px;
    margin: 18px 0 22px;
    font-size: 56px;
    font-weight: 620;
    line-height: 1.08;
    letter-spacing: 0;
  }

  > p:last-child {
    max-width: 560px;
    font-size: 17px;
    line-height: 1.8;
    color: rgb(239 248 244 / 76%);
  }
}

.eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  color: #5f9d89;
}

.brand-panel .eyebrow {
  color: #8ed9c0;
}

.environment {
  font-size: 13px;
  color: rgb(239 248 244 / 60%);
}

.login-panel {
  display: grid;
  place-items: center;
  padding: 48px;
}

.login-form {
  display: grid;
  gap: 24px;
  width: min(100%, 420px);

  h2 {
    margin: 10px 0 8px;
    font-size: 32px;
    font-weight: 650;
    letter-spacing: 0;
  }

  label {
    display: grid;
    gap: 9px;

    > span {
      font-size: 14px;
      font-weight: 600;
    }
  }
}

.form-note {
  color: #6f7e79;
}

.error-message {
  padding: 11px 13px;
  font-size: 13px;
  color: #9b2c2c;
  background: #fff0f0;
  border-left: 3px solid #cf4d4d;
}

.captcha-field {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  gap: 10px;

  button {
    width: 150px;
    height: 40px;
    padding: 0;
    overflow: hidden;
    background: #edf4f1;
    border: 1px solid #d7e2de;
    cursor: pointer;
  }

  img {
    display: block;
    width: 150px;
    height: 40px;
  }
}

.submit-button {
  width: 100%;
  height: 46px;
  margin-top: 2px;
  font-weight: 650;
}

@media (max-width: 900px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    min-height: 280px;
    padding: 28px;
  }

  .brand-copy h1 {
    max-width: 540px;
    margin: 12px 0;
    font-size: 36px;
  }

  .brand-copy > p:last-child,
  .environment {
    display: none;
  }

  .login-panel {
    padding: 44px 24px;
  }
}
</style>
