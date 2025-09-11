<script setup>
import { useRoute } from 'vue-router'
import SiteHeader from '@/components/SiteHeader.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import ToastHost from '@/components/ToastHost.vue'

const route = useRoute()
</script>

<template>
  <SiteHeader v-if="!route.meta.bare" />
  <main :class="{ 'is-bare': route.meta.bare }">
    <RouterView v-slot="{ Component }">
      <Transition name="page" mode="out-in">
        <component :is="Component" />
      </Transition>
    </RouterView>
  </main>
  <SiteFooter v-if="!route.meta.bare" />
  <ToastHost />
</template>

<style scoped>
main.is-bare {
  min-height: 100vh;
}

.page-enter-active {
  transition: opacity 200ms var(--ease), transform 200ms var(--ease);
}

.page-leave-active {
  transition: opacity 90ms var(--ease);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-leave-to {
  opacity: 0;
}
</style>
