<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { session, saveSession } from '@/stores/session'
import { isAuthError } from '@/api/client'
import { userApi, signApi, historyApi, commentApi, pointApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { auditStatusText, fmtRelative, fmtDate } from '@/utils/format'
import EmptyState from '@/components/EmptyState.vue'
import Pager from '@/components/Pager.vue'

const toast = useToast()

const TABS = [
  { key: 'profile', label: '资料' },
  { key: 'sign', label: '签到' },
  { key: 'points', label: '积分' },
  { key: 'history', label: '阅读记录' },
  { key: 'comments', label: '我的评论' }
]

const COMMENT_PAGE_SIZE = 10
const LEDGER_PAGE_SIZE = 10

const tab = ref('profile')
const state = reactive({
  loading: true,
  error: '',
  user: null,
  sign: { year: 0, month: 0, days: [], count: 0, todaySigned: false },
  continuous: 0,
  history: [],
  /* 后端 pageByUser 返回 {list, total}，用数组接会把空态判断做废 */
  comments: { list: [], total: 0 },
  points: {
    balance: 0,
    totalEarned: 0,
    level: 1,
    levelName: '',
    nextLevelAt: null,
    nextLevelName: '',
    todayEarned: 0
  },
  ledger: { list: [], total: 0 },
  rules: []
})

const commentsPage = ref(1)
const commentsLoading = ref(false)
const ledgerPage = ref(1)
const ledgerLoading = ref(false)
/* 明细翻页请求的序号，切页太快时只认最后一次的响应 */
let ledgerSeq = 0

const points = computed(() => state.points)

/* 满级时 nextLevelAt 是 null，进度条按满格显示 */
const levelProgress = computed(() => {
  const { totalEarned, nextLevelAt } = state.points
  if (!nextLevelAt) return 100
  return Math.min(100, Math.round((Number(totalEarned || 0) / nextLevelAt) * 100))
})

const toNextLevel = computed(() => {
  const { totalEarned, nextLevelAt } = state.points
  return nextLevelAt ? Math.max(0, nextLevelAt - Number(totalEarned || 0)) : 0
})

const profile = reactive({ nickName: '', phone: '', email: '' })
const password = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const profileBusy = ref(false)
/* 刚签到的日期，用来给那个格子一个落章动画 */
const justSigned = ref(0)
const passwordBusy = ref(false)
let signTimer = 0

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

function applyComments(data) {
  state.comments = {
    list: Array.isArray(data?.list) ? data.list : [],
    total: Number(data?.total || 0)
  }
}

async function loadComments(page = 1) {
  commentsPage.value = page
  commentsLoading.value = true
  try {
    applyComments(await commentApi.mine({ pageNum: page, pageSize: COMMENT_PAGE_SIZE }))
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '评论加载失败')
  } finally {
    commentsLoading.value = false
  }
}

function applyPoints(data) {
  if (!data) return
  state.points = { ...state.points, ...data }
  /* 余额顺手同步到 store，书籍页投票后头部不用等刷新 */
  session.points = Number(data.balance || 0)
}

async function loadPoints() {
  try {
    applyPoints(await pointApi.summary())
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '积分加载失败')
  }
}

async function loadLedger(page = 1) {
  ledgerPage.value = page
  ledgerLoading.value = true
  const seq = (ledgerSeq += 1)
  try {
    const data = await pointApi.ledger({ pageNum: page, pageSize: LEDGER_PAGE_SIZE })
    /* 快速翻页时旧响应可能后到，直接丢掉，否则页码和内容会对不上 */
    if (seq !== ledgerSeq) return
    state.ledger = {
      list: Array.isArray(data?.list) ? data.list : [],
      total: Number(data?.total || 0)
    }
  } catch (err) {
    if (seq !== ledgerSeq || isAuthError(err)) return
    toast.error(err.message || '明细加载失败')
  } finally {
    if (seq === ledgerSeq) ledgerLoading.value = false
  }
}

async function loadRules() {
  if (state.rules.length) return
  try {
    state.rules = (await pointApi.rules()) || []
  } catch {
    /* 规则表只是说明性内容，拿不到就不显示，不打断页面 */
    state.rules = []
  }
}

