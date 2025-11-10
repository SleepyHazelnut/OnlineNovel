import { session, clearSession } from '@/stores/session'

/* 网关模式下整个 SPA 挂在 /app/ 下，所有整页跳转都得带这个前缀，否则刷新即 404 */
const BASE = import.meta.env.BASE_URL || '/'

export function withBase(path) {
  if (!path.startsWith('/')) return path
  if (BASE === '/') return path
  return `${BASE.replace(/\/$/, '')}${path}`
}

/* 路由内部的 fullPath 不带 base；万一外部传进来带了，去掉后再交给 vue-router 解析 */
export function stripBase(path) {
  if (!path || BASE === '/') return path || '/'
  const root = BASE.replace(/\/$/, '')
  if (path === root) return '/'
  if (path.startsWith(`${root}/`)) return path.slice(root.length)
  return path
}

/* 业务请求统一 10 秒：慢接口不该一直占着 loading，也不该让翻章卡死 */
const TIMEOUT = 10000

function timeoutSignal(ms) {
  if (typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function') {
    return { signal: AbortSignal.timeout(ms), cancel: () => {} }
  }
  const controller = new AbortController()
  const timer = window.setTimeout(() => {
    controller.abort(new DOMException('请求超时', 'TimeoutError'))
  }, ms)
  return { signal: controller.signal, cancel: () => window.clearTimeout(timer) }
}

function composeSignal(signal) {
  const timeout = timeoutSignal(TIMEOUT)
  if (!signal) return timeout
  /* 老浏览器没有 AbortSignal.any，只能放弃调用方的取消能力，至少保住超时 */
  if (typeof AbortSignal.any === 'function') {
    return { signal: AbortSignal.any([signal, timeout.signal]), cancel: timeout.cancel }
  }
  return timeout
}

/* main.js 注入，避免 client 与 router 互相 import */
let router = null
let redirecting = false

export function setRouter(instance) {
  router = instance
}

function goLogin() {
  if (redirecting) return
  const current = router?.currentRoute.value
  /* 已经在登录页就不再跳，挡掉同一批并发请求的后续 401 */
  if (current?.name === 'login') return
  redirecting = true
  const redirect = current?.fullPath || stripBase(location.pathname + location.search)
  const query = redirect && redirect !== '/' ? { redirect } : {}

  if (router) {
    router.replace({ name: 'login', query }).finally(() => {
      redirecting = false
    })
    return
  }
  /* 没有 router（例如独立使用 api 层）时退回整页跳转，这里的路径必须带 base */
  const target = `${withBase('/login')}${redirect && redirect !== '/' ? `?redirect=${encodeURIComponent(redirect)}` : ''}`
  location.href = target
}

export function isAuthError(err) {
  return err?.code === 401
}

async function doRequest(path, options, signal) {
  const headers = {}
  if (session.token) headers.Authorization = `Bearer ${session.token}`

  const opt = { method: options.method || 'GET', headers, signal }
  if (options.body !== undefined) {
    if (options.body instanceof FormData) {
      opt.body = options.body
    } else {
      headers['Content-Type'] = 'application/json'
      opt.body = JSON.stringify(options.body)
    }
  }

  let resp
  try {
    resp = await fetch(path, opt)
  } catch (err) {
    if (options.signal?.aborted) throw err
    if (err?.name === 'TimeoutError' || err?.name === 'AbortError') {
      throw new Error('请求超时，请稍后再试')
    }
    throw new Error('网络连接失败，请检查后重试')
  }

  if (resp.status === 401) {
    clearSession()
    goLogin()
    const err = new Error('登录状态已失效')
    err.code = 401
    throw err
  }

  let data
  try {
    data = await resp.json()
  } catch {
    throw new Error(`服务返回异常（HTTP ${resp.status}）`)
  }

  if (data.code !== 200) {
    throw new Error(data.msg || '操作失败')
  }
  return data.data
}

async function request(path, options = {}) {
  const composed = composeSignal(options.signal)
  try {
    return await doRequest(path, options, composed.signal)
  } finally {
    composed.cancel()
  }
}

export function get(path, params, options = {}) {
  if (params) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== null && v !== undefined && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&')
    if (qs) path += (path.includes('?') ? '&' : '?') + qs
  }
  return request(path, options)
}

export function post(path, body, options = {}) {
  return request(path, { ...options, method: 'POST', body: body ?? {} })
}

export function put(path, body, options = {}) {
  return request(path, { ...options, method: 'PUT', body: body ?? {} })
}

export function del(path, options = {}) {
  return request(path, { ...options, method: 'DELETE' })
}

export function upload(path, file, options = {}) {
  const form = new FormData()
  form.append('file', file)
  return request(path, { ...options, method: 'POST', body: form })
}
