<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { bookApi, chapterApi, historyApi } from '@/api'
import { session } from '@/stores/session'
import { isAuthError } from '@/api/client'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const THEMES = [
  { key: 'paper', label: '纸白' },
  { key: 'sepia', label: '米黄' },
  { key: 'night', label: '夜间' }
]

const PREFS_KEY = 'inkisle_reader_prefs'

function loadPrefs() {
  try {
    const saved = JSON.parse(localStorage.getItem(PREFS_KEY) || '{}')
    return { size: Number(saved.size) || 18, theme: THEMES.some((t) => t.key === saved.theme) ? saved.theme : 'paper' }
  } catch {
    return { size: 18, theme: 'paper' }
  }
}

const prefs = reactive(loadPrefs())
const state = reactive({
  loading: true,
  /* 翻章期间的过渡态：锁住翻页按钮，同时给顶部的不确定进度条一个开关 */
  switching: false,
  error: '',
  book: null,
  chapters: [],
  chapter: null,
  paragraphs: [],
  prevId: null,
  nextId: null
})

const catalogOpen = ref(false)
const fontOpen = ref(false)
/* 翻章方向，决定章节内容从哪一侧进入 */
const turn = ref('jump')

const bookId = computed(() => Number(route.params.bookId))
const chapterIndex = computed(() => {
  if (!state.chapter) return -1
  return state.chapters.findIndex((item) => item.id === state.chapter.id)
})
const progress = computed(() => {
  const total = state.chapters.length
  if (!total || chapterIndex.value < 0) return 0
  return Math.round(((chapterIndex.value + 1) / total) * 100)
})
const themeLabel = computed(() => THEMES.find((t) => t.key === prefs.theme)?.label || '纸白')

watch(
  prefs,
  (value) => {
    localStorage.setItem(PREFS_KEY, JSON.stringify({ size: value.size, theme: value.theme }))
  },
  { deep: true }
)

let bookSeq = 0
let chapterSeq = 0
let chapterRequest = null

async function loadBook() {
  const current = ++bookSeq
  state.loading = true
  state.error = ''
  try {
    const [chapters, book] = await Promise.all([
      chapterApi.list(bookId.value),
      bookApi.detail(bookId.value).catch(() => null)
    ])
    if (current !== bookSeq) return
    state.book = book
    state.chapters = chapters || []
    if (!state.chapters.length) {
      state.error = '这部作品还没有发布章节'
      return
    }
    const target = Number(route.params.chapterId) || state.chapters[0].id
    await loadChapter(target)
  } catch (err) {
    if (current !== bookSeq) return
    if (isAuthError(err)) return
    state.error = err.message || '加载失败'
  } finally {
    if (current === bookSeq) state.loading = false
  }
}

async function loadChapter(id) {
  const current = ++chapterSeq
  /* 上一次翻章还没回来就切了，先取消，避免慢响应把新章节盖回旧的 */
  chapterRequest?.abort()
  const controller = new AbortController()
  chapterRequest = controller

  /* 只有第一次拿章节时才铺骨架，翻章期间保留当前章节，避免闪一下占位 */
  const firstLoad = !state.chapter
  if (firstLoad) state.loading = true
  state.switching = true
  state.error = ''
  try {
    const data = await chapterApi.read(id, { signal: controller.signal })
    if (current !== chapterSeq) return
    state.chapter = data?.chapter || null
    state.prevId = data?.prevId || null
    state.nextId = data?.nextId || null
    state.paragraphs = String(data?.content || '')
      .split(/\n+/)
      .map((line) => line.trim())
      .filter(Boolean)
    saveHistory()
  } catch (err) {
    if (current !== chapterSeq || controller.signal.aborted) return
    if (isAuthError(err)) return
    state.error = err.message || '这一章打不开'
  } finally {
    if (current === chapterSeq) {
      state.loading = false
      state.switching = false
    }
  }
}

