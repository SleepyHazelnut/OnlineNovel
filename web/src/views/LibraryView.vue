<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { bookApi } from '@/api'
import { useCategories } from '@/composables/useCategories'
import { readList, writeList, rememberScroll } from '@/utils/listCache'
import BookGrid from '@/components/BookGrid.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pager from '@/components/Pager.vue'

const route = useRoute()
const router = useRouter()
const { state: categoryState, ensure: ensureCategories } = useCategories()

const PAGE_SIZE = 20
const CACHE_KEY = 'library'

const state = reactive({
  loading: true,
  error: '',
  books: [],
  total: 0
})

const categories = computed(() =>
  categoryState.list.filter((item) => !item.parentId || Number(item.parentId) === 0)
)

const keywordInput = ref(route.query.keyword || '')
const keyword = computed(() => String(route.query.keyword || '').trim())
const categoryId = computed(() => (route.query.categoryId ? Number(route.query.categoryId) : null))
const page = computed(() => Math.max(1, Number(route.query.page) || 1))

/* 缓存只对“同一组筛选条件”有效，页码/关键词/分类变了就得重新请求 */
const signature = () => JSON.stringify([keyword.value, categoryId.value, page.value])

let seq = 0

async function load() {
  const current = ++seq
  state.loading = true
  state.error = ''
  try {
    const data = keyword.value
      ? await bookApi.search({ keyword: keyword.value, pageNum: page.value, pageSize: PAGE_SIZE })
      : await bookApi.list({ pageNum: page.value, pageSize: PAGE_SIZE, categoryId: categoryId.value })
    if (current !== seq) return
    state.books = data?.list || []
    state.total = Number(data?.total || 0)
    writeList(CACHE_KEY, { books: state.books, total: state.total }, signature())
  } catch (err) {
    if (current !== seq) return
    state.error = err.message || '加载失败'
    state.books = []
    state.total = 0
  } finally {
    if (current === seq) state.loading = false
  }
}

function apply(patch) {
  const next = { ...route.query, ...patch }
  if (!('page' in patch)) next.page = 1
  Object.keys(next).forEach((key) => {
    if (next[key] === '' || next[key] === null || next[key] === undefined) delete next[key]
  })
  router.push({ query: next })
}

function submitSearch() {
  apply({ keyword: keywordInput.value.trim() })
}

function pickCategory(id) {
  apply({ categoryId: id })
}

function reset() {
  keywordInput.value = ''
  router.push({ query: {} })
}

watch(
  () => route.query,
  () => {
    /* 离开书库时 route 已经指向新页面，别再拿新 query 发一次列表请求 */
    if (route.name !== 'library') return
    keywordInput.value = route.query.keyword || ''
    /* 列表页的滚动交给页面自己管，换页/换筛选时手动回顶 */
    window.scrollTo({ top: 0, behavior: 'auto' })
    load()
  }
)

onMounted(() => {
  ensureCategories()
  const cached = readList(CACHE_KEY)
  if (cached && cached.signature === signature()) {
    state.books = cached.data.books || []
    state.total = Number(cached.data.total || 0)
    state.loading = false
    nextTick(() => window.scrollTo({ top: cached.scrollY || 0, behavior: 'auto' }))
    return
  }
  /* 路由层对这页关了自动回顶，首次进入得自己落位 */
  window.scrollTo({ top: 0, behavior: 'auto' })
  load()
})

onBeforeRouteLeave(() => {
  rememberScroll(CACHE_KEY)
})
</script>

<template>
  <div class="library container">
    <header class="head">
      <h1>{{ keyword ? `「${keyword}」的结果` : '书库' }}</h1>
      <p class="count mono">{{ state.loading ? '—' : state.total }} 部作品</p>
    </header>

    <div class="toolbar">
      <form class="searchbar" role="search" @submit.prevent="submitSearch">
        <input
          v-model="keywordInput"
          class="field"
          type="search"
          placeholder="搜书名或作者"
          aria-label="搜索作品"
        />
        <button class="btn btn--solid btn--sm" type="submit">搜索</button>
      </form>
      <button v-if="keyword || categoryId" class="clear" type="button" @click="reset">清除筛选</button>
    </div>

    <div class="filters">
      <button class="chip" :class="{ 'is-on': !categoryId }" type="button" @click="pickCategory(null)">全部</button>
      <button
        v-for="cat in categories"
        :key="cat.id"
        class="chip"
        :class="{ 'is-on': categoryId === cat.id }"
        type="button"
        @click="pickCategory(cat.id)"
      >
        {{ cat.categoryName }}
      </button>
    </div>

    <div v-if="state.error" class="error">
      <span>{{ state.error }}</span>
      <button class="btn btn--sm" type="button" @click="load">重试</button>
    </div>

    <BookGrid :books="state.books" :loading="state.loading" :skeleton-count="12" />

    <EmptyState
      v-if="!state.loading && !state.error && !state.books.length"
      title="没有找到相关作品"
      desc="换一个关键词，或者切换到其他分类看看。"
    >
      <button class="btn btn--sm" type="button" @click="reset">回到全部</button>
    </EmptyState>

    <Pager :page="page" :page-size="PAGE_SIZE" :total="state.total" @change="apply({ page: $event })" />
  </div>
</template>

<style scoped>
.library {
  padding-top: var(--s-5);
  min-height: 60vh;
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--s-4);
  padding-bottom: var(--s-3);
}

.head h1 {
  font-size: var(--t-xl);
  font-weight: 500;
}

.count {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: var(--s-3);
  padding-bottom: var(--s-4);
}

.searchbar {
  display: flex;
  gap: var(--s-2);
  flex: 1;
  max-width: 400px;
}

.searchbar .field {
  height: 32px;
  border-radius: var(--r-pill);
  padding-inline: 14px;
  font-size: var(--t-sm);
}

.clear {
  font-size: var(--t-sm);
  color: var(--ink-3);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--s-4);
  padding-bottom: var(--s-3);
  border-bottom: 1px solid var(--line);
  margin-bottom: var(--s-5);
}

.chip {
  position: relative;
  padding: 2px 0 6px;
  background: none;
  border: none;
  font-size: var(--t-base);
  color: var(--ink-2);
  transition: color var(--dur-1) var(--ease);
}

.chip::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 1.5px;
  background: var(--ink);
  opacity: 0;
  transform: scaleX(0.4);
  transition: opacity var(--dur-1) var(--ease), transform var(--dur-1) var(--ease);
}

.chip:hover {
  color: var(--ink);
}

.chip.is-on {
  color: var(--ink);
  font-weight: 500;
}

.chip.is-on::after {
  opacity: 1;
  transform: scaleX(1);
}

.error {
  display: flex;
  align-items: center;
  gap: var(--s-4);
  margin-bottom: var(--s-5);
  padding: var(--s-3) var(--s-4);
  border-radius: var(--r-m);
  background: var(--pale-red);
  color: var(--pale-red-ink);
  font-size: var(--t-sm);
}

@media (max-width: 560px) {
  .searchbar {
    max-width: none;
  }

  .filters {
    gap: var(--s-3);
  }
}
</style>
