<script setup>
import { computed } from 'vue'
import BookCover from './BookCover.vue'
import { fmtCount, scoreText, bookStatusText } from '@/utils/format'
import { useCategories } from '@/composables/useCategories'

const props = defineProps({
  book: { type: Object, required: true },
  rank: { type: Number, default: 0 }
})

const { nameOf } = useCategories()

const id = computed(() => props.book.id ?? props.book.bookId)
const name = computed(() => props.book.bookName || '未命名')
const category = computed(() => props.book.categoryName || nameOf(props.book.categoryId))
const finished = computed(() => Number(props.book.status) === 1)
/* 榜单接口的条目不一定带 status，缺失时宁可不显示，也别一律标成「连载中」 */
const hasStatus = computed(() => props.book.status !== undefined && props.book.status !== null && props.book.status !== '')
</script>

<template>
  <RouterLink :to="`/books/${id}`" class="card">
    <div class="frame">
      <BookCover :book="book" :category="category" />
      <span v-if="rank && rank <= 3" class="badge mono">{{ String(rank).padStart(2, '0') }}</span>
      <span v-if="hasStatus" class="state" :class="{ done: finished }">{{ bookStatusText(book.status) }}</span>
    </div>
    <div class="body">
      <h3 class="name">{{ name }}</h3>
      <p class="sub">
        <span class="author">{{ book.authorName || '佚名' }}</span>
      </p>
      <p class="meta">
        <span>{{ scoreText(book.score) }}</span>
        <span class="sep">·</span>
        <span>{{ fmtCount(book.wordCount) }} 字</span>
        <span v-if="book.visitCount" class="visit">{{ fmtCount(book.visitCount) }} 阅读</span>
      </p>
    </div>
  </RouterLink>
</template>

<style scoped>
.card {
  display: block;
  min-width: 0;
}

.frame {
  position: relative;
  transition: transform var(--dur-2) var(--ease-swift), filter var(--dur-2) var(--ease);
}

.badge {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 4;
  padding: 1px 7px 2px;
  border-radius: 5px 0 4px 0;
  background: rgba(251, 250, 247, 0.94);
  color: var(--ink);
  font-size: var(--t-xs);
  font-weight: 500;
}

.badge + .state {
  top: auto;
}

.state {
  position: absolute;
  right: 6px;
  top: 6px;
  z-index: 4;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(20, 20, 19, 0.42);
  color: #f6f4ee;
  font-size: var(--t-xs);
  letter-spacing: 0.04em;
  backdrop-filter: none;
}

.state.done {
  background: rgba(251, 250, 247, 0.92);
  color: var(--ink);
}

/* 悬停只在真正有指针的设备上生效，触屏点按会误触发 hover */
@media (hover: hover) and (pointer: fine) {
  .card:hover .frame {
    transform: translateY(-6px);
    filter: drop-shadow(0 14px 20px rgba(47, 52, 55, 0.16));
  }

  .card:hover .name {
    color: var(--sage-deep);
  }

  .card:hover :deep(.title) {
    transform: translateY(-2px);
  }
}

.card :deep(.title) {
  transition: transform var(--dur-2) var(--ease-swift);
}

@media (prefers-reduced-motion: reduce) {
  .card:hover .frame,
  .card:hover :deep(.title) {
    transform: none;
  }
}

.body {
  padding-top: 9px;
}

.name {
  font-size: var(--t-base);
  font-weight: 400;
  line-height: 1.45;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  transition: color var(--dur-1) var(--ease);
}

.sub {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 3px;
  font-size: var(--t-xs);
  color: var(--ink-3);
  overflow: hidden;
  white-space: nowrap;
}

.author {
  overflow: hidden;
  text-overflow: ellipsis;
}

.meta {
  display: flex;
  align-items: center;
  gap: 5px;
  margin-top: 5px;
  font-size: var(--t-xs);
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
  overflow: hidden;
}

.sep {
  color: var(--line-strong);
}

.visit {
  margin-left: auto;
  color: var(--ink-3);
}
</style>
