<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { session, clearSession } from '@/stores/session'

const route = useRoute()
const router = useRouter()

const menu = ref(false)
const sheet = ref(false)
const scrolled = ref(false)

/* 直接读 store，资料页改完昵称头像首字立刻跟着变，不再等下一次路由变化 */
const user = computed(() => session.user)
const logged = computed(() => Boolean(session.token && user.value))
const initial = computed(() => (user.value?.nickName || user.value?.username || '读').slice(0, 1))
const navs = [
  { label: '书库', to: '/library' },
  { label: '榜单', to: '/rank' },
  { label: '书架', to: '/shelf' }
]

function onScroll() {
  scrolled.value = window.scrollY > 12
}

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  document.body.style.overflow = ''
})

watch(
  () => route.fullPath,
  () => {
    menu.value = false
    sheet.value = false
  }
)

watch(sheet, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
})

function isActive(to) {
  return to === '/' ? route.path === '/' : route.path.startsWith(to)
}

function logout() {
  clearSession()
  menu.value = false
  router.push('/')
}
</script>

<template>
  <div class="header-wrap">
    <header class="bar" :class="{ 'is-scrolled': scrolled }">
      <RouterLink to="/" class="brand" aria-label="墨屿小说首页">
        <span class="mark" aria-hidden="true" />
        <span class="word">墨屿小说</span>
      </RouterLink>

      <nav class="nav" aria-label="主导航">
        <RouterLink
          v-for="item in navs"
          :key="item.to"
          :to="item.to"
          class="nav-link"
          :class="{ 'is-active': isActive(item.to) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="tail">
        <RouterLink to="/library" class="search" aria-label="搜索作品">
          <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
            <circle cx="6.8" cy="6.8" r="4.6" stroke="currentColor" stroke-width="1.2" />
            <path d="M10.4 10.4L14 14" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" />
          </svg>
          <span>搜索</span>
        </RouterLink>

        <RouterLink v-if="!logged" to="/login" class="signin">登录</RouterLink>

        <div v-else class="account">
          <button class="avatar" type="button" aria-label="账户菜单" @click="menu = !menu">
            {{ initial }}
          </button>
          <Transition name="pop">
            <div v-if="menu" class="menu">
              <RouterLink to="/me" class="menu-item">个人中心</RouterLink>
              <RouterLink to="/shelf" class="menu-item">我的书架</RouterLink>
              <button class="menu-item is-danger" type="button" @click="logout">退出登录</button>
            </div>
          </Transition>
        </div>

        <button class="burger" type="button" aria-label="打开菜单" :aria-expanded="sheet" @click="sheet = true">
          <span /><span />
        </button>
      </div>
    </header>

    <Transition name="sheet">
      <div v-if="sheet" class="sheet">
        <div class="sheet-top">
          <span class="eyebrow">墨屿小说</span>
          <button class="sheet-close" type="button" aria-label="关闭菜单" @click="sheet = false">
            <span /><span />
          </button>
        </div>
        <nav class="sheet-nav">
          <RouterLink
            v-for="(item, i) in navs"
            :key="item.to"
            :to="item.to"
            class="sheet-link"
            :style="{ transitionDelay: `${60 + i * 40}ms` }"
          >
            {{ item.label }}
          </RouterLink>
          <RouterLink
            :to="logged ? '/me' : '/login'"
            class="sheet-link"
            :style="{ transitionDelay: `${60 + navs.length * 40}ms` }"
          >
            {{ logged ? '个人中心' : '登录' }}
          </RouterLink>
        </nav>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.header-wrap {
  position: sticky;
  top: 0;
  z-index: 30;
  padding: 14px var(--gutter) 0;
  pointer-events: none;
}

.bar {
  pointer-events: auto;
  display: flex;
  align-items: center;
  gap: var(--s-5);
  width: 100%;
  max-width: var(--page);
  height: 54px;
  margin-inline: auto;
  padding: 0 8px 0 20px;
  border: 1px solid var(--line);
  border-radius: var(--r-pill);
  background: rgba(247, 246, 243, 0.78);
  backdrop-filter: blur(14px) saturate(1.1);
  transition: height var(--dur-2) var(--ease), box-shadow var(--dur-2) var(--ease),
    background var(--dur-2) var(--ease);
}

.bar.is-scrolled {
  height: 48px;
  background: rgba(247, 246, 243, 0.92);
  box-shadow: var(--shadow-float);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
}

