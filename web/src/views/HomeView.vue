<script setup>
import { computed, onMounted, reactive } from 'vue'
import { homeApi, rankApi } from '@/api'
import { fmtCount, scoreText, bookStatusText } from '@/utils/format'
import { useCategories } from '@/composables/useCategories'
import BookGrid from '@/components/BookGrid.vue'
import BookCover from '@/components/BookCover.vue'

const { state: categoryState, nameOf, ensure: ensureCategories } = useCategories()

const state = reactive({
  loading: true,
  error: '',
  hot: [],
  quality: [],
  newest: [],
  update: [],
  categories: [],
  rank: []
})

const featured = computed(() => state.quality[0] || state.hot[0] || null)
const featuredId = computed(() => featured.value?.id ?? featured.value?.bookId)
const featuredCat = computed(() => nameOf(featured.value?.categoryId))

const sections = computed(() => [
  { key: 'hot', title: '本周强推', hint: '按阅读热度排序', books: state.hot.slice(0, 12), to: '/rank' },
  { key: 'update', title: '最近更新', hint: '按更新时间排序', books: state.update.slice(0, 12), to: '/rank?tab=update' },
  { key: 'newest', title: '新书上架', hint: '按入库时间倒序', books: state.newest.slice(0, 12), to: '/rank?tab=newest' }
])

async function load() {
  state.loading = true
  state.error = ''
  try {
    const [recommend, , rank, update] = await Promise.all([
      homeApi.recommend(),
      ensureCategories(),
      rankApi.hot(8),
      rankApi.update(12)
    ])
    state.hot = recommend?.hot || []
    state.quality = recommend?.quality || []
    state.newest = recommend?.newest || []
    state.categories = categoryState.list
    state.rank = (rank || []).slice(0, 8)
    state.update = update || []
  } catch (err) {
    state.error = err.message || '加载失败'
  } finally {
    state.loading = false
  }
}

onMounted(load)
</script>

<template>
  <div class="home" :class="{ 'is-ready': !state.loading }">
    <nav class="catbar container">
      <RouterLink to="/library" class="catbar__all">全部作品</RouterLink>
      <RouterLink
        v-for="cat in state.categories"
        :key="cat.id"
        :to="`/library?categoryId=${cat.id}`"
        class="catbar__item"
      >
        {{ cat.categoryName }}
      </RouterLink>
      <RouterLink to="/rank" class="catbar__more">榜单 →</RouterLink>
    </nav>

    <section class="banner container">
      <div v-if="featured" class="spot">
        <RouterLink :to="`/books/${featuredId}`" class="spot__cover">
          <BookCover :book="featured" :category="featuredCat" size="lg" />
        </RouterLink>
        <div class="spot__info">
          <div class="spot__tags">
            <span v-if="featuredCat" class="chip chip--soft">{{ featuredCat }}</span>
            <span class="chip" :class="{ 'chip--done': Number(featured.status) === 1 }">
              {{ bookStatusText(featured.status) }}
            </span>
            <span class="spot__label">编辑推荐</span>
          </div>
          <h1 class="serif">{{ featured.bookName }}</h1>
          <p class="spot__desc">{{ featured.bookDesc || '作者暂未填写简介。' }}</p>
          <ul class="spot__stats">
            <li><b>{{ scoreText(featured.score) }}</b><span>评分</span></li>
            <li><b>{{ fmtCount(featured.wordCount) }}</b><span>字数</span></li>
            <li><b>{{ fmtCount(featured.visitCount) || '—' }}</b><span>阅读</span></li>
            <li><b>{{ fmtCount(featured.commentCount) || '—' }}</b><span>评论</span></li>
          </ul>
          <div class="spot__actions">
            <RouterLink :to="`/books/${featuredId}`" class="btn btn--solid">
              开始阅读
              <span class="btn__icon" aria-hidden="true">↗</span>
            </RouterLink>
            <RouterLink :to="`/books/${featuredId}`" class="btn">查看详情</RouterLink>
          </div>
        </div>
      </div>

      <div v-else class="spot spot--ph">
        <div class="skeleton ph-cover" />
        <div class="ph-lines">
          <div class="skeleton ph-line" />
          <div class="skeleton ph-line short" />
          <div class="skeleton ph-line" />
        </div>
      </div>

      <aside class="board">
        <header class="board__head">
          <h2>人气榜</h2>
          <RouterLink to="/rank" class="board__more">完整榜单 →</RouterLink>
        </header>
        <ol v-if="state.rank.length" class="board__list">
          <li v-for="(item, i) in state.rank" :key="item.id ?? item.bookId ?? i">
            <RouterLink :to="`/books/${item.id ?? item.bookId}`">
              <em :class="{ 'is-top': i < 3 }">{{ i + 1 }}</em>
              <span class="bname">{{ item.bookName }}</span>
              <span class="bcat">{{ nameOf(item.categoryId) }}</span>
            </RouterLink>
          </li>
        </ol>
        <div v-else class="board__empty">暂无榜单数据</div>
      </aside>
    </section>

    <div v-if="state.error" class="container error">
      <p>{{ state.error }}</p>
      <button class="btn btn--sm" type="button" @click="load">重试</button>
    </div>

    <section
      v-for="section in sections"
      v-show="state.loading || section.books.length"
      :key="section.key"
      class="section container"
    >
      <div class="section__head">
        <h2>{{ section.title }}</h2>
        <span class="section__hint">{{ section.hint }}</span>
        <RouterLink :to="section.to" class="section__more">全部 →</RouterLink>
      </div>
      <BookGrid :books="section.books" :loading="state.loading" :skeleton-count="6" />
    </section>
  </div>
