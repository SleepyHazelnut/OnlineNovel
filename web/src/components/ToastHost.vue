<script setup>
import { useToast } from '@/composables/useToast'

const { state } = useToast()
</script>

<template>
  <div class="toast-host" aria-live="polite">
    <TransitionGroup name="toast">
      <div v-for="item in state.items" :key="item.id" class="toast" :class="`is-${item.type}`">
        {{ item.text }}
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-host {
  position: fixed;
  left: 50%;
  bottom: 32px;
  transform: translateX(-50%);
  z-index: 60;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  pointer-events: none;
}

.toast {
  padding: 9px 16px;
  border-radius: var(--r-pill);
  background: var(--ink);
  color: var(--ink-inverse);
  font-size: var(--t-sm);
  box-shadow: var(--shadow-float);
}

.toast.is-error {
  background: var(--pale-red-ink);
}

.toast.is-success {
  background: var(--sage-deep);
}

/* 提示从底部升起，退场沿原路返回；多条堆叠时同步位移 */
.toast-enter-active {
  transition: opacity 300ms ease, transform 300ms var(--ease-swift);
}

.toast-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.toast-enter-from {
  opacity: 0;
  transform: translateY(16px) scale(0.96);
}

.toast-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

.toast-move {
  transition: transform 240ms var(--ease-swift);
}
</style>
