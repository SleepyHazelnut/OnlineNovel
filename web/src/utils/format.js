const THEME_TONES = [
  { base: '#3c3489', ink: '#26215c', light: '#afa9ec', on: '#eeedfe', deep: true },
  { base: '#0f6e56', ink: '#04342c', light: '#5dcaa5', on: '#e1f5ee', deep: true },
  { base: '#993c1d', ink: '#4a1b0c', light: '#f0997b', on: '#faece7', deep: true },
  { base: '#2c2c2a', ink: '#161615', light: '#888780', on: '#f1efe8', deep: true },
  { base: '#185fa5', ink: '#042c53', light: '#85b7eb', on: '#e6f1fb', deep: true },
  { base: '#854f0b', ink: '#412402', light: '#fac775', on: '#faeeda', deep: true },
  { base: '#f4c0d1', ink: '#993556', light: '#d4537e', on: '#4b1528', deep: false },
  { base: '#c0dd97', ink: '#3b6d11', light: '#639922', on: '#173404', deep: false },
  { base: '#b5d4f4', ink: '#185fa5', light: '#378add', on: '#042c53', deep: false },
  { base: '#fac775', ink: '#854f0b', light: '#ba7517', on: '#412402', deep: false }
]

const CATEGORY_TONE = {
  玄幻: 0,
  奇幻: 0,
  仙侠: 1,
  武侠: 1,
  都市: 2,
  现实: 2,
  悬疑: 3,
  灵异: 3,
  科幻: 4,
  末世: 4,
  历史: 5,
  军事: 5,
  言情: 6,
  青春: 6,
  游戏: 7,
  竞技: 7,
  轻小说: 8,
  二次元: 8,
  短篇: 9
}

function hashOf(text) {
  const key = String(text || '')
  let sum = 0
  for (let i = 0; i < key.length; i += 1) sum = (sum * 31 + key.charCodeAt(i)) % 9973
  return sum
}

export function coverTone(name, categoryName) {
  const matched = Object.keys(CATEGORY_TONE).find((key) => String(categoryName || '').includes(key))
  const index = matched ? CATEGORY_TONE[matched] : hashOf(name) % THEME_TONES.length
  const tone = THEME_TONES[index]
  return { ...tone, variant: hashOf(name + (categoryName || '')) % 5 }
}

export function fmtCount(n) {
  const v = Number(n || 0)
  if (v >= 10000) return `${(v / 10000).toFixed(1)} 万`
  return String(v)
}

export function fmtTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

export function fmtDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 10)
}

export function fmtRelative(value) {
  if (!value) return '-'
  const time = new Date(String(value).replace(' ', 'T')).getTime()
  if (Number.isNaN(time)) return fmtDate(value)
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  if (diff < 30 * 86_400_000) return `${Math.floor(diff / 86_400_000)} 天前`
  return fmtDate(value)
}

export function scoreText(score) {
  return Number(score) > 0 ? Number(score).toFixed(1) : '暂无评分'
}

export function bookStatusText(status) {
  return Number(status) === 1 ? '已完结' : '连载中'
}

export const AUDIT_STATUS = {
  0: ['未提交', 'gray'],
  1: ['待审核', 'yellow'],
  2: ['已通过', 'green'],
  3: ['已驳回', 'red'],
  4: ['已下架', 'gray']
}

export function auditStatusText(status) {
  return AUDIT_STATUS[Number(status)]?.[0] || '未知'
}
