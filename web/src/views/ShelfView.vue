<script setup>
import { onMounted, reactive } from 'vue'
import { shelfApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { useCategories } from '@/composables/useCategories'
import { fmtRelative } from '@/utils/format'
import BookCover from '@/components/BookCover.vue'
import EmptyState from '@/components/EmptyState.vue'

const toast = useToast()
const { nameOf, ensure: ensureCategories } = useCategories()
const state = reactive({ loading: true, error: '', items: [], busyId: null })

async function load() {
  state.loading = true
  state.error = ''
  try {
    state.items = (await shelfApi.list()) || []
  } catch (err) {
    state.error = err.message || '加载失败'
  } finally {
    state.loading = false
  }
}

async function remove(item) {
  state.busyId = item.bookId
  try {
    await shelfApi.remove(item.bookId)
    state.items = state.items.filter((row) => row.bookId !== item.bookId)
    toast.info('已移出书架')
  } catch (err) {
    toast.error(err.message || '操作失败')
  } finally {
    state.busyId = null
  }
}

onMounted(() => {
  ensureCategories()
  load()
})
</script>

<template>
  <div class="shelf container">
    <header class="head">
      <h1>我的书架</h1>
      <p class="count mono">{{ state.loading ? '—' : state.items.length }} 部作品</p>
    </header>

    <div v-if="state.loading" class="grid">
      <div v-for="i in 5" :key="i">
        <div class="skeleton sk-cover" />
        <div class="skeleton sk-line" />
      </div>
    </div>

    <div v-else-if="state.error" class="error">
      <span>{{ state.error }}</span>
      <button class="btn btn--sm" type="button" @click="load">重试</button>
    </div>

    <EmptyState v-else-if="!state.items.length" title="书架还是空的" desc="在作品页点「加入书架」，读过的书会留在这里。">
      <RouterLink to="/library" class="btn btn--sm btn--solid">
        去书库逛逛
        <span class="btn__icon" aria-hidden="true">↗</span>
      </RouterLink>
    </EmptyState>

    <ul v-else class="grid">
      <li v-for="item in state.items" :key="item.id" v-reveal class="cell">
        <RouterLink :to="`/books/${item.bookId}`" class="cover">
          <BookCover :book="item" :category="nameOf(item.categoryId)" />
        </RouterLink>
        <div class="info">
          <RouterLink :to="`/books/${item.bookId}`" class="name">{{ item.bookName }}</RouterLink>
          <div class="meta">{{ item.authorName || '佚名' }}</div>
          <div class="foot">
            <span class="hint">收藏于 {{ fmtRelative(item.createTime) }}</span>
            <button
              class="remove"
              type="button"
              :disabled="state.busyId === item.bookId"
              @click="remove(item)"
            >
              移除
            </button>
          </div>
        </div>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.shelf {
  padding-top: var(--s-5);
  min-height: 55vh;
}

.head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: var(--s-3);
  border-bottom: 1px solid var(--line);
  margin-bottom: var(--s-5);
}

.head h1 {
  font-size: var(--t-lg);
  font-weight: 500;
}

.count {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--s-6) var(--s-4);
}

.cover {
  display: block;
  border-radius: var(--r-s);
  overflow: hidden;
  transition: transform var(--dur-2) var(--ease-swift);
}

.cell:hover .cover {
  transform: translateY(-4px);
}

.info {
  padding-top: 10px;
}

.name {
  display: block;
  font-size: var(--t-base);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.meta {
  margin-top: 2px;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--line);
}

.remove {
  font-size: var(--t-xs);
  color: var(--ink-3);
  opacity: 0.6;
  transition: opacity var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.cell:hover .remove,
.remove:focus-visible {
  opacity: 1;
}

.remove:hover {
  color: var(--pale-red-ink);
}

.sk-cover {
  aspect-ratio: 3 / 4;
  border-radius: var(--r-s);
}

.sk-line {
  height: 12px;
  width: 70%;
  margin-top: 10px;
}

.error {
  display: flex;
  align-items: center;
  gap: var(--s-4);
  padding: var(--s-3) var(--s-4);
  border-radius: var(--r-m);
  background: var(--pale-red);
  color: var(--pale-red-ink);
  font-size: var(--t-sm);
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .remove {
    opacity: 1;
  }
}
</style>
