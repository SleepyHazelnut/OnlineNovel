<script setup>
import { computed } from 'vue'

const props = defineProps({
  page: { type: Number, default: 1 },
  pageSize: { type: Number, default: 10 },
  total: { type: Number, default: 0 }
})

const emit = defineEmits(['change'])

const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.pageSize)))

const items = computed(() => {
  const count = pageCount.value
  const current = props.page
  if (count <= 7) return Array.from({ length: count }, (_, i) => i + 1)

  const set = new Set([1, count, current, current - 1, current + 1])
  const list = [...set].filter((n) => n >= 1 && n <= count).sort((a, b) => a - b)

  const result = []
  list.forEach((n, i) => {
    if (i > 0 && n - list[i - 1] > 1) result.push('...')
    result.push(n)
  })
  return result
})

function go(target) {
  if (typeof target !== 'number') return
  if (target === props.page || target < 1 || target > pageCount.value) return
  emit('change', target)
}
</script>

<template>
  <nav v-if="pageCount > 1" class="pager" aria-label="分页">
    <button class="step" type="button" :disabled="page === 1" @click="go(page - 1)">上一页</button>
    <button
      v-for="(item, i) in items"
      :key="`${item}-${i}`"
      class="num"
      :class="{ 'is-current': item === page, 'is-gap': item === '...' }"
      type="button"
      :disabled="item === '...'"
      @click="go(item)"
    >
      {{ item }}
    </button>
    <button class="step" type="button" :disabled="page === pageCount" @click="go(page + 1)">下一页</button>
  </nav>
</template>

<style scoped>
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: var(--s-7);
}

.num,
.step {
  height: 32px;
  min-width: 32px;
  padding: 0 10px;
  border-radius: var(--r-s);
  font-size: var(--t-sm);
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
  transition: background var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.num:hover,
.step:hover {
  background: var(--surface-sunken);
  color: var(--ink);
}

.num.is-current {
  background: var(--ink);
  color: var(--ink-inverse);
}

.num.is-gap {
  color: var(--ink-3);
  pointer-events: none;
}

.step[disabled] {
  color: var(--ink-3);
  opacity: 0.5;
  pointer-events: none;
}
</style>