/* 新章节进场前回到顶部，避免沿用上一章的滚动位置 */
function resetScroll() {
  window.scrollTo({ top: 0, behavior: 'auto' })
}

function saveHistory() {
  if (!state.chapter || !session.token) return
  historyApi
    .save({
      bookId: bookId.value,
      chapterId: state.chapter.id,
      chapterNum: state.chapter.chapterNum
    })
    .catch(() => {})
}

function goChapter(id, direction = 0) {
  if (!id || state.switching) return
  /* 已经在读这一章就什么都不做，push 同一 URL 会被路由去重，用户只会觉得点击失灵 */
  if (state.chapter && Number(id) === state.chapter.id) return
  turn.value = direction < 0 ? 'back' : direction > 0 ? 'next' : 'jump'
  catalogOpen.value = false
  /* 同步锁住按钮：等路由 watcher 触发再锁就晚了，第二次点击会落到还没更新的 nextId 上 */
  state.switching = true
  router
    .push(`/read/${bookId.value}/${id}`)
    .then((failure) => {
      /* 路由被判重/取消时不会有 watcher 来解锁，得自己放开 */
      if (failure) state.switching = false
    })
    .catch(() => {
      state.switching = false
    })
}

function step(direction) {
  goChapter(direction < 0 ? state.prevId : state.nextId, direction)
}

function changeSize(delta) {
  prefs.size = Math.min(26, Math.max(15, prefs.size + delta))
}

function cycleTheme() {
  const index = THEMES.findIndex((t) => t.key === prefs.theme)
  prefs.theme = THEMES[(index + 1) % THEMES.length].key
}

function onKey(event) {
  if (event.target instanceof HTMLElement && /INPUT|TEXTAREA/.test(event.target.tagName)) return
  if (event.key === 'ArrowLeft') step(-1)
  if (event.key === 'ArrowRight') step(1)
  if (event.key === 'Escape') {
    catalogOpen.value = false
    fontOpen.value = false
  }
}

watch(
  () => route.params.chapterId,
  (value) => {
    if (!value) return
    if (state.chapter && Number(value) === state.chapter.id) return
    loadChapter(Number(value))
  }
)

watch(
  () => route.params.bookId,
  () => {
    chapterRequest?.abort()
    chapterSeq += 1
    state.chapter = null
    state.chapters = []
    state.paragraphs = []
    state.prevId = null
    state.nextId = null
    state.switching = false
    loadBook()
  }
)

watch(catalogOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

onMounted(() => {
  loadBook()
  window.addEventListener('keydown', onKey)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKey)
  document.body.style.overflow = ''
  chapterRequest?.abort()
})
</script>

