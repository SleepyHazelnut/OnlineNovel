<script setup>
import { computed, ref, watch } from 'vue'
import { coverTone } from '@/utils/format'

const props = defineProps({
  book: { type: Object, required: true },
  category: { type: String, default: '' },
  size: { type: String, default: 'md' },
  /* 首屏大图标记为 priority：不做懒加载，否则 LCP 要等布局完成才开始下载 */
  priority: { type: Boolean, default: false }
})

const tone = computed(() => coverTone(props.book?.bookName, props.category))
const name = computed(() => props.book?.bookName || '未命名')
const cover = computed(() => props.book?.coverUrl || '')
const dense = computed(() => name.value.length >= 7)

/* 封面 404 时回退到组件自己的文字封面，而不是让浏览器露出破图 */
const failed = ref(false)
const showImage = computed(() => Boolean(cover.value) && !failed.value)

watch(cover, () => {
  failed.value = false
})

const titleStyle = computed(() => {
  const base = props.size === 'lg' ? 16 : props.size === 'sm' ? 11 : 12
  const len = name.value.length
  if (len <= 4) return { fontSize: `${base}px` }
  if (len <= 6) return { fontSize: `${base - 2}px`, letterSpacing: '0.14em' }
  if (len <= 8) return { fontSize: `${base - 3}px`, letterSpacing: '0.05em' }
  return { fontSize: `${base - 4}px`, letterSpacing: '0.02em' }
})

const vars = computed(() => ({
  '--base': tone.value.base,
  '--ink': tone.value.ink,
  '--light': tone.value.light,
  '--on': tone.value.on
}))
</script>

<template>
  <div class="bcover" :class="[`v${tone.variant}`, size, { 'is-light': !tone.deep, 'is-dense': dense }]" :style="vars">
    <img
      v-if="showImage"
      class="shot"
      :src="cover"
      :alt="name"
      :loading="priority ? 'eager' : 'lazy'"
      :fetchpriority="priority ? 'high' : 'auto'"
      decoding="async"
      @error="failed = true"
    />
    <template v-else>
      <span class="deco-a" aria-hidden="true" />
      <span class="deco-b" aria-hidden="true" />
      <span class="title" :style="titleStyle">{{ name }}</span>
    </template>
    <span v-if="category" class="foot">{{ category }}</span>
  </div>
</template>

<style scoped>
.bcover {
  position: relative;
  aspect-ratio: 3 / 4;
  border-radius: 5px;
  overflow: hidden;
  background: var(--base);
  display: flex;
  align-items: center;
  justify-content: center;
  isolation: isolate;
}

.shot {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.title {
  position: relative;
  z-index: 2;
  writing-mode: vertical-rl;
  max-height: 82%;
  overflow: hidden;
  padding-right: 2px;
  color: var(--on);
  font-family: var(--font-serif);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.24em;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.md .title {
  font-size: 12px;
}

.lg .title {
  font-size: 16px;
  letter-spacing: 0.3em;
}

.sm .title {
  font-size: 11px;
  letter-spacing: 0.2em;
}

.deco-a,
.deco-b {
  position: absolute;
  z-index: 1;
  pointer-events: none;
}

.v0 .deco-a {
  left: 0;
  right: 0;
  top: 0;
  height: 22%;
  background: var(--ink);
}

.v0 .deco-b {
  left: 18%;
  top: 28%;
  bottom: 20%;
  width: 1px;
  background: var(--light);
  opacity: 0.5;
}

.v1 .deco-a {
  left: 0;
  top: 0;
  bottom: 0;
  width: 19%;
  background: var(--ink);
}

.v1 .deco-b {
  left: 25%;
  right: 14%;
  top: 30%;
  height: 1px;
  background: var(--light);
  opacity: 0.5;
}

.v2 .deco-a {
  left: 50%;
  top: 9%;
  width: 32%;
  margin-left: -16%;
  aspect-ratio: 1;
  border: 1px solid var(--light);
  border-radius: 50%;
  opacity: 0.55;
}

.v2 .deco-b {
  left: 0;
  right: 0;
  top: 0;
  height: 7%;
  background: var(--ink);
}

.v3 .deco-a {
  left: 0;
  right: 0;
  bottom: 0;
  height: 26%;
  background: var(--ink);
}

.v3 .deco-b {
  left: 12%;
  right: 12%;
  top: 22%;
  height: 1px;
  background: var(--light);
  opacity: 0.5;
}

.v4 .deco-a {
  left: 0;
  right: 0;
  top: 0;
  height: 40%;
  background: var(--ink);
  clip-path: polygon(0 0, 100% 0, 100% 100%, 0 58%);
}

.v4 .deco-b {
  left: 0;
  right: 0;
  bottom: 27%;
  height: 1px;
  background: var(--light);
  opacity: 0.5;
}

.foot {
  position: absolute;
  z-index: 3;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 3px 7px;
  background: var(--ink);
  color: var(--light);
  font-size: 11px;
  letter-spacing: 0.08em;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.v1 .foot {
  left: 19%;
  padding-left: 6px;
  background: transparent;
}

.is-dense .title {
  font-size: 11px;
  letter-spacing: 0.12em;
}

.lg.is-dense .title {
  font-size: 13px;
  letter-spacing: 0.16em;
}

.sm.is-dense .title {
  font-size: 10px;
}
</style>