</template>

<style scoped>
.home {
  padding-top: var(--s-3);
}

/* 首屏只在数据到位后落位一次，之后的板块交给滚动入场 */
.home.is-ready .spot {
  animation: rise-in 520ms var(--ease) both;
}

.home.is-ready .board {
  animation: rise-in 520ms var(--ease) 70ms both;
}

@keyframes rise-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .home.is-ready .spot,
  .home.is-ready .board {
    animation: none;
  }
}

.catbar {
  display: flex;
  align-items: center;
  gap: var(--s-5);
  padding-block: var(--s-3);
  border-bottom: 1px solid var(--line);
  overflow-x: auto;
  scrollbar-width: none;
}

.catbar::-webkit-scrollbar {
  display: none;
}

.catbar__all,
.catbar__item,
.catbar__more {
  flex: none;
  font-size: var(--t-base);
  color: var(--ink-2);
  transition: color var(--dur-1) var(--ease);
}

.catbar__all {
  font-weight: 500;
  color: var(--ink);
}

.catbar__item:hover,
.catbar__more:hover {
  color: var(--sage-deep);
}

.catbar__more {
  margin-left: auto;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.banner {
  display: grid;
  grid-template-columns: minmax(0, 1.62fr) minmax(0, 1fr);
  gap: var(--s-7);
  padding-block: var(--s-6) var(--s-5);
}

.spot {
  display: flex;
  gap: var(--s-5);
  min-width: 0;
}

.spot__cover {
  flex: none;
  width: 148px;
}

.spot__info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.spot__tags {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: var(--s-3);
}

.chip {
  padding: 1px 7px;
  border-radius: 3px;
  background: var(--surface-sunken);
  color: var(--ink-2);
  font-size: var(--t-xs);
}

.chip--soft {
  background: var(--sage-soft);
  color: var(--sage-deep);
}

.chip--done {
  background: var(--pale-yellow);
  color: var(--pale-yellow-ink);
}

.spot__label {
  font-size: var(--t-xs);
  letter-spacing: 0.12em;
  color: var(--ink-3);
}

.spot__info h1 {
  font-size: var(--t-2xl);
  line-height: 1.24;
  letter-spacing: -0.01em;
}

.spot__desc {
  margin-top: var(--s-3);
  font-size: var(--t-sm);
  line-height: 1.9;
  color: var(--ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.spot__stats {
  display: flex;
  gap: var(--s-6);
  margin: var(--s-4) 0 0;
  padding: 0;
  list-style: none;
}

.spot__stats li {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-variant-numeric: tabular-nums;
}

.spot__stats b {
  font-size: var(--t-md);
  font-weight: 500;
}

.spot__stats span {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.spot__actions {
  display: flex;
  gap: var(--s-2);
  margin-top: var(--s-5);
}

.spot--ph {
  align-items: flex-start;
}

.ph-cover {
  flex: none;
  width: 148px;
  aspect-ratio: 3 / 4;
  border-radius: 5px;
}

.ph-lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--s-3);
  padding-top: var(--s-4);
}

.ph-line {
  height: 14px;
  width: 70%;
}

.ph-line.short {
  width: 40%;
}

.board {
  min-width: 0;
  padding-left: var(--s-6);
  border-left: 1px solid var(--line);
}

.board__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: var(--s-3);
  border-bottom: 1px solid var(--line);
}

.board__head h2 {
  font-size: var(--t-md);
  font-weight: 500;
}

.board__more {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.board__list {
  margin: 0;
  padding: var(--s-2) 0 0;
  list-style: none;
}

.board__list a {
  display: flex;
  align-items: baseline;
  gap: var(--s-2);
  padding: 5px 0;
  font-size: var(--t-sm);
  transition: color var(--dur-1) var(--ease);
}

.board__list a:hover .bname {
  color: var(--sage-deep);
}

.board__list em {
  flex: none;
  width: 16px;
  font-style: normal;
  font-size: var(--t-xs);
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}

.board__list em.is-top {
  color: var(--pale-clay-ink);
  font-weight: 500;
}

.bname {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bcat {
  flex: none;
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.board__empty {
  padding: var(--s-5) 0;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.section {
  padding-block: var(--s-5) var(--s-6);
  border-top: 1px solid var(--line);
}

.section__head {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  margin-bottom: var(--s-4);
}

.section__head h2 {
  font-size: var(--t-lg);
  font-weight: 500;
}

.section__hint {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.section__more {
  margin-left: auto;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.section__more:hover {
  color: var(--ink);
}

.error {
  display: flex;
  align-items: center;
  gap: var(--s-4);
  margin-top: var(--s-5);
  padding: var(--s-4) var(--s-5);
  border: 1px solid var(--pale-red);
  border-radius: var(--r-m);
  background: var(--pale-red);
  color: var(--pale-red-ink);
  font-size: var(--t-sm);
}

@media (max-width: 1000px) {
  .banner {
    grid-template-columns: 1fr;
    gap: var(--s-5);
  }

  .board {
    padding-left: 0;
    border-left: none;
    border-top: 1px solid var(--line);
    padding-top: var(--s-4);
  }
}

@media (max-width: 640px) {
  .spot {
    gap: var(--s-4);
  }

  .spot__cover,
  .ph-cover {
    width: 108px;
  }

  .spot__info h1 {
    font-size: var(--t-xl);
  }

  .spot__stats {
    gap: var(--s-4);
  }

  .spot__stats li:nth-child(n + 3) {
    display: none;
  }
}
</style>