<template>
  <div class="reader" :data-theme="prefs.theme" :style="{ '--read-size': `${prefs.size}px` }">
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink :to="`/books/${bookId}`" class="back" aria-label="返回作品页">
          <span aria-hidden="true">‹</span>
          <span class="back-text">{{ state.book?.bookName || '作品' }}</span>
        </RouterLink>

        <div class="center">
          <span class="cname">{{ state.chapter?.chapterName || '加载中' }}</span>
          <span class="cmeta mono" v-if="chapterIndex >= 0">
            {{ chapterIndex + 1 }} / {{ state.chapters.length }}
          </span>
        </div>

        <button class="icon-btn" type="button" @click="catalogOpen = true">目录</button>
      </div>
      <div class="progress" aria-hidden="true"><span :style="{ width: `${progress}%` }" /></div>
      <div v-if="state.switching && !state.loading" class="busybar" aria-hidden="true"><span /></div>
    </header>

    <main class="page">
      <div v-if="state.loading" class="loading">
        <div class="skeleton l t" />
        <div v-for="i in 8" :key="i" class="skeleton l" :class="{ short: i % 4 === 0 }" />
      </div>

      <div v-else-if="state.error" class="state">
        <h1 class="serif">{{ state.error }}</h1>
        <div class="state-actions">
          <button class="pill-btn" type="button" @click="loadBook">重新加载</button>
          <RouterLink :to="`/books/${bookId}`" class="pill-btn is-ghost">回到作品页</RouterLink>
        </div>
      </div>

      <Transition v-else :name="`turn-${turn}`" mode="out-in" @before-enter="resetScroll">
        <article :key="state.chapter?.id">
          <h1 class="chapter-title serif">{{ state.chapter?.chapterName }}</h1>
          <div class="chapter-meta mono">
            第 {{ state.chapter?.chapterNum }} 章 · {{ state.chapter?.wordCount || 0 }} 字
          </div>

          <div class="prose serif">
            <p v-for="(line, i) in state.paragraphs" :key="i">{{ line }}</p>
            <p v-if="!state.paragraphs.length" class="muted">这一章还没有正文。</p>
          </div>

          <div class="endnav">
            <button class="pill-btn is-ghost" type="button" :disabled="!state.prevId || state.switching" @click="step(-1)">
              ← 上一章
            </button>
            <button class="pill-btn" type="button" :disabled="!state.nextId || state.switching" @click="step(1)">
              {{ state.nextId ? '下一章' : '已是最后一章' }}
              <span class="pill-icon" aria-hidden="true">↗</span>
            </button>
          </div>
        </article>
      </Transition>
    </main>

    <div class="island" :class="{ 'is-lifted': fontOpen }">
      <button class="island-btn" type="button" :disabled="!state.prevId || state.switching" @click="step(-1)">上一章</button>
      <button class="island-btn" type="button" @click="catalogOpen = true">目录</button>
      <button class="island-btn" type="button" :aria-expanded="fontOpen" @click="fontOpen = !fontOpen">
        字号 <span class="mono">{{ prefs.size }}</span>
      </button>
      <button class="island-btn" type="button" @click="cycleTheme">{{ themeLabel }}</button>
      <button class="island-btn" type="button" :disabled="!state.nextId || state.switching" @click="step(1)">下一章</button>
    </div>

    <Transition name="panel">
      <div v-if="fontOpen" class="font-panel">
        <div class="font-row">
          <span class="hint">字号</span>
          <button class="round" type="button" aria-label="减小字号" @click="changeSize(-1)">A−</button>
          <span class="mono size">{{ prefs.size }}</span>
          <button class="round" type="button" aria-label="增大字号" @click="changeSize(1)">A+</button>
        </div>
        <div class="font-row">
          <span class="hint">主题</span>
          <button
            v-for="t in THEMES"
            :key="t.key"
            class="theme-dot"
            :class="[`is-${t.key}`, { 'is-on': prefs.theme === t.key }]"
            type="button"
            :aria-label="t.label"
            @click="prefs.theme = t.key"
          >
            <span>{{ t.label }}</span>
          </button>
        </div>
      </div>
    </Transition>

    <Transition name="fade">
      <div v-if="catalogOpen" class="scrim" @click="catalogOpen = false" />
    </Transition>

    <Transition name="drawer">
      <aside v-if="catalogOpen" class="drawer" aria-label="目录">
        <div class="drawer-head">
          <div>
            <div class="eyebrow">目录</div>
            <h2 class="serif">{{ state.book?.bookName || '作品' }}</h2>
          </div>
          <button class="close" type="button" aria-label="关闭目录" @click="catalogOpen = false">
            <span /><span />
          </button>
        </div>
        <ol class="drawer-list">
          <li v-for="(item, i) in state.chapters" :key="item.id">
            <button
              class="drawer-item"
              :class="{ 'is-on': item.id === state.chapter?.id }"
              type="button"
              :style="{ '--d': `${(i % 8) * 12}ms` }"
              @click="goChapter(item.id)"
            >
              <span class="mono idx">{{ String(item.chapterNum).padStart(2, '0') }}</span>
              <span class="name">{{ item.chapterName }}</span>
            </button>
          </li>
        </ol>
      </aside>
    </Transition>
  </div>
</template>

