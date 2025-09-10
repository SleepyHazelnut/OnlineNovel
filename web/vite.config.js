import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const GATEWAY = 'http://localhost:8080'

const prefixes = [
  'user',
  'sign',
  'captcha',
  'author',
  'admin',
  'book',
  'chapter',
  'category',
  'comment',
  'rating',
  'rank',
  'home',
  'bookshelf',
  'history',
  'file',
  'upload'
]

const proxy = {}
for (const prefix of prefixes) {
  proxy[`^/${prefix}/`] = { target: GATEWAY, changeOrigin: true }
}

export default defineConfig(({ mode }) => {
  const forGateway = mode === 'gateway'

  return {
    base: forGateway ? '/app/' : '/',
    plugins: [vue()],
    resolve: {
      alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
    },
    server: {
      port: 5173,
      proxy
    },
    build: forGateway
      ? {
          outDir: '../inkisle-gateway/src/main/resources/static/app',
          emptyOutDir: true
        }
      : {
          outDir: 'dist',
          emptyOutDir: true
        }
  }
})