.mark {
  position: relative;
  width: 20px;
  height: 20px;
  border: 1px solid var(--sage-line);
  border-radius: 50%;
  background: var(--sage-soft);
}

.mark::after {
  content: '';
  position: absolute;
  right: 4px;
  bottom: 4px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--sage);
}

.word {
  font-family: var(--font-serif);
  font-size: var(--t-md);
  letter-spacing: 0.12em;
}

.nav {
  display: flex;
  gap: var(--s-5);
}

.nav-link {
  font-size: var(--t-sm);
  color: var(--ink-2);
  padding: 5px 2px;
  border-bottom: 1px solid transparent;
  transition: color var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease);
}

.nav-link:hover {
  color: var(--ink);
}

.nav-link.is-active {
  color: var(--ink);
  border-bottom-color: var(--ink);
}

.tail {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--s-2);
}

.search {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 12px;
  border-radius: var(--r-pill);
  color: var(--ink-3);
  font-size: var(--t-sm);
  transition: color var(--dur-1) var(--ease), background var(--dur-1) var(--ease);
}

.search:hover {
  color: var(--ink);
  background: rgba(47, 52, 55, 0.05);
}

.signin {
  display: inline-flex;
  align-items: center;
  height: 32px;
  padding: 0 16px;
  font-size: var(--t-sm);
  border: 1px solid var(--line-strong);
  border-radius: var(--r-pill);
  transition: border-color var(--dur-1) var(--ease);
}

.signin:hover {
  border-color: var(--ink-3);
}

.account {
  position: relative;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--sage);
  color: var(--ink-inverse);
  font-size: var(--t-sm);
  transition: transform var(--dur-1) var(--spring);
}

.avatar:active {
  transform: scale(0.94);
}

.menu {
  position: absolute;
  right: 0;
  top: 40px;
  min-width: 136px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: var(--r-m);
  box-shadow: var(--shadow-float);
  /* 从头像那一角展开，而不是从自身中心凭空放大 */
  transform-origin: top right;
}

.menu-item {
  text-align: left;
  padding: 8px 10px;
  font-size: var(--t-sm);
  color: var(--ink-2);
  border-radius: var(--r-s);
  transition: background var(--dur-1) var(--ease), color var(--dur-1) var(--ease);
}

.menu-item:hover {
  background: var(--surface-sunken);
  color: var(--ink);
}

.menu-item.is-danger {
  color: var(--pale-red-ink);
}

.burger {
  display: none;
  width: 34px;
  height: 34px;
  padding: 10px 8px;
  flex-direction: column;
  justify-content: space-between;
}

.burger span {
  display: block;
  height: 1px;
  background: var(--ink);
}

.sheet {
  pointer-events: auto;
  position: fixed;
  inset: 0;
  z-index: 40;
  display: flex;
  flex-direction: column;
  padding: 20px var(--gutter) var(--s-7);
  background: rgba(247, 246, 243, 0.97);
  backdrop-filter: blur(18px);
}

.sheet-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sheet-close {
  position: relative;
  width: 34px;
  height: 34px;
}

.sheet-close span {
  position: absolute;
  left: 6px;
  top: 16px;
  width: 22px;
  height: 1px;
  background: var(--ink);
}

.sheet-close span:first-child {
  transform: rotate(45deg);
}

.sheet-close span:last-child {
  transform: rotate(-45deg);
}

.sheet-nav {
  display: flex;
  flex-direction: column;
  gap: var(--s-4);
  margin-top: var(--s-8);
}

.sheet-link {
  font-family: var(--font-serif);
  font-size: 30px;
  color: var(--ink);
  opacity: 1;
  transform: none;
  transition: opacity 320ms var(--ease), transform 320ms var(--ease-swift);
}

.sheet-enter-from .sheet-link {
  opacity: 0;
  transform: translateY(20px);
}

.sheet-enter-from,
.sheet-leave-to {
  opacity: 0;
}

.sheet-enter-active,
.sheet-leave-active {
  transition: opacity var(--dur-2) var(--ease);
}

.pop-enter-active,
.pop-leave-active {
  transition: opacity var(--dur-1) var(--ease), transform var(--dur-1) var(--ease-swift);
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

@media (max-width: 860px) {
  .nav,
  .search span,
  .signin {
    display: none;
  }

  .burger {
    display: flex;
  }

  .bar {
    padding-right: 6px;
  }
}
</style>