/* 积分页第一次打开才拉明细，进个人中心就请求一堆数据是白费流量 */
watch(tab, (key) => {
  if (key !== 'points') return
  if (!state.ledger.list.length) loadLedger(1)
  loadRules()
})

async function load() {
  state.loading = true
  state.error = ''
  try {
    const [user, sign, continuous, history, comments, points] = await Promise.all([
      userApi.info(),
      signApi.month().catch(() => null),
      signApi.continuous().catch(() => 0),
      historyApi.list().catch(() => []),
      commentApi.mine({ pageNum: 1, pageSize: COMMENT_PAGE_SIZE }).catch(() => null),
      pointApi.summary().catch(() => null)
    ])
    state.user = user
    /* 拉到的资料同步回 store，头部昵称和头像首字跟着更新 */
    if (user) saveSession(session.token, user)
    Object.assign(profile, {
      nickName: user?.nickName || '',
      phone: user?.phone || '',
      email: user?.email || ''
    })
    if (sign) state.sign = sign
    state.continuous = Number(continuous || 0)
    state.history = history || []
    commentsPage.value = 1
    applyComments(comments)
    applyPoints(points)
  } catch (err) {
    /* 401 已经由请求层统一清登录态并跳转，这里再报一次只会出现两条提示 */
    if (isAuthError(err)) return
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
    saveSession(session.token, updated)
    toast.success('资料已更新')
  } catch (err) {
    if (isAuthError(err)) return
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
    if (isAuthError(err)) return
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
      toast.success(`签到成功 +${data?.points || 0} 分，连续 ${data?.continuousDays || 0} 天`)
    }
    state.continuous = Number(data?.continuousDays || state.continuous)
    /* 接口已经带回了最新余额，先用它，避免提示里出现旧数字 */
    if (typeof data?.balance === 'number') {
      state.points.balance = data.balance
      session.points = data.balance
    }
    const month = await signApi.month().catch(() => null)
    if (month) state.sign = month
    /* 连签奖励和今日所得要重新算一次，签到响应里只有总分没有等级 */
    if (!data?.repeat) loadPoints()
    if (!data?.repeat) {
      justSigned.value = new Date().getDate()
      /* 重新盖章前先清掉上一个定时器，否则快速连签会留下几个待触发的回调 */
      if (signTimer) window.clearTimeout(signTimer)
      signTimer = window.setTimeout(() => {
        justSigned.value = 0
        signTimer = 0
      }, 1400)
    }
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '签到失败')
  }
}

onMounted(load)

onBeforeUnmount(() => {
  if (signTimer) window.clearTimeout(signTimer)
})
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
      <div class="stats">
        <button class="stat" type="button" @click="tab = 'points'">
          <span class="mono num">{{ points.balance }}</span>
          <span class="hint">积分 · {{ points.levelName }}</span>
        </button>
        <div class="stat">
          <span class="mono num">{{ state.continuous }}</span>
          <span class="hint">连续签到</span>
        </div>
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

      <section v-else-if="tab === 'points'" class="panel">
        <div class="point-card">
          <div class="point-main">
            <span class="mono pnum">{{ points.balance }}</span>
            <span class="hint">可用积分</span>
          </div>
          <div class="point-side">
            <span class="tag tag--green">{{ points.levelName || '初读' }}</span>
            <span class="hint">今日已得 {{ points.todayEarned || 0 }} 分</span>
          </div>
        </div>

        <div class="level">
          <div class="level-bar"><span :style="{ width: `${levelProgress}%` }" /></div>
          <p class="hint">
            <template v-if="points.nextLevelAt">再得 {{ toNextLevel }} 分升到「{{ points.nextLevelName }}」</template>
            <template v-else>已经是最高等级</template>
            <span class="sep">·</span>
            等级看累计获得 {{ points.totalEarned || 0 }} 分，花掉不会掉级
          </p>
        </div>

        <div class="panel-head ledger-head">
          <h2 class="serif">积分明细</h2>
          <p class="hint">每一条行为只记一次账，反复翻同一章不会重复加。</p>
        </div>

        <EmptyState v-if="!state.ledger.list.length" title="还没有积分记录" desc="签到、读完一章、评论过审都会加分。" />

        <ul v-else class="rows">
          <li v-for="item in state.ledger.list" :key="item.id" class="lrow">
            <span class="ltype">{{ item.bizLabel }}</span>
            <span class="lremark">{{ item.remark }}</span>
            <span class="lpoints mono" :class="item.points >= 0 ? 'is-plus' : 'is-minus'">
              {{ item.points >= 0 ? `+${item.points}` : item.points }}
            </span>
            <span class="rtime">{{ fmtRelative(item.createTime) }}</span>
          </li>
        </ul>

        <div class="ledger-pager" :class="{ 'is-loading': ledgerLoading }">
          <Pager
            :page="ledgerPage"
            :page-size="LEDGER_PAGE_SIZE"
            :total="state.ledger.total"
            @change="loadLedger"
          />
        </div>

        <details v-if="state.rules.length" class="rules">
          <summary>怎么赚分</summary>
          <ul>
            <li v-for="rule in state.rules" :key="rule.code">
              <span class="rlabel">{{ rule.label }}</span>
              <span class="mono rpoints">{{ rule.points }} 分</span>
              <span class="hint">{{ rule.dailyCap ? `每天最多 ${rule.dailyCap} 次` : '' }}</span>
            </li>
          </ul>
        </details>
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

        <EmptyState v-if="!state.comments.list.length" title="还没有发过评论" desc="在作品页可以留下第一句。" />

        <ul v-else class="rows">
          <li v-for="item in state.comments.list" :key="item.id" class="comment">
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

        <div class="comments-pager" :class="{ 'is-loading': commentsLoading }">
          <Pager
            :page="commentsPage"
            :page-size="COMMENT_PAGE_SIZE"
            :total="state.comments.total"
            @change="loadComments"
          />
        </div>
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

