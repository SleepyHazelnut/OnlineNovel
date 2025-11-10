import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { setRouter } from './api/client'
import { hydrate } from './stores/session'
import reveal from './directives/reveal'
import './styles/base.css'

/* 先恢复登录态再挂载，首次导航的判断和头部渲染都能直接读到 */
hydrate()

setRouter(router)

createApp(App).use(router).directive('reveal', reveal).mount('#app')
