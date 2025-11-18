import { reactive } from 'vue'

/* token 的 key 沿用旧名，后端/脚本或用户已登录的浏览器不用清缓存也能接上 */
const TOKEN_KEY = 'inkisle_token'
const USER_KEY = 'inkisle_user'

/* 全站唯一的一份登录态。组件直接读这里，改昵称后头部不用等路由变化就能刷新 */
/* points 只放内存：余额随时会被签到、投票改掉，落 localStorage 反而容易和接口对不上 */
export const session = reactive({ token: '', user: null, points: null })

function read(key) {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/* 隐私模式下 localStorage 读写都会抛错：登录本身应该成功，只是刷新后需要重新登录 */
export function hydrate() {
  let user = null
  try {
    user = JSON.parse(read(USER_KEY) || 'null')
  } catch {
    user = null
  }
  session.token = read(TOKEN_KEY) || ''
  session.user = user
}

export function saveSession(token, user) {
  session.token = token || ''
  session.user = user || null
  try {
    if (session.token) localStorage.setItem(TOKEN_KEY, session.token)
    else localStorage.removeItem(TOKEN_KEY)
    localStorage.setItem(USER_KEY, JSON.stringify(session.user))
  } catch (err) {
    console.warn('[session] 本地存储不可用，刷新后需要重新登录', err)
  }
}

/* 401 清理与主动登出共用这一份，避免两处各删一半留下残状态 */
export function clearSession() {
  session.token = ''
  session.user = null
  try {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  } catch (err) {
    console.warn('[session] 本地存储清理失败', err)
  }
}
