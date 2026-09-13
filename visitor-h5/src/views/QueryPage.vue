<script setup>
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { queryVisitors } from '../api'

const form = reactive({
  phone: '',
  idCardSuffix: '',
})
const loading = ref(false)
const error = ref('')
const records = ref([])

function statusBadge(record) {
  if (record.entryAllowed) {
    return { text: '可入场', tone: 'approved' }
  }
  if (record.status === 1 && record.hikSyncStatus === 'PENDING') {
    return { text: '同步中', tone: 'pending' }
  }
  if (record.status === 1 && record.hikSyncStatus === 'FAILED') {
    return { text: '同步失败', tone: 'rejected' }
  }
  if (record.status === 1 && record.hikAccessStatus === 'PENDING') {
    return { text: '权限下发中', tone: 'pending' }
  }
  if (record.status === 1 && record.hikAccessStatus === 'FAILED') {
    return { text: '权限下发失败', tone: 'rejected' }
  }
  if (record.status === 1) {
    return { text: '已通过', tone: 'approved' }
  }
  if (record.expired) {
    return { text: '已过期', tone: 'expired' }
  }
  if (record.status === 2) {
    return { text: '已拒绝', tone: 'rejected' }
  }
  return { text: '待审批', tone: 'pending' }
}

function validate() {
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) return '请输入有效的手机号'
  if (!/^[0-9Xx]{6}$/.test(form.idCardSuffix.trim())) return '请输入身份证后6位'
  return ''
}

function formatTime(value) {
  if (!value) return '--'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

async function submit() {
  const message = validate()
  if (message) {
    window.alert(message)
    return
  }
  loading.value = true
  error.value = ''
  try {
    records.value = await queryVisitors({
      phone: form.phone.trim(),
      idCardSuffix: form.idCardSuffix.trim().toUpperCase(),
    })
  } catch (err) {
    records.value = []
    error.value = err.message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="hero-card">
      <h1>查询我的申请单</h1>
      <p class="hero-copy">输入手机号和身份证后6位，可找回最近提交的来访申请记录。</p>
    </div>

    <div class="panel">
      <label class="field">
        <span>手机号</span>
        <input v-model="form.phone" type="tel" maxlength="11" placeholder="请输入登记时填写的手机号" />
      </label>

      <label class="field">
        <span>身份证后6位</span>
        <input v-model="form.idCardSuffix" type="text" maxlength="6" placeholder="请输入身份证后6位" />
      </label>

      <div class="footer-actions">
        <button class="primary-button" :disabled="loading" @click="submit">
          {{ loading ? '查询中...' : '查询申请单' }}
        </button>
        <RouterLink class="secondary-link" to="/visitor/register">返回登记页</RouterLink>
      </div>

      <div v-if="error" class="hint danger">{{ error }}</div>

      <div v-if="records.length" class="record-list">
        <RouterLink
          v-for="record in records"
          :key="record.bizId"
          :to="{ path: `/visitor/result/${record.bizId}`, query: { accessToken: record.resultAccessToken } }"
          class="record-card"
        >
          <div class="record-card-header">
            <strong>{{ record.statusText }}</strong>
            <span :class="['status-badge', statusBadge(record).tone]">
              {{ statusBadge(record).text }}
            </span>
          </div>
          <p>{{ record.visitorName }} / {{ record.visitedUserName }}</p>
          <p>登记编号：{{ record.recordNo }}</p>
          <p>入场时间：{{ formatTime(record.plannedEntryTime) }}</p>
          <p>出场时间：{{ formatTime(record.plannedExitTime) }}</p>
        </RouterLink>
      </div>
    </div>
  </section>
</template>
