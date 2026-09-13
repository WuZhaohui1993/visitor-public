<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { approveVisitor, authByDingTalk, clearVisitorAccessToken, fetchApproveDetail } from '../api'

const route = useRoute()
const detail = ref(null)
const loading = ref(true)
const error = ref('')
const notice = ref('')
const debugNotice = ref('')
const currentUserId = ref('')
const submitLoading = ref(false)
const dialog = reactive({
  visible: false,
  title: '',
  message: '',
  tone: 'success',
})
const form = reactive({
  status: 1,
  remark: '',
})

const inDingTalk = computed(() => /dingtalk/i.test(window.navigator.userAgent))
const corpId = computed(() => route.query.corpId || import.meta.env.VITE_DINGTALK_CORP_ID || '')
const debugUserId = computed(() => {
  const value = route.query.debugUserId || import.meta.env.VITE_DEBUG_APPROVE_USER_ID || ''
  return typeof value === 'string' ? value.trim() : ''
})
const debugEnabled = computed(() => {
  return import.meta.env.DEV || typeof route.query.debugUserId === 'string'
})

const canSubmit = computed(() => {
  return detail.value && detail.value.status === 0 && !detail.value.expired && currentUserId.value
})

function formatTime(value) {
  if (!value) return '--'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

function waitForDingTalkReady() {
  return new Promise((resolve, reject) => {
    if (!window.dd) {
      reject(new Error('钉钉 JSAPI 未加载成功，请检查网络环境或刷新重试。'))
      return
    }
    if (typeof window.dd.ready !== 'function') {
      resolve()
      return
    }
    let settled = false
    const finish = (handler) => (payload) => {
      if (settled) return
      settled = true
      handler(payload)
    }
    window.dd.ready(finish(() => resolve()))
    if (typeof window.dd.error === 'function') {
      window.dd.error(finish((err) => reject(new Error(err?.errorMessage || '钉钉 JSAPI 初始化失败'))))
    }
    window.setTimeout(finish(() => reject(new Error('钉钉 JSAPI 初始化超时，请稍后重试'))), 5000)
  })
}

function getAuthCode() {
  if (!corpId.value) {
    return Promise.reject(new Error('缺少 VITE_DINGTALK_CORP_ID，无法发起钉钉授权'))
  }
  if (window.dd?.runtime?.permission?.requestAuthCode) {
    return new Promise((resolve, reject) => {
      window.dd.runtime.permission.requestAuthCode({
        corpId: corpId.value,
        onSuccess: (res) => resolve(res.code),
        onFail: (err) => reject(new Error(err?.errorMessage || '钉钉授权失败')),
      })
    })
  }
  return Promise.resolve(import.meta.env.VITE_MOCK_AUTH_CODE || 'mock-u1001')
}

function openDialog(title, message, tone = 'success') {
  dialog.title = title
  dialog.message = message
  dialog.tone = tone
  dialog.visible = true
}

function closeDialog() {
  dialog.visible = false
}

async function authWithDebugFallback(reason) {
  if (!debugEnabled.value || !debugUserId.value) {
    throw new Error(reason)
  }
  debugNotice.value = `当前为调试审批模式，已改用 mock 身份继续联调。原因：${reason}`
  return authByDingTalk(`mock-${debugUserId.value}`)
}

async function bootstrap() {
  loading.value = true
  error.value = ''
  notice.value = ''
  debugNotice.value = ''
  clearVisitorAccessToken()
  try {
    if (!route.query.bizId || !route.query.token) {
      throw new Error('缺少审批参数')
    }
    let authResult
    if (!inDingTalk.value) {
      authResult = await authWithDebugFallback('请在钉钉内打开该审批页，或传入 debugUserId 进行本地联调')
    } else {
      try {
        await waitForDingTalkReady()
        const authCode = await getAuthCode()
        authResult = await authByDingTalk(authCode)
      } catch (authError) {
        authResult = await authWithDebugFallback(authError.message || '钉钉鉴权失败')
      }
    }
    currentUserId.value = authResult.userId
    detail.value = await fetchApproveDetail(route.query.bizId)
  } catch (err) {
    if (String(err.message).includes('用户不在当前企业')) {
      error.value = `当前审批页使用的企业标识不正确，请检查 corpId 配置。当前 corpId：${corpId.value || '未配置'}`
      return
    }
    error.value = err.message
  } finally {
    loading.value = false
  }
}

async function submit(status) {
  if (status === 2 && !form.remark.trim()) {
    openDialog('无法提交', '拒绝时请填写原因。', 'danger')
    return
  }
  submitLoading.value = true
  try {
    await approveVisitor({
      bizId: route.query.bizId,
      token: route.query.token,
      status,
      remark: form.remark.trim(),
    })
    detail.value = await fetchApproveDetail(route.query.bizId)
    notice.value = status === 1 ? '审批已通过，访客可在规定时间内入场。' : '审批已拒绝，系统已记录拒绝原因。'
    if (status === 1) {
      openDialog('审批成功', '审批已通过。', 'success')
    } else {
      openDialog('审批已拒绝', '系统已记录拒绝原因。', 'danger')
    }
  } catch (err) {
    openDialog('提交失败', err.message, 'danger')
  } finally {
    submitLoading.value = false
  }
}

onMounted(bootstrap)
</script>

<template>
  <section class="page">
    <div class="hero-card">
      <h1>来访审批</h1>
      <p class="hero-copy">请核对访客资料和来访时间，确认无误后完成审批。</p>
    </div>

    <div class="panel">
      <div v-if="loading" class="empty-state">正在加载审批信息...</div>
      <div v-else-if="error" class="empty-state danger">{{ error }}</div>
      <template v-else-if="detail">
        <div v-if="debugNotice" class="notice-banner warning">
          {{ debugNotice }}
        </div>

        <div v-if="notice" class="notice-banner" :class="detail.status === 1 ? 'success' : 'danger'">
          {{ notice }}
        </div>

        <dl class="detail-grid">
          <div>
            <dt>登记编号</dt>
            <dd>{{ detail.recordNo }}</dd>
          </div>
          <div>
            <dt>访客姓名</dt>
            <dd>{{ detail.visitorName }}</dd>
          </div>
          <div>
            <dt>身份证号</dt>
            <dd>{{ detail.idCardMasked }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd>{{ detail.phoneMasked }}</dd>
          </div>
          <div>
            <dt>被访人</dt>
            <dd>{{ detail.visitedUserName }} / {{ detail.visitedDeptName }}</dd>
          </div>
          <div>
            <dt>访问事由</dt>
            <dd>{{ detail.visitReason || '未填写' }}</dd>
          </div>
          <div>
            <dt>预计入场时间</dt>
            <dd>{{ formatTime(detail.plannedEntryTime) }}</dd>
          </div>
          <div>
            <dt>预计出场时间</dt>
            <dd>{{ formatTime(detail.plannedExitTime) }}</dd>
          </div>
          <div>
            <dt>提交时间</dt>
            <dd>{{ formatTime(detail.createTime) }}</dd>
          </div>
          <div>
            <dt>审批时间</dt>
            <dd>{{ formatTime(detail.approveTime) }}</dd>
          </div>
          <div>
            <dt>当前状态</dt>
            <dd>{{ detail.status === 0 ? '待审批' : detail.status === 1 ? '已同意' : '已拒绝' }}</dd>
          </div>
          <div>
            <dt>审批备注</dt>
            <dd>{{ detail.approveRemark || '无' }}</dd>
          </div>
        </dl>

        <div class="upload-grid approve-image-grid">
          <div class="upload-card static-card">
            <span>身份证正面</span>
            <img v-if="detail.idCardFrontUrl" :src="detail.idCardFrontUrl" alt="身份证正面" />
            <strong v-else>未上传</strong>
          </div>

          <div class="upload-card static-card">
            <span>身份证背面</span>
            <img v-if="detail.idCardBackUrl" :src="detail.idCardBackUrl" alt="身份证背面" />
            <strong v-else>未上传</strong>
          </div>

          <div class="upload-card static-card face-card">
            <span>人脸自拍照</span>
            <img v-if="detail.facePhotoUrl" :src="detail.facePhotoUrl" alt="人脸自拍照" />
            <strong v-else>未上传</strong>
          </div>
        </div>

        <label class="field">
          <span>拒绝原因</span>
          <textarea v-model="form.remark" rows="4" maxlength="200" placeholder="仅在拒绝时必填" />
        </label>

        <div class="footer-actions">
          <button class="primary-button" :disabled="!canSubmit || submitLoading" @click="submit(1)">同意</button>
          <button class="secondary-button danger" :disabled="!canSubmit || submitLoading" @click="submit(2)">拒绝</button>
        </div>
      </template>
    </div>

    <div v-if="dialog.visible" class="dialog-mask" @click="closeDialog">
      <div class="dialog-card" :class="dialog.tone" @click.stop>
        <h3>{{ dialog.title }}</h3>
        <p>{{ dialog.message }}</p>
        <button class="primary-button dialog-button" @click="closeDialog">我知道了</button>
      </div>
    </div>
  </section>
</template>
