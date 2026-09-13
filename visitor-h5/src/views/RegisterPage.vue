<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { registerVisitor, searchVisitors, uploadFile } from '../api'

const router = useRouter()
const form = reactive({
  visitorName: '',
  idCardNo: '',
  phone: '',
  idCardFrontKey: '',
  idCardBackKey: '',
  facePhotoKey: '',
  visitedUserId: '',
  visitedUserName: '',
  visitedDeptName: '',
  plannedEntryTime: '',
  plannedExitTime: '',
  visitReason: '',
})

const loading = ref(false)
const searchLoading = ref(false)
const searchKeyword = ref('')
const candidates = ref([])
const searchError = ref('')
const uploadState = reactive({
  front: { loading: false, previewUrl: '', error: '' },
  back: { loading: false, previewUrl: '', error: '' },
  face: { loading: false, previewUrl: '', error: '' },
})
const timePicker = reactive({
  visible: false,
  field: 'plannedEntryTime',
  date: '',
  hour: '09',
  minute: '00',
})

let timer
let selectingCandidate = false

const hourOptions = Array.from({ length: 24 }, (_, index) => String(index).padStart(2, '0'))
const minuteOptions = ['00', '10', '20', '30', '40', '50']

watch(searchKeyword, (value) => {
  if (selectingCandidate) {
    candidates.value = []
    searchError.value = ''
    selectingCandidate = false
    return
  }
  if (!selectingCandidate) {
    form.visitedUserId = ''
    form.visitedUserName = ''
    form.visitedDeptName = ''
  }
  clearTimeout(timer)
  if (value.trim().length < 2) {
    candidates.value = []
    searchError.value = value ? '至少输入 2 个字开始搜索' : ''
    selectingCandidate = false
    return
  }
  timer = setTimeout(async () => {
    searchLoading.value = true
    searchError.value = ''
    try {
      candidates.value = await searchVisitors(value.trim())
    } catch (error) {
      searchError.value = error.message
    } finally {
      searchLoading.value = false
      selectingCandidate = false
    }
  }, 300)
})

function validateForm() {
  if (!form.visitorName.trim()) return '请输入访客姓名'
  if (form.visitorName.trim().length > 20) return '访客姓名不能超过 20 个字'
  if (!/^[0-9]{17}[0-9Xx]$/.test(form.idCardNo.trim())) return '请输入有效的身份证号'
  if (!/^1[3-9]\d{9}$/.test(form.phone.trim())) return '请输入有效的手机号'
  if (!form.idCardFrontKey || !form.idCardBackKey) return '请上传身份证正反面'
  if (uploadState.face.error) return uploadState.face.error
  if (!form.facePhotoKey) return '请上传人脸自拍照'
  if (!form.visitedUserId) return '请选择被访人'
  if (!form.plannedEntryTime) return '请选择预计入场时间'
  if (!form.plannedExitTime) return '请选择预计出场时间'
  if (new Date(form.plannedExitTime).getTime() <= new Date(form.plannedEntryTime).getTime()) return '预计出场时间必须晚于预计入场时间'
  if (form.visitReason.length > 200) return '访问事由不能超过 200 字'
  return ''
}

function toIsoLocalDateTime(value) {
  return value ? `${value}:00` : value
}

function formatDisplayDateTime(value) {
  if (!value) return '请选择时间'
  return value.replace('T', ' ')
}

function roundMinutes(date) {
  const result = new Date(date)
  const minutes = result.getMinutes()
  const rounded = Math.ceil(minutes / 10) * 10
  result.setSeconds(0, 0)
  if (rounded === 60) {
    result.setHours(result.getHours() + 1, 0, 0, 0)
  } else {
    result.setMinutes(rounded, 0, 0)
  }
  return result
}

