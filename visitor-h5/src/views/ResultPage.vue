<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { fetchResult } from '../api'

const route = useRoute()
const result = ref(null)
const loading = ref(true)
const error = ref('')
let intervalId
let stopTimeout

const resultAccessToken = computed(() => {
  return typeof route.query.accessToken === 'string' ? route.query.accessToken.trim() : ''
})

const passState = computed(() => {
  if (!result.value) return { title: '凭证加载中', copy: '正在同步审批状态。', tone: 'pending' }
  if (result.value.entryAllowed) {
    return {
      title: '可入场',
      copy: '请向门岗出示本页，核对访客姓名、被访人和时间窗口后放行。',
      tone: 'approved',
    }
  }
  if (result.value.status === 1 && !result.value.expired) {
    const waitingTitle = result.value.hikSyncStatus !== 'SUCCESS'
      ? '等待海康同步'
      : result.value.hikAccessStatus === 'SUCCESS' || !result.value.hikAccessStatus
        ? '审批已通过'
        : '等待权限下发'
    return {
      title: waitingTitle,
      copy: result.value.entryMessage,
      tone: 'approved-pending',
    }
  }
  if (result.value.expired || result.value.status === 2 || result.value.status === 1) {
    return {
      title: '不可入场',
      copy: result.value.entryMessage,
      tone: 'rejected',
    }
  }
  return {
    title: '等待审批',
    copy: '审批完成前请勿入场，本页面会自动刷新状态。',
    tone: 'pending',
  }
})

const shortBizId = computed(() => {
  if (!result.value?.bizId) return '--'
  return result.value.bizId.slice(0, 8).toUpperCase()
})

const approvedAt = computed(() => {
  if (!result.value?.approveTime) return '待审批'
  return formatTime(result.value.approveTime)
})

const createdAt = computed(() => {
  if (!result.value?.createTime) return '--'
  return formatTime(result.value.createTime)
})

function formatTime(value) {
  if (!value) return '--'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(0, 16) : text
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!resultAccessToken.value) {
      throw new Error('缺少结果访问凭证，请从登记完成页或查询页重新进入')
    }
    result.value = await fetchResult(route.params.bizId, resultAccessToken.value)
  } catch (err) {
    error.value = err.message
  } finally {
    loading.value = false
  }
}

function setupPolling() {
  clearInterval(intervalId)
  clearTimeout(stopTimeout)
  intervalId = window.setInterval(async () => {
    await load()
    if (!result.value || result.value.expired || (result.value.status !== 0 && result.value.hikSyncStatus !== 'PENDING' && result.value.hikAccessStatus !== 'PENDING')) {
      clearInterval(intervalId)
    }
  }, 5000)
  stopTimeout = window.setTimeout(() => {
    clearInterval(intervalId)
  }, 10 * 60 * 1000)
}

onMounted(async () => {
  await load()
  if (result.value && !result.value.expired && (result.value.status === 0 || result.value.hikSyncStatus === 'PENDING' || result.value.hikAccessStatus === 'PENDING')) {
    setupPolling()
  }
})

onBeforeUnmount(() => {
  clearInterval(intervalId)
  clearTimeout(stopTimeout)
})
</script>

<template>
  <section class="page">
    <div class="panel pass-panel" :class="passState.tone">
      <div v-if="loading" class="empty-state">正在加载审批状态...</div>
      <div v-else-if="error" class="empty-state danger">
        {{ error }}
        <button class="secondary-button" @click="load">重新加载</button>
      </div>
      <template v-else-if="result">
        <div class="pass-header">
          <div>
            <h2>{{ passState.title }}</h2>
            <p class="hero-copy">{{ passState.copy }}</p>
          </div>
          <div class="pass-stamp" :class="passState.tone">{{ result.statusText }}</div>
        </div>

          <div class="pass-grid">
          <div>
            <span>访客</span>
            <strong>{{ result.visitorName }}</strong>
          </div>
          <div>
            <span>被访人</span>
            <strong>{{ `${result.visitedUserName} / ${result.visitedDeptName}` }}</strong>
          </div>
          <div>
            <span>登记编号</span>
            <strong>{{ result.recordNo || shortBizId }}</strong>
          </div>
          <div>
            <span>提交时间</span>
            <strong>{{ createdAt }}</strong>
          </div>
          <div>
            <span>审批时间</span>
            <strong>{{ approvedAt }}</strong>
          </div>
          <div>
            <span>门岗提示</span>
            <strong>{{ result.entryMessage || '待系统判断' }}</strong>
          </div>
          </div>

          <div v-if="result.hikSyncStatusText" class="hint" :class="result.hikSyncStatus === 'FAILED' ? 'danger' : ''">
            海康状态：{{ result.hikSyncStatusText }}
            <template v-if="result.hikSyncError">
              ，{{ result.hikSyncError }}
            </template>
          </div>

          <div v-if="result.hikAccessStatusText" class="hint" :class="result.hikAccessStatus === 'FAILED' ? 'danger' : ''">
            权限状态：{{ result.hikAccessStatusText }}
            <template v-if="result.hikAccessError">
              ，{{ result.hikAccessError }}
            </template>
          </div>

          <dl class="detail-grid">
          <div>
            <dt>身份证号</dt>
            <dd>{{ result.idCardMasked }}</dd>
          </div>
          <div>
            <dt>手机号</dt>
            <dd>{{ result.phoneMasked }}</dd>
          </div>
          <div>
            <dt>访问事由</dt>
            <dd>{{ result.visitReason || '未填写' }}</dd>
          </div>
          <div>
            <dt>预计入场时间</dt>
            <dd>{{ formatTime(result.plannedEntryTime) }}</dd>
          </div>
          <div>
            <dt>预计出场时间</dt>
            <dd>{{ formatTime(result.plannedExitTime) }}</dd>
          </div>
          <div>
            <dt>审批备注</dt>
            <dd>{{ result.approveRemark || '无' }}</dd>
          </div>
        </dl>

        <div class="footer-actions">
          <button class="secondary-button" @click="load">刷新状态</button>
          <RouterLink class="secondary-link" to="/visitor/query">查询其他申请单</RouterLink>
        </div>
      </template>
    </div>
  </section>
</template>
