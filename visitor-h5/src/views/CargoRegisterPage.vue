<script setup>
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { createCargoEntry } from '../api'

function currentLocalDateTime() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 16)
}

function initialForm() {
  return {
    supplierUnit: '',
    receivingUnit: '',
    goodsName: '',
    quantity: '',
    receiverName: '',
    storageLocation: '',
    entryTime: currentLocalDateTime(),
  }
}

const form = reactive(initialForm())
const loading = ref(false)
const submitted = ref(null)

const fieldRules = [
  ['supplierUnit', '请输入供货单位', 100, '供货单位'],
  ['receivingUnit', '请输入收货单位', 100, '收货单位'],
  ['goodsName', '请输入货物名称', 100, '货物名称'],
  ['quantity', '请输入数量', 50, '数量'],
  ['receiverName', '请输入收货人姓名', 50, '收货人姓名'],
  ['storageLocation', '请输入存放位置', 100, '存放位置'],
]

function validateForm() {
  for (const [field, emptyMessage, maxLength, label] of fieldRules) {
    const value = form[field].trim()
    if (!value) return emptyMessage
    if (value.length > maxLength) return `${label}不能超过${maxLength}个字`
  }
  if (!form.entryTime) return '请选择入场时间'
  return ''
}

function normalizeDateTime(value) {
  return value?.length === 16 ? `${value}:00` : value
}

function formatDateTime(value) {
  return value ? value.replace('T', ' ') : '-'
}

async function submit() {
  const error = validateForm()
  if (error) {
    window.alert(error)
    return
  }

  loading.value = true
  try {
    submitted.value = await createCargoEntry({
      supplierUnit: form.supplierUnit.trim(),
      receivingUnit: form.receivingUnit.trim(),
      goodsName: form.goodsName.trim(),
      quantity: form.quantity.trim(),
      receiverName: form.receiverName.trim(),
      storageLocation: form.storageLocation.trim(),
      entryTime: normalizeDateTime(form.entryTime),
    })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  } catch (error) {
    window.alert(error.message)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, initialForm())
  submitted.value = null
}
</script>

<template>
  <section class="page cargo-page">
    <header class="page-heading">
      <h1>货物入场登记</h1>
      <p>厂区送货信息</p>
    </header>

    <div v-if="submitted" class="panel cargo-success" aria-live="polite">
      <span class="success-label">已保存</span>
      <h2>登记成功</h2>
      <dl class="cargo-receipt">
        <div>
          <dt>登记序号</dt>
          <dd>#{{ submitted.id }}</dd>
        </div>
        <div>
          <dt>入场时间</dt>
          <dd>{{ formatDateTime(submitted.entryTime) }}</dd>
        </div>
      </dl>
      <div class="footer-actions">
        <button type="button" class="primary-button" @click="resetForm">继续登记</button>
        <RouterLink class="secondary-link" to="/visitor/register">访客登记</RouterLink>
      </div>
    </div>

    <form v-else class="panel cargo-form" @submit.prevent="submit">
      <label class="field">
        <span>供货单位</span>
        <input v-model="form.supplierUnit" type="text" maxlength="100" autocomplete="organization" placeholder="请输入供货单位" />
      </label>

      <label class="field">
        <span>收货单位</span>
        <input v-model="form.receivingUnit" type="text" maxlength="100" placeholder="请输入收货单位" />
      </label>

      <label class="field">
        <span>货物名称</span>
        <input v-model="form.goodsName" type="text" maxlength="100" placeholder="请输入货物名称" />
      </label>

      <label class="field">
        <span>数量</span>
        <input v-model="form.quantity" type="text" maxlength="50" inputmode="text" placeholder="例如：10 吨、20 箱" />
      </label>

      <label class="field">
        <span>收货人姓名</span>
        <input v-model="form.receiverName" type="text" maxlength="50" autocomplete="name" placeholder="请输入收货人姓名" />
      </label>

      <label class="field">
        <span>存放位置</span>
        <input v-model="form.storageLocation" type="text" maxlength="100" placeholder="请输入存放位置" />
      </label>

      <label class="field">
        <span>入场时间</span>
        <input v-model="form.entryTime" type="datetime-local" class="cargo-time-input" />
      </label>

      <div class="footer-actions cargo-actions">
        <button type="submit" class="primary-button cargo-submit" :disabled="loading">
          {{ loading ? '提交中...' : '提交登记' }}
        </button>
        <RouterLink class="secondary-link" to="/visitor/register">访客登记</RouterLink>
      </div>
    </form>
  </section>
</template>