<style scoped>
.reader {
  --rb: #fbfaf7;
  --ri: #26251f;
  --rm: #8a867c;
  --rl: #eae6dc;
  --rsurface: #ffffff;
  --rbar: rgba(251, 250, 247, 0.86);
  min-height: 100vh;
  background: var(--rb);
  color: var(--ri);
  transition: background var(--dur-2) var(--ease), color var(--dur-2) var(--ease);
}

.reader[data-theme='sepia'] {
  --rb: #f6efe1;
  --ri: #3a342a;
  --rm: #8b7f6b;
  --rl: #e6dcc7;
  --rsurface: #fbf4e6;
  --rbar: rgba(246, 239, 225, 0.86);
}

.reader[data-theme='night'] {
  --rb: #16171a;
  --ri: #c9c7c0;
  --rm: #7e8079;
  --rl: #2a2c30;
  --rsurface: #1f2126;
  --rbar: rgba(22, 23, 26, 0.86);
}

.topbar {
  position: fixed;
  inset: 0 0 auto;
  z-index: 30;
  background: var(--rbar);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid var(--rl);
}

.topbar-inner {
  display: flex;
  align-items: center;
  gap: var(--s-4);
  max-width: 920px;
  margin-inline: auto;
  height: 56px;
  padding: 0 var(--s-5);
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--t-sm);
  color: var(--rm);
  transition: color var(--dur-1) var(--ease);
}

.back:hover {
  color: var(--ri);
}

.back span[aria-hidden] {
  font-size: 18px;
  line-height: 1;
}

.back-text {
  max-width: 160px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.center {
  flex: 1;
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}

.cname {
  font-size: var(--t-sm);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cmeta {
  font-size: var(--t-xs);
  color: var(--rm);
}

.icon-btn {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--rl);
  border-radius: var(--r-pill);
  font-size: var(--t-xs);
  color: var(--rm);
  transition: color var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease);
}

.icon-btn:hover {
  color: var(--ri);
  border-color: var(--rm);
}

.progress {
  height: 1px;
  background: var(--rl);
}

.progress span {
  display: block;
  height: 1px;
  background: var(--ri);
  transition: width var(--dur-3) var(--ease);
}

/* 翻章等待时贴一条 2px 的不确定进度条，比骨架屏更不打断阅读 */
.busybar {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 2px;
  overflow: hidden;
}

.busybar span {
  display: block;
  width: 40%;
  height: 100%;
  background: var(--ri);
  animation: busy-slide 1s var(--ease) infinite;
}

