import { createRouter, createWebHistory } from 'vue-router'
import { auth, setRole, roleOf } from '@/api/client'
import { useToast } from '@/composables/useToast'

const routes = [
  { path: '/', name: 'home', component: () => import('@/views/HomeView.vue'), meta: { title: '首页' } },
  { path: '/library', name: 'library', component: () => import('@/views/LibraryView.vue'), meta: { title: '书库' } },
  { path: '/books/:id', name: 'book', component: () => import('@/views/BookView.vue'), meta: { title: '作品' } },
  {
    path: '/read/:bookId/:chapterId?',
    name: 'read',
    component: () => import('@/views/ReadView.vue'),
    meta: { bare: true, title: '阅读' }
  },
  { path: '/rank', name: 'rank', component: () => import('@/views/RankView.vue'), meta: { title: '榜单' } },
  {
    path: '/shelf',
    name: 'shelf',
    component: () => import('@/views/ShelfView.vue'),
    meta: { title: '书架', auth: true }
  },
  {
    path: '/me',
    name: 'me',
    component: () => import('@/views/MeView.vue'),
    meta: { title: '个人中心', auth: true }
  },
  { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue'), meta: { bare: true, title: '登录' } },
  {
    path: '/register',
    name: 'register',
    component: () => import('@/views/RegisterView.vue'),
    meta: { bare: true, title: '注册' }
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, saved) {
    if (saved) return saved
    if (to.name === from.name) {
      /* 阅读页翻章由页面自己控制滚动，其余同页跳转只在换页或换筛选条件时回顶 */
      if (to.name === 'read') return {}
      const changed = ['page', 'keyword', 'categoryId'].some((key) => to.query[key] !== from.query[key])
      return changed ? { top: 0 } : {}
    }
    return { top: 0 }
  }
})

router.beforeEach((to) => {
  setRole(roleOf(to.path))
  document.title = to.meta.title ? `${to.meta.title} · 墨屿小说` : '墨屿小说'

  if (to.meta.auth && !auth.token()) {
    useToast().error('请先登录')
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