function formatDatePart(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function formatTimePart(date) {
  return {
    hour: String(date.getHours()).padStart(2, '0'),
    minute: String(date.getMinutes()).padStart(2, '0'),
  }
}

function getSuggestedDate(field) {
  const now = roundMinutes(new Date())
  if (field === 'plannedExitTime') {
    if (form.plannedEntryTime) {
      const base = new Date(form.plannedEntryTime)
      base.setHours(base.getHours() + 1)
      return base
    }
    now.setHours(now.getHours() + 1)
    return now
  }
  if (form.plannedEntryTime) {
    return new Date(form.plannedEntryTime)
  }
  return now
}

function openTimePicker(field) {
  const currentValue = form[field]
  const date = currentValue ? new Date(currentValue) : getSuggestedDate(field)
  const { hour, minute } = formatTimePart(date)
  timePicker.visible = true
  timePicker.field = field
  timePicker.date = formatDatePart(date)
  timePicker.hour = hour
  timePicker.minute = minuteOptions.includes(minute) ? minute : minuteOptions[0]
}

function closeTimePicker() {
  timePicker.visible = false
}

function confirmTimePicker() {
  if (!timePicker.date) {
    window.alert('请选择日期')
    return
  }
  form[timePicker.field] = `${timePicker.date}T${timePicker.hour}:${timePicker.minute}`
  timePicker.visible = false
}

const pickerTitle = computed(() => timePicker.field === 'plannedEntryTime' ? '选择预计入场时间' : '选择预计出场时间')

async function handleUpload(side, event) {
  const file = event.target.files?.[0]
  if (!file) return
  const localError = validateUploadSelection(file)
  if (localError) {
    uploadState[side].error = localError
    event.target.value = ''
    return
  }
  uploadState[side].error = ''
  uploadState[side].loading = true
  try {
    const result = await uploadFile(file, side === 'face' ? 'face' : 'general')
    const previewUrl = URL.createObjectURL(file)
    if (side === 'front') {
      form.idCardFrontKey = result.fileKey
      replacePreviewUrl('front', previewUrl)
    } else if (side === 'back') {
      form.idCardBackKey = result.fileKey
      replacePreviewUrl('back', previewUrl)
    } else {
      form.facePhotoKey = result.fileKey
      replacePreviewUrl('face', previewUrl)
    }
  } catch (error) {
    uploadState[side].error = error.message
    if (side === 'face') {
      form.facePhotoKey = ''
      uploadState.face.previewUrl = ''
    }
  } finally {
    uploadState[side].loading = false
    event.target.value = ''
  }
}

function replacePreviewUrl(side, nextUrl) {
  const currentUrl = uploadState[side].previewUrl
  if (currentUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(currentUrl)
  }
  uploadState[side].previewUrl = nextUrl
}

function validateUploadSelection(file) {
  const allowedTypes = ['image/jpeg', 'image/png']
  if (file.size > 5 * 1024 * 1024) {
    return '图片不能超过5MB'
  }
  if (file.type && !allowedTypes.includes(file.type)) {
    return '仅支持 JPG/PNG 图片'
  }
  return ''
}

function selectCandidate(candidate) {
  selectingCandidate = true
  form.visitedUserId = candidate.userId
  form.visitedUserName = candidate.name
  form.visitedDeptName = candidate.deptName
  searchKeyword.value = candidate.name
  candidates.value = []
}

async function submit() {
  const error = validateForm()
  if (error) {
    window.alert(error)
    return
  }
  loading.value = true
  try {
    const result = await registerVisitor({
      ...form,
      visitorName: form.visitorName.trim(),
      idCardNo: form.idCardNo.trim().toUpperCase(),
      phone: form.phone.trim(),
      plannedEntryTime: toIsoLocalDateTime(form.plannedEntryTime),
      plannedExitTime: toIsoLocalDateTime(form.plannedExitTime),
      visitReason: form.visitReason.trim(),
    })
    router.push({
      path: `/visitor/result/${result.bizId}`,
      query: { accessToken: result.resultAccessToken },
    })
  } catch (error) {
    window.alert(error.message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="page">
    <div class="hero-card">
      <h1>访客登记</h1>
      <p class="hero-copy">扫码填写来访信息，提交后系统会自动通知被访人审批。</p>
    </div>

    <div class="panel">
      <label class="field">
        <span>姓名</span>
        <input v-model="form.visitorName" type="text" maxlength="20" placeholder="请输入访客姓名" />
      </label>

      <label class="field">
        <span>身份证号</span>
        <input v-model="form.idCardNo" type="text" maxlength="18" placeholder="请输入身份证号" />
      </label>

      <label class="field">
        <span>手机号</span>
        <input v-model="form.phone" type="tel" maxlength="11" placeholder="请输入手机号" />
      </label>

      <div class="upload-grid">
        <label class="upload-card">
          <span>身份证正面</span>
          <input type="file" accept="image/png,image/jpeg" @change="handleUpload('front', $event)" />
          <strong>{{ uploadState.front.loading ? '上传中...' : '选择图片' }}</strong>
          <img v-if="uploadState.front.previewUrl" :src="uploadState.front.previewUrl" alt="身份证正面" />
          <small v-if="uploadState.front.error" class="upload-card-error">{{ uploadState.front.error }}</small>
        </label>

        <label class="upload-card">
          <span>身份证背面</span>
          <input type="file" accept="image/png,image/jpeg" @change="handleUpload('back', $event)" />
          <strong>{{ uploadState.back.loading ? '上传中...' : '选择图片' }}</strong>
          <img v-if="uploadState.back.previewUrl" :src="uploadState.back.previewUrl" alt="身份证背面" />
          <small v-if="uploadState.back.error" class="upload-card-error">{{ uploadState.back.error }}</small>
        </label>

        <label class="upload-card face-card">
          <span>人脸自拍照</span>
          <input type="file" accept="image/png,image/jpeg" @change="handleUpload('face', $event)" />
          <strong>{{ uploadState.face.loading ? '上传中...' : '选择图片' }}</strong>
          <img v-if="uploadState.face.previewUrl" :src="uploadState.face.previewUrl" alt="人脸自拍照" />
          <small class="upload-card-tip">系统会先做人脸评分校验，建议上传无遮挡、光线充足的清晰正脸照。</small>
          <small v-if="uploadState.face.error" class="upload-card-error">{{ uploadState.face.error }}</small>
        </label>
      </div>

      <label class="field">
        <span>被访人员</span>
        <input v-model="searchKeyword" type="text" placeholder="输入姓名或部门搜索，至少 2 个字" />
      </label>

      <div v-if="searchLoading" class="hint">正在搜索被访人...</div>
      <div v-else-if="searchError" class="hint danger">{{ searchError }}</div>
      <ul v-else-if="candidates.length" class="candidate-list">
        <li v-for="candidate in candidates" :key="candidate.userId" @click="selectCandidate(candidate)">
          <strong>{{ candidate.name }} - {{ candidate.deptName }}</strong>
          <span>{{ candidate.deptName }}</span>
        </li>
      </ul>
      <div v-else-if="searchKeyword.trim().length >= 2" class="hint">暂无更多结果，可换关键字继续搜索。</div>

      <div v-if="form.visitedUserId" class="selected-user">
        已选择：{{ form.visitedUserName }} - {{ form.visitedDeptName }}
      </div>

      <div class="time-grid">
        <label class="field">
          <span>预计入场时间</span>
          <button type="button" class="time-trigger" @click="openTimePicker('plannedEntryTime')">
            {{ formatDisplayDateTime(form.plannedEntryTime) }}
          </button>
        </label>

        <label class="field">
          <span>预计出场时间</span>
          <button type="button" class="time-trigger" @click="openTimePicker('plannedExitTime')">
            {{ formatDisplayDateTime(form.plannedExitTime) }}
          </button>
        </label>
      </div>

      <label class="field">
        <span>访问事由</span>
        <textarea v-model="form.visitReason" maxlength="200" rows="4" placeholder="请输入访问事由（选填）" />
      </label>

      <div class="footer-actions">
        <button class="primary-button" :disabled="loading" @click="submit">
          {{ loading ? '提交中...' : '提交登记' }}
        </button>
        <RouterLink class="secondary-link" to="/visitor/query">查询我的申请单</RouterLink>
        <RouterLink class="secondary-link" to="/cargo/register">送货司机登记</RouterLink>
      </div>
    </div>

    <div v-if="timePicker.visible" class="picker-mask" @click.self="closeTimePicker">
      <div class="picker-sheet">
        <div class="picker-header">
          <strong>{{ pickerTitle }}</strong>
          <button type="button" class="picker-close" @click="closeTimePicker">取消</button>
        </div>

        <label class="field">
          <span>日期</span>
          <input v-model="timePicker.date" type="date" class="picker-input" />
        </label>

        <div class="picker-grid">
          <label class="field">
            <span>小时</span>
            <select v-model="timePicker.hour" class="picker-select">
              <option v-for="hour in hourOptions" :key="hour" :value="hour">{{ hour }}</option>
            </select>
          </label>

          <label class="field">
            <span>分钟</span>
            <select v-model="timePicker.minute" class="picker-select">
              <option v-for="minute in minuteOptions" :key="minute" :value="minute">{{ minute }}</option>
            </select>
          </label>
        </div>

        <button type="button" class="primary-button picker-confirm" @click="confirmTimePicker">确认时间</button>
      </div>
    </div>
  </section>
</template>