@keyframes busy-slide {
  from {
    transform: translateX(-100%);
  }
  to {
    transform: translateX(350%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .busybar span {
    animation-duration: 2.4s;
  }
}

.page {
  max-width: 720px;
  margin-inline: auto;
  padding: 100px var(--gutter) 160px;
  overflow-x: clip;
}

/* 翻章：下一章从右侧进、上一章从左侧进，退场走反方向 */
.turn-next-enter-active {
  transition: opacity 180ms var(--ease), transform 180ms var(--ease-swift);
}

.turn-next-leave-active {
  transition: opacity 90ms var(--ease), transform 90ms var(--ease);
}

.turn-next-enter-from {
  opacity: 0;
  transform: translateX(18px);
}

.turn-next-leave-to {
  opacity: 0;
  transform: translateX(-12px);
}

.turn-back-enter-active {
  transition: opacity 180ms var(--ease), transform 180ms var(--ease-swift);
}

.turn-back-leave-active {
  transition: opacity 90ms var(--ease), transform 90ms var(--ease);
}

.turn-back-enter-from {
  opacity: 0;
  transform: translateX(-18px);
}

.turn-back-leave-to {
  opacity: 0;
  transform: translateX(12px);
}

/* 从目录点进、首屏进入不做横向位移，只淡入 */
.turn-jump-enter-active {
  transition: opacity 240ms var(--ease);
}

.turn-jump-leave-active {
  transition: opacity 90ms var(--ease);
}

.turn-jump-enter-from,
.turn-jump-leave-to {
  opacity: 0;
}

.chapter-title {
  font-size: clamp(20px, 2.6vw, 26px);
  line-height: 1.4;
  letter-spacing: 0.01em;
}

.chapter-meta {
  margin-top: 10px;
  padding-bottom: var(--s-5);
  border-bottom: 1px solid var(--rl);
  font-size: var(--t-xs);
  color: var(--rm);
}

.prose {
  margin-top: var(--s-6);
  font-size: var(--read-size);
  line-height: 2.05;
  letter-spacing: 0.015em;
}

.prose p {
  text-indent: 2em;
  margin-bottom: 0.9em;
  word-break: break-word;
}

.prose p.muted {
  text-indent: 0;
  color: var(--rm);
}

.endnav {
  display: flex;
  justify-content: space-between;
  gap: var(--s-4);
  margin-top: var(--s-8);
  padding-top: var(--s-5);
  border-top: 1px solid var(--rl);
}

.pill-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 8px 0 20px;
  border: 1px solid var(--ri);
  border-radius: var(--r-pill);
  background: var(--ri);
  color: var(--rb);
  font-size: var(--t-sm);
  transition: transform var(--dur-1) var(--spring), opacity var(--dur-1) var(--ease);
}

.pill-btn.is-ghost {
  background: transparent;
  color: var(--ri);
  border-color: var(--rl);
  padding: 0 20px;
}

.pill-btn:active {
  transform: scale(0.98);
}

.pill-btn[disabled] {
  opacity: 0.4;
  pointer-events: none;
}

.pill-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--rb);
  color: var(--ri);
  font-size: 11px;
}

.island {
  position: fixed;
  left: 50%;
  bottom: 22px;
  transform: translateX(-50%);
  z-index: 32;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 6px;
  border: 1px solid var(--rl);
  border-radius: var(--r-pill);
  background: var(--rsurface);
  box-shadow: 0 10px 30px -18px rgba(0, 0, 0, 0.35);
  transition: transform var(--dur-2) var(--ease-swift);
}

.island.is-lifted {
  transform: translateX(-50%) translateY(-6px);
}

.island-btn {
  height: 32px;
  padding: 0 14px;
  border-radius: var(--r-pill);
  font-size: var(--t-xs);
  color: var(--rm);
  display: inline-flex;
  align-items: center;
  gap: 5px;
  transition: background var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.island-btn:hover {
  background: rgba(127, 127, 127, 0.1);
  color: var(--ri);
}

.island-btn:active {
  transform: scale(0.97);
}

.island-btn[disabled] {
  opacity: 0.35;
  pointer-events: none;
}

.font-panel {
  position: fixed;
  left: 50%;
  bottom: 72px;
  transform: translateX(-50%);
  z-index: 31;
  width: min(360px, calc(100vw - 40px));
  padding: var(--s-4);
  border: 1px solid var(--rl);
  border-radius: var(--r-l);
  background: var(--rsurface);
  box-shadow: 0 18px 40px -24px rgba(0, 0, 0, 0.4);
}

.font-row {
  display: flex;
  align-items: center;
  gap: var(--s-3);
}

.font-row + .font-row {
  margin-top: var(--s-4);
  padding-top: var(--s-4);
  border-top: 1px solid var(--rl);
}

.font-row .hint {
  min-width: 34px;
  color: var(--rm);
}

.round {
  height: 30px;
  padding: 0 12px;
  border: 1px solid var(--rl);
  border-radius: var(--r-pill);
  font-size: var(--t-xs);
  color: var(--ri);
  transition: transform var(--dur-1) var(--spring);
}

.round:active {
  transform: scale(0.95);
}

.size {
  min-width: 24px;
  text-align: center;
  font-size: var(--t-sm);
}

.theme-dot {
  height: 30px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  border: 1px solid var(--rl);
  font-size: var(--t-xs);
  color: var(--rm);
  transition: border-color var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.theme-dot.is-on {
  border-color: var(--ri);
  color: var(--ri);
}

.theme-dot.is-paper {
  background: #fbfaf7;
  color: #26251f;
}

.theme-dot.is-sepia {
  background: #f6efe1;
  color: #3a342a;
}

.theme-dot.is-night {
  background: #16171a;
  color: #c9c7c0;
}

.scrim {
  position: fixed;
  inset: 0;
  z-index: 40;
  background: rgba(20, 20, 18, 0.35);
}

.drawer {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 41;
  width: min(330px, 84vw);
  display: flex;
  flex-direction: column;
  background: var(--rb);
  border-right: 1px solid var(--rl);
}

.drawer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--s-4);
  padding: var(--s-5);
  border-bottom: 1px solid var(--rl);
}

