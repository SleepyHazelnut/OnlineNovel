const ROLES = {
  reader: { token: 'inkisle_token', user: 'inkisle_user', login: '/login' },
  author: { token: 'inkisle_author_token', user: 'inkisle_author', login: '/author/login' },
  admin: { token: 'inkisle_admin_token', user: 'inkisle_admin', login: '/admin/login' }
}

let currentRole = 'reader'

export function setRole(role) {
  currentRole = ROLES[role] ? role : 'reader'
}

export function roleOf(path) {
  if (path.startsWith('/admin')) return 'admin'
  if (path.startsWith('/author')) return 'author'
  return 'reader'
}

export const auth = {
  role: () => currentRole,
  token: () => localStorage.getItem(ROLES[currentRole].token),
  user() {
    try {
      return JSON.parse(localStorage.getItem(ROLES[currentRole].user) || 'null')
    } catch {
      return null
    }
  },
  save(token, user) {
    const r = ROLES[currentRole]
    localStorage.setItem(r.token, token)
    localStorage.setItem(r.user, JSON.stringify(user || null))
  },
  clear() {
    const r = ROLES[currentRole]
    localStorage.removeItem(r.token)
    localStorage.removeItem(r.user)
  },
  logged() {
    return Boolean(this.token())
  },
  logout() {
    const login = ROLES[currentRole].login
    this.clear()
    location.href = login
  }
}

async function request(path, options = {}) {
  const role = options.role || currentRole
  const headers = {}
  const token = localStorage.getItem(ROLES[role].token)
  if (token) headers.Authorization = `Bearer ${token}`

  const opt = { method: options.method || 'GET', headers }
  if (options.body !== undefined) {
    if (options.body instanceof FormData) {
      opt.body = options.body
    } else {
      headers['Content-Type'] = 'application/json'
      opt.body = JSON.stringify(options.body)
    }
  }

  const resp = await fetch(path, opt)

  if (resp.status === 401) {
    localStorage.removeItem(ROLES[role].token)
    localStorage.removeItem(ROLES[role].user)
    const back = encodeURIComponent(location.pathname + location.search)
    location.href = `${ROLES[role].login}?redirect=${back}`
    throw new Error('请先登录')
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

export function get(path, params) {
  if (params) {
    const qs = Object.entries(params)
      .filter(([, v]) => v !== null && v !== undefined && v !== '')
      .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
      .join('&')
    if (qs) path += (path.includes('?') ? '&' : '?') + qs
  }
  return request(path)
}

export function post(path, body) {
  return request(path, { method: 'POST', body: body ?? {} })
}

export function put(path, body) {
  return request(path, { method: 'PUT', body: body ?? {} })
}

export function del(path) {
  return request(path, { method: 'DELETE' })
}

export function upload(path, file) {
  const form = new FormData()
  form.append('file', file)
  return request(path, { method: 'POST', body: form })
}
