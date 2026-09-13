const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
const ACCESS_TOKEN_STORAGE_KEY = 'visitor-auth-access-token'

export function getVisitorAccessToken() {
  return window.sessionStorage.getItem(ACCESS_TOKEN_STORAGE_KEY) || ''
}

export function setVisitorAccessToken(accessToken) {
  if (!accessToken) {
    window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
    return
  }
  window.sessionStorage.setItem(ACCESS_TOKEN_STORAGE_KEY, accessToken)
}

export function clearVisitorAccessToken() {
  window.sessionStorage.removeItem(ACCESS_TOKEN_STORAGE_KEY)
}

async function request(path, options = {}) {
  const { headers = {}, skipAuth = false, ...rest } = options
  const accessToken = skipAuth ? '' : getVisitorAccessToken()
  const response = await fetch(`${baseUrl}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...headers,
    },
    ...rest,
  })

  const isJson = response.headers.get('content-type')?.includes('application/json')
  const payload = isJson ? await response.json() : null

  if (!response.ok || (payload && payload.success === false)) {
    throw new Error(payload?.message || '请求失败')
  }

  return payload?.data
}

export async function uploadFile(file, scene = 'general') {
  const formData = new FormData()
  formData.append('file', file)
  const response = await fetch(`${baseUrl}/visitor/upload?scene=${encodeURIComponent(scene)}`, {
    method: 'POST',
    body: formData,
  })
  const payload = await response.json()
  if (!response.ok || payload.success === false) {
    throw new Error(payload.message || '上传失败')
  }
  return payload.data
}

export function searchVisitors(keyword) {
  return request(`/visitor/search?q=${encodeURIComponent(keyword)}`)
}

export function registerVisitor(body) {
  return request('/visitor/register', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function createCargoEntry(body) {
  return request('/cargo/entries', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function queryVisitors(body) {
  return request('/visitor/query', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}

export function fetchResult(bizId, accessToken) {
  return request(`/visitor/result/${bizId}?accessToken=${encodeURIComponent(accessToken)}`, {
    skipAuth: true,
  })
}

export function fetchApproveDetail(bizId) {
  return request(`/visitor/approve-detail/${bizId}`)
}

export async function authByDingTalk(authCode) {
  const data = await request('/visitor/auth', {
    method: 'POST',
    body: JSON.stringify({ authCode }),
  })
  setVisitorAccessToken(data?.accessToken || '')
  return data
}

export function approveVisitor(body) {
  return request('/visitor/approve', {
    method: 'POST',
    body: JSON.stringify(body),
  })
}