.drawer-head h2 {
  margin-top: 6px;
  font-size: var(--t-lg);
}

.close {
  position: relative;
  width: 30px;
  height: 30px;
  flex: none;
}

.close span {
  position: absolute;
  left: 4px;
  top: 14px;
  width: 22px;
  height: 1px;
  background: var(--ri);
}

.close span:first-child {
  transform: rotate(45deg);
}

.close span:last-child {
  transform: rotate(-45deg);
}

.drawer-list {
  flex: 1;
  overflow-y: auto;
  padding: var(--s-3) var(--s-3) var(--s-7);
}

.drawer-item {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  width: 100%;
  padding: 10px 12px;
  border-radius: var(--r-s);
  text-align: left;
  font-size: var(--t-sm);
  color: var(--rm);
  transition: background var(--dur-1) var(--ease), color var(--dur-1) var(--ease),
    opacity var(--dur-2) var(--ease), transform var(--dur-2) var(--ease-swift);
  /* 目录上千条时只布局视口附近，条目高度先按实测值占位 */
  content-visibility: auto;
  contain-intrinsic-size: auto 42px;
}

/* 目录展开时条目依次落位，延迟只在这段过渡里生效 */
.drawer-enter-from .drawer-item {
  opacity: 0;
  transform: translateX(-10px);
  transition-delay: var(--d, 0ms);
}

.drawer-item:hover {
  background: rgba(127, 127, 127, 0.08);
  color: var(--ri);
}

.drawer-item.is-on {
  color: var(--ri);
  background: rgba(127, 127, 127, 0.12);
}

.idx {
  font-size: var(--t-xs);
  color: var(--rm);
}

.name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.loading {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: var(--s-6);
}

.l {
  height: 16px;
  border-radius: 3px;
  background: var(--rl);
  opacity: 0.6;
  animation: pulse 1.6s var(--ease) infinite;
}

.l.t {
  height: 26px;
  width: 46%;
  margin-bottom: var(--s-5);
}

.l.short {
  width: 62%;
}

.state {
  padding-top: var(--s-8);
  text-align: center;
}

.state h1 {
  font-size: var(--t-xl);
}

.state-actions {
  display: flex;
  justify-content: center;
  gap: var(--s-3);
  margin-top: var(--s-5);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity var(--dur-2) var(--ease);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.panel-enter-active,
.panel-leave-active {
  transition: opacity var(--dur-2) var(--ease), transform var(--dur-2) var(--ease-swift);
}

.panel-enter-from,
.panel-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}

.drawer-enter-active,
.drawer-leave-active {
  transition: transform var(--dur-2) var(--ease-swift), opacity var(--dur-2) var(--ease);
}

.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(-100%);
  opacity: 0.6;
}

@media (max-width: 620px) {
  .page {
    padding-top: 88px;
    padding-bottom: 130px;
  }

  .back-text {
    display: none;
  }

  .center {
    justify-content: flex-start;
  }

  .island {
    bottom: 14px;
    width: calc(100vw - 24px);
    justify-content: space-between;
  }

  .island-btn {
    padding: 0 10px;
  }

  .font-panel {
    bottom: 66px;
  }
}
</style>
