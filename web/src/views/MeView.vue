<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { auth } from '@/api/client'
import { userApi, signApi, historyApi, commentApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { auditStatusText, fmtRelative, fmtDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'

const toast = useToast()

const TABS = [
  { key: 'profile', label: '资料' },
  { key: 'sign', label: '签到' },
  { key: 'history', label: '阅读记录' },
  { key: 'comments', label: '我的评论' }
]

const tab = ref('profile')
const state = reactive({
  loading: true,
  error: '',
  user: null,
  sign: { year: 0, month: 0, days: [], count: 0, todaySigned: false },
  continuous: 0,
  history: [],
  comments: []
})

const profile = reactive({ nickName: '', phone: '', email: '' })
const password = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const profileBusy = ref(false)
/* 刚签到的日期，用来给那个格子一个落章动画 */
const justSigned = ref(0)
const passwordBusy = ref(false)

const avatarText = computed(() => (state.user?.nickName || state.user?.username || '读').slice(0, 1))

const calendar = computed(() => {
  const { year, month, days } = state.sign
  if (!year || !month) return []
  const first = new Date(year, month - 1, 1)
  const offset = first.getDay()
  const total = new Date(year, month, 0).getDate()
  const signed = new Set(days || [])
  const cells = []
  for (let i = 0; i < offset; i += 1) cells.push({ key: `pad-${i}`, day: null })
  for (let day = 1; day <= total; day += 1) cells.push({ key: `d-${day}`, day, signed: signed.has(day) })
  return cells
})

const monthTitle = computed(() => (state.sign.year ? `${state.sign.year} 年 ${state.sign.month} 月` : '本月'))

async function load() {
  state.loading = true
  state.error = ''
  try {
    const [user, sign, continuous, history, comments] = await Promise.all([
      userApi.info(),
      signApi.month().catch(() => null),
      signApi.continuous().catch(() => 0),
      historyApi.list().catch(() => []),
      commentApi.mine().catch(() => [])
    ])
    state.user = user
    Object.assign(profile, {
      nickName: user?.nickName || '',
      phone: user?.phone || '',
      email: user?.email || ''
    })
    if (sign) state.sign = sign
    state.continuous = Number(continuous || 0)
    state.history = history || []
    state.comments = comments || []
  } catch (err) {
    state.error = err.message || '加载失败'
  } finally {
    state.loading = false
  }
}

async function saveProfile() {
  if (!profile.nickName.trim()) {
    toast.error('昵称不能为空')
    return
  }
  profileBusy.value = true
  try {
    const updated = await userApi.updateProfile({
      nickName: profile.nickName.trim(),
      phone: profile.phone.trim(),
      email: profile.email.trim(),
      avatar: state.user?.avatar || ''
    })
    state.user = updated
    auth.save(auth.token(), updated)
    toast.success('资料已更新')
  } catch (err) {
    toast.error(err.message || '保存失败')
  } finally {
    profileBusy.value = false
  }
}

async function savePassword() {
  if (!password.oldPassword || !password.newPassword) {
    toast.error('请填写完整')
    return
  }
  if (password.newPassword !== password.confirm) {
    toast.error('两次输入的新密码不一致')
    return
  }
  passwordBusy.value = true
  try {
    await userApi.changePassword({
      oldPassword: password.oldPassword,
      newPassword: password.newPassword
    })
    password.oldPassword = ''
    password.newPassword = ''
    password.confirm = ''
    toast.success('密码已修改')
  } catch (err) {
    toast.error(err.message || '修改失败')
  } finally {
    passwordBusy.value = false
  }
}

async function doSign() {
  try {
    const data = await signApi.signIn()
    if (data?.repeat) {
      toast.info('今天已经签过了')
    } else {
      toast.success(`签到成功，连续 ${data?.continuousDays || 0} 天`)
    }
    state.continuous = Number(data?.continuousDays || state.continuous)
    const month = await signApi.month().catch(() => null)
    if (month) state.sign = month
    if (!data?.repeat) {
      justSigned.value = new Date().getDate()
      window.setTimeout(() => {
        justSigned.value = 0
      }, 1400)
    }
  } catch (err) {
    toast.error(err.message || '签到失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="me container">
    <header class="head">
      <div class="id">
        <div class="avatar">{{ avatarText }}</div>
        <div>
          <h1 class="serif">{{ state.user?.nickName || state.user?.username || '读者' }}</h1>
          <p class="joined">加入于 {{ fmtDate(state.user?.createTime) }}</p>
        </div>
      </div>
      <div class="streak">
        <span class="mono num">{{ state.continuous }}</span>
        <span class="hint">连续签到</span>
      </div>
    </header>

    <nav class="tabs" role="tablist">
      <button
        v-for="item in TABS"
        :key="item.key"
        class="tab"
        :class="{ 'is-on': tab === item.key }"
        type="button"
        role="tab"
        :aria-selected="tab === item.key"
        @click="tab = item.key"
      >
        {{ item.label }}
      </button>
    </nav>

    <div v-if="state.loading" class="loading">
      <div v-for="i in 4" :key="i" class="skeleton line" />
    </div>

    <div v-else-if="state.error" class="error">
      <span>{{ state.error }}</span>
      <button class="btn btn--sm" type="button" @click="load">重试</button>
    </div>

    <template v-else>
      <section v-if="tab === 'profile'" class="panel">
        <div class="panel-head">
          <h2 class="serif">基本资料</h2>
          <p class="hint">用户名不可修改，昵称会显示在评论里。</p>
        </div>

        <form class="form" @submit.prevent="saveProfile">
          <div class="form-row">
            <label class="label" for="nick">昵称</label>
            <input id="nick" v-model="profile.nickName" class="field" maxlength="50" />
          </div>
          <div class="form-row two">
            <div>
              <label class="label" for="phone">手机号</label>
              <input id="phone" v-model="profile.phone" class="field" placeholder="选填" />
            </div>
            <div>
              <label class="label" for="email">邮箱</label>
              <input id="email" v-model="profile.email" class="field" placeholder="选填" />
            </div>
          </div>
          <div class="form-row">
            <span class="label">用户名</span>
            <p class="readonly mono">{{ state.user?.username }}</p>
          </div>
          <button class="btn btn--solid" type="submit" :disabled="profileBusy">
            {{ profileBusy ? '保存中' : '保存资料' }}
          </button>
        </form>

        <hr class="hairline split" />

        <div class="panel-head">
          <h2 class="serif">修改密码</h2>
          <p class="hint">改完需要重新登录。</p>
        </div>

        <form class="form" @submit.prevent="savePassword">
          <div class="form-row">
            <label class="label" for="old">当前密码</label>
            <input id="old" v-model="password.oldPassword" class="field" type="password" autocomplete="current-password" />
          </div>
          <div class="form-row two">
            <div>
              <label class="label" for="new">新密码</label>
              <input id="new" v-model="password.newPassword" class="field" type="password" autocomplete="new-password" />
            </div>
            <div>
              <label class="label" for="confirm">确认新密码</label>
              <input
                id="confirm"
                v-model="password.confirm"
                class="field"
                type="password"
                autocomplete="new-password"
              />
            </div>
          </div>
          <button class="btn" type="submit" :disabled="passwordBusy">
            {{ passwordBusy ? '修改中' : '修改密码' }}
          </button>
        </form>
      </section>

      <section v-else-if="tab === 'sign'" class="panel">
        <div class="panel-head">
          <h2 class="serif">{{ monthTitle }}</h2>
          <p class="hint">本月已签 {{ state.sign.count || 0 }} 天</p>
        </div>

        <div class="week">
          <span v-for="d in ['日', '一', '二', '三', '四', '五', '六']" :key="d">{{ d }}</span>
        </div>
        <div class="calendar">
          <span
            v-for="cell in calendar"
            :key="cell.key"
            class="day mono"
            :class="{ 'is-empty': !cell.day, 'is-signed': cell.signed, 'is-new': cell.day === justSigned }"
          >
            {{ cell.day || '' }}
          </span>
        </div>

        <div class="sign-actions">
          <button class="btn btn--solid" type="button" :disabled="state.sign.todaySigned" @click="doSign">
            {{ state.sign.todaySigned ? '今天已签到' : '签到' }}
          </button>
          <span class="hint">连续 {{ state.continuous }} 天，中断后从 1 重新计。</span>
        </div>
      </section>

      <section v-else-if="tab === 'history'" class="panel">
        <div class="panel-head">
          <h2 class="serif">阅读记录</h2>
          <p class="hint">按最近阅读排序</p>
        </div>

        <EmptyState v-if="!state.history.length" title="还没有阅读记录" desc="打开任意一章，进度会自动记下来。">
          <RouterLink to="/library" class="btn btn--sm">去书库</RouterLink>
        </EmptyState>

        <ul v-else class="rows">
          <li v-for="item in state.history" :key="item.id">
            <RouterLink :to="`/read/${item.bookId}/${item.chapterId}`" class="row">
              <span class="rname">{{ item.bookName || `作品 ${item.bookId}` }}</span>
              <span class="rmeta">读到第 {{ item.chapterNum || '—' }} 章</span>
              <span class="rtime">{{ fmtRelative(item.updateTime) }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section v-else class="panel">
        <div class="panel-head">
          <h2 class="serif">我的评论</h2>
          <p class="hint">待审核的评论只有自己可见</p>
        </div>

        <EmptyState v-if="!state.comments.length" title="还没有发过评论" desc="在作品页可以留下第一句。" />

        <ul v-else class="rows">
          <li v-for="item in state.comments" :key="item.id" class="comment">
            <div class="row-head">
              <RouterLink :to="`/books/${item.bookId}`" class="rname">{{ item.bookName || `作品 ${item.bookId}` }}</RouterLink>
              <span class="tag" :class="`tag--${item.auditStatus === 2 ? 'green' : item.auditStatus === 3 ? 'red' : 'yellow'}`">
                {{ auditStatusText(item.auditStatus) }}
              </span>
              <span class="rtime">{{ fmtRelative(item.createTime) }}</span>
            </div>
            <p class="ctext">{{ item.content }}</p>
            <p v-if="item.rejectReason" class="reject">驳回原因：{{ item.rejectReason }}</p>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.me {
  padding-top: var(--s-7);
  max-width: 860px;
  min-height: 60vh;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-5);
  padding-bottom: var(--s-5);
  border-bottom: 1px solid var(--line);
}

.id {
  display: flex;
  align-items: center;
  gap: var(--s-4);
}

.avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--sage-soft);
  color: var(--sage-deep);
  font-size: var(--t-lg);
}

.head h1 {
  font-size: var(--t-xl);
}

.joined {
  margin-top: 2px;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.streak {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.streak .num {
  font-size: var(--t-xl);
  line-height: 1.2;
}

.tabs {
  display: flex;
  gap: var(--s-5);
  margin-top: var(--s-5);
  border-bottom: 1px solid var(--line);
}

.tab {
  padding: 0 2px 12px;
  margin-bottom: -1px;
  font-size: var(--t-base);
  color: var(--ink-3);
  border-bottom: 1px solid transparent;
  transition: color var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease);
}

.tab:hover {
  color: var(--ink);
}

.tab.is-on {
  color: var(--ink);
  border-bottom-color: var(--ink);
}

.panel {
  padding-top: var(--s-6);
}

.panel-head {
  margin-bottom: var(--s-5);
}

.panel-head h2 {
  font-size: var(--t-lg);
}

.panel-head .hint {
  margin-top: 6px;
}

.form {
  max-width: 520px;
}

.form-row.two {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--s-4);
}

.readonly {
  padding: 9px 0;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.split {
  margin-block: var(--s-7);
}

.week,
.calendar {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  max-width: 420px;
}

.week span {
  text-align: center;
  font-size: var(--t-xs);
  color: var(--ink-3);
  padding-bottom: 6px;
}

.day {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--r-s);
  font-size: var(--t-sm);
  color: var(--ink-2);
  background: var(--surface);
  border: 1px solid var(--line);
  transition: background-color var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease),
    color var(--dur-1) var(--ease);
}

.day.is-empty {
  background: transparent;
  border-color: transparent;
}

.day.is-signed {
  background: var(--sage);
  border-color: var(--sage);
  color: var(--ink-inverse);
}

/* 签到当天像盖下一枚章：轻微回弹，不位移其它格子 */
.day.is-signed.is-new {
  animation: stamp-in 460ms var(--spring) both;
}

@keyframes stamp-in {
  0% {
    opacity: 0;
    transform: scale(0.55);
  }
  55% {
    opacity: 1;
    transform: scale(1.08);
  }
  100% {
    opacity: 1;
    transform: scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .day.is-signed.is-new {
    animation: none;
  }
}

.sign-actions {
  display: flex;
  align-items: center;
  gap: var(--s-4);
  margin-top: var(--s-6);
}

.rows {
  max-width: 620px;
}

.row {
  display: flex;
  align-items: baseline;
  gap: var(--s-4);
  padding: 12px 2px;
  border-bottom: 1px solid var(--line);
  transition: background var(--dur-1) var(--ease);
}

.row:hover {
  background: var(--paper-warm);
}

.rname {
  font-size: var(--t-base);
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.rmeta {
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.rtime {
  margin-left: auto;
  font-size: var(--t-xs);
  color: var(--ink-3);
  white-space: nowrap;
}

.comment {
  padding: var(--s-4) 2px;
  border-bottom: 1px solid var(--line);
}

.row-head {
  display: flex;
  align-items: center;
  gap: var(--s-3);
}

.ctext {
  margin-top: 8px;
  font-size: var(--t-base);
  line-height: 1.9;
  color: var(--ink-2);
  white-space: pre-wrap;
}

.reject {
  margin-top: 6px;
  font-size: var(--t-xs);
  color: var(--pale-red-ink);
}

.loading {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: var(--s-5);
}

.line {
  height: 16px;
  width: 60%;
}

.error {
  display: flex;
  align-items: center;
  gap: var(--s-4);
  margin-top: var(--s-5);
  padding: var(--s-3) var(--s-4);
  border-radius: var(--r-m);
  background: var(--pale-red);
  color: var(--pale-red-ink);
  font-size: var(--t-sm);
}

@media (max-width: 560px) {
  .form-row.two {
    grid-template-columns: 1fr;
  }

  .head {
    align-items: flex-start;
  }
}
</style>
