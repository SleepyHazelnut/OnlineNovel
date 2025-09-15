<script setup>
import { computed, onMounted, reactive, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { rankApi } from '@/api'
import { fmtCount, scoreText, fmtRelative } from '@/utils/format'
import { useCategories } from '@/composables/useCategories'
import BookCover from '@/components/BookCover.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const router = useRouter()
const { nameOf, ensure: ensureCategories } = useCategories()

const TABS = [
  { key: 'hot', label: '人气榜', title: '人气榜', desc: '按阅读热度实时累加', api: 'hot' },
  { key: 'click', label: '点击榜', title: '点击榜', desc: '按累计阅读次数排序', api: 'click' },
  { key: 'newest', label: '新书榜', title: '新书榜', desc: '按作品入库时间倒序', api: 'newest' },
  { key: 'update', label: '更新榜', title: '更新榜', desc: '按最近更新时间倒序', api: 'update' }
]

const active = computed(() => TABS.find((t) => t.key === route.query.tab) || TABS[0])
const state = reactive({ loading: true, ready: false, error: '', list: [] })

async function load() {
  /* 换榜时保留当前列表，只有首次进入才铺骨架 */
  if (!state.ready) state.loading = true
  state.error = ''
  try {
    const data = await rankApi[active.value.api](20)
    state.list = data || []
  } catch (err) {
    state.error = err.message || '加载失败'
    state.list = []
  } finally {
    state.loading = false
    state.ready = true
  }
}

function switchTab(key) {
  if (key === active.value.key) return
  router.push({ query: { tab: key } })
}

watch(() => route.query.tab, load)
onMounted(() => {
  ensureCategories()
  load()
})
</script>

<template>
  <div class="rank container">
    <header class="head">
      <h1>榜单</h1>
      <p class="desc">{{ active.desc }}</p>
    </header>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in TABS"
        :key="tab.key"
        class="tab"
        :class="{ 'is-on': active.key === tab.key }"
        type="button"
        role="tab"
        :aria-selected="active.key === tab.key"
        @click="switchTab(tab.key)"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="state.loading" class="rows">
      <div v-for="i in 8" :key="i" class="row">
        <div class="skeleton sk-no" />
        <div class="skeleton sk-cover" />
        <div class="sk-lines">
          <div class="skeleton sk-line" />
          <div class="skeleton sk-line short" />
        </div>
      </div>
    </div>

    <div v-else-if="state.error" class="error">
      <span>{{ state.error }}</span>
      <button class="btn btn--sm" type="button" @click="load">重试</button>
    </div>

    <EmptyState v-else-if="!state.list.length" title="榜单暂时为空" desc="等有作品开始阅读后，这里会自动出现。" />

    <Transition v-else name="swap" mode="out-in">
      <ol :key="active.key" class="rows">
        <li v-for="(item, i) in state.list" :key="item.bookId || i">
        <RouterLink :to="`/books/${item.bookId}`" class="row">
          <span class="no mono" :class="`is-${item.rank}`">{{ String(item.rank || i + 1).padStart(2, '0') }}</span>
          <span class="thumb">
            <BookCover :book="item" :category="nameOf(item.categoryId)" size="sm" />
          </span>
          <span class="body">
            <span class="name">{{ item.bookName }}</span>
            <span class="meta">
              <span v-if="nameOf(item.categoryId)" class="cat">{{ nameOf(item.categoryId) }}</span>
              {{ item.authorName || '佚名' }}
              <span class="dot">·</span>
              {{ fmtCount(item.wordCount) }} 字
              <span class="dot">·</span>
              {{ active.key === 'newest' ? `入库于 ${fmtRelative(item.createTime)}` : `更新于 ${fmtRelative(item.updateTime)}` }}
            </span>
          </span>
          <span class="tail">
            <span v-if="active.key === 'hot'" class="heat mono">{{ Number(item.heat || 0).toFixed(0) }}</span>
            <span v-else class="score mono">{{ scoreText(item.score) }}</span>
            <span class="tail-label">{{ active.key === 'hot' ? '热度' : '评分' }}</span>
          </span>
        </RouterLink>
      </li>
      </ol>
    </Transition>
  </div>
</template>

<style scoped>
.rank {
  padding-top: var(--s-5);
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--s-4);
  padding-bottom: var(--s-3);
}

.head h1 {
  font-size: var(--t-lg);
  font-weight: 500;
}

.desc {
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.tabs {
  display: flex;
  gap: var(--s-5);
  margin-top: var(--s-2);
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

.rows {
  margin-top: var(--s-3);
}

.row {
  display: flex;
  align-items: center;
  gap: var(--s-5);
  padding: var(--s-3) var(--s-2);
  border-bottom: 1px solid var(--line);
  transition: background var(--dur-1) var(--ease), padding var(--dur-2) var(--ease-swift);
}

.rows li:hover .row {
  background: var(--paper-warm);
  padding-inline: var(--s-3);
}

.no {
  width: 30px;
  font-size: var(--t-md);
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}

.no.is-1,
.no.is-2,
.no.is-3 {
  color: var(--ink);
}

.thumb {
  position: relative;
  width: 44px;
  height: 60px;
  flex: none;
  border-radius: var(--r-xs);
  overflow: hidden;
}

.thumb :deep(.bcover) {
  width: 100%;
  height: 100%;
  aspect-ratio: auto;
  border-radius: 0;
}

.thumb :deep(.title) {
  display: none;
}

.body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  font-size: var(--t-base);
  color: var(--ink);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.meta {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.cat {
  padding: 0 5px;
  border-radius: 3px;
  background: var(--sage-soft);
  color: var(--sage-deep);
  line-height: 1.6;
}

.dot {
  color: var(--line-strong);
}

.tail {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  min-width: 56px;
}

.heat {
  font-size: var(--t-md);
  color: var(--pale-red-ink);
}

.score {
  font-size: var(--t-md);
  color: var(--ink);
}

.tail-label {
  font-size: 10px;
  letter-spacing: 0.1em;
  color: var(--ink-3);
}

.sk-no {
  width: 34px;
  height: 20px;
}

.sk-cover {
  width: 44px;
  height: 60px;
}

.sk-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sk-line {
  height: 12px;
  width: 42%;
}

.sk-line.short {
  width: 26%;
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

/* 换榜时列表整体替换，淡出淡入比逐行入场更安静 */
.swap-enter-active {
  transition: opacity 180ms var(--ease), transform 180ms var(--ease);
}

.swap-leave-active {
  transition: opacity 90ms var(--ease);
}

.swap-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.swap-leave-to {
  opacity: 0;
}

@media (max-width: 560px) {
  .desc {
    display: none;
  }

  .row {
    gap: var(--s-3);
  }

  .meta {
    flex-wrap: wrap;
  }
}
</style>