.stats {
  display: flex;
  align-items: center;
  gap: var(--s-6);
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.stat .num {
  font-size: var(--t-xl);
  line-height: 1.2;
}

/* 积分那块可以点，点进去看明细；只给数字加悬停反馈，不用整块变色 */
button.stat:hover .num {
  color: var(--sage-deep);
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

.point-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-5);
  max-width: 620px;
  padding: var(--s-5);
  border: 1px solid var(--line);
  border-radius: var(--r-l);
  background: var(--paper-warm);
}

.point-main {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
}

.pnum {
  font-size: var(--t-2xl);
  line-height: 1.1;
}

.point-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--s-2);
}

.level {
  max-width: 620px;
  margin-top: var(--s-4);
}

.level-bar {
  height: 4px;
  border-radius: var(--r-pill);
  background: var(--surface-sunken);
  overflow: hidden;
}

.level-bar > span {
  display: block;
  height: 100%;
  background: var(--sage);
  transition: width var(--dur-2) var(--ease);
}

.level .hint {
  margin-top: var(--s-2);
}

.sep {
  margin: 0 6px;
  color: var(--line-strong);
}

.ledger-head {
  margin-top: var(--s-7);
}

/* 明细一行四项：来源、说明、分值、时间。说明占伸缩位，长了截断 */
.lrow {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  padding: 11px 2px;
  border-bottom: 1px solid var(--line);
}

.ltype {
  font-size: var(--t-sm);
  color: var(--ink);
  white-space: nowrap;
}

.lremark {
  min-width: 0;
  font-size: var(--t-sm);
  color: var(--ink-3);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.lpoints {
  margin-left: auto;
  font-size: var(--t-base);
  white-space: nowrap;
}

.lpoints.is-plus {
  color: var(--sage-deep);
}

.lpoints.is-minus {
  color: var(--pale-red-ink);
}

.ledger-pager.is-loading {
  opacity: 0.6;
  pointer-events: none;
}

.rules {
  max-width: 620px;
  margin-top: var(--s-6);
  padding-top: var(--s-4);
  border-top: 1px solid var(--line);
}

.rules summary {
  cursor: pointer;
  font-size: var(--t-sm);
  color: var(--ink-2);
}

.rules ul {
  margin-top: var(--s-3);
}

.rules li {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  padding: 6px 0;
  font-size: var(--t-sm);
}

.rlabel {
  min-width: 96px;
  color: var(--ink-2);
}

.rpoints {
  color: var(--ink);
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

/* 翻页请求还在路上时先挡住第二次点击，避免两个响应互相覆盖 */
.comments-pager.is-loading {
  opacity: 0.6;
  pointer-events: none;
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
