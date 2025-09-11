<script setup>
defineProps({
  title: { type: String, default: '这里还没有内容' },
  desc: { type: String, default: '' }
})
</script>

<template>
  <div class="empty">
    <div class="spine" aria-hidden="true" />
    <h3 class="serif">{{ title }}</h3>
    <p v-if="desc" class="desc">{{ desc }}</p>
    <div class="slot">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--s-9) var(--s-5);
  text-align: center;
}

.spine {
  width: 34px;
  height: 46px;
  margin-bottom: var(--s-5);
  border: 1px solid var(--line-strong);
  border-radius: 3px;
  background: var(--surface-sunken);
}

h3 {
  font-size: var(--t-lg);
  letter-spacing: 0.02em;
}

.desc {
  margin-top: 8px;
  max-width: 320px;
  font-size: var(--t-sm);
  line-height: 1.9;
  color: var(--ink-3);
}

.slot:not(:empty) {
  margin-top: var(--s-5);
}
</style>
