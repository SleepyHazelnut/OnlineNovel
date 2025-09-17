<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { auth } from '@/api/client'
import { userApi, captchaUrl } from '@/api'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const toast = useToast()

const form = reactive({ username: '', password: '', captcha: '' })
const captcha = ref(captchaUrl())
const loading = ref(false)
const error = ref('')

function refreshCaptcha() {
  captcha.value = captchaUrl()
  form.captcha = ''
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    error.value = '请填写用户名和密码'
    return
  }
  if (!form.captcha.trim()) {
    error.value = '请填写验证码'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const data = await userApi.login({
      username: form.username.trim(),
      password: form.password,
      captcha: form.captcha.trim()
    })
    auth.save(data?.token, data?.user)
    toast.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect.startsWith('/') ? redirect : '/')
  } catch (err) {
    error.value = err.message || '登录失败'
    refreshCaptcha()
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth">
    <section class="panel">
      <div class="panel-inner">
        <div class="eyebrow">墨屿小说</div>
        <blockquote class="serif">
          追更不迷路，<br />
          读到哪都记得。
        </blockquote>
        <p class="note">登录后书架、阅读进度和评论跟随账号，手机与电脑随时接上。</p>
      </div>
      <div class="shelf-art" aria-hidden="true">
        <span v-for="i in 7" :key="i" :style="{ height: `${70 + ((i * 17) % 46)}px` }" />
      </div>
    </section>

    <section class="form-side">
      <div class="form-card">
        <RouterLink to="/" class="back">← 回到首页</RouterLink>
        <h1 class="serif">登录</h1>
        <p class="sub">还没有账号？<RouterLink to="/register" class="link">注册一个</RouterLink></p>

        <form @submit.prevent="submit">
          <div class="form-row">
            <label class="label" for="username">用户名</label>
            <input id="username" v-model="form.username" class="field" autocomplete="username" placeholder="4 到 20 个字符" />
          </div>

          <div class="form-row">
            <label class="label" for="password">密码</label>
            <input
              id="password"
              v-model="form.password"
              class="field"
              type="password"
              autocomplete="current-password"
              placeholder="至少 6 位"
            />
          </div>

          <div class="form-row">
            <label class="label" for="captcha">验证码</label>
            <div class="captcha-row">
              <input id="captcha" v-model="form.captcha" class="field" autocomplete="off" placeholder="输入图中的字符" />
              <button class="captcha" type="button" aria-label="刷新验证码" @click="refreshCaptcha">
                <img :src="captcha" alt="验证码" />
              </button>
            </div>
          </div>

          <p v-if="error" class="error">{{ error }}</p>

          <button class="btn btn--solid submit" type="submit" :disabled="loading">
            {{ loading ? '登录中' : '登录' }}
            <span class="btn__icon" aria-hidden="true">↗</span>
          </button>
        </form>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: 100vh;
  background: var(--paper);
}

.panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: var(--s-9) var(--s-8);
  overflow: hidden;
  border-right: 1px solid var(--line);
  background: var(--paper-warm);
}

.panel-inner {
  max-width: 380px;
}

blockquote {
  margin-top: var(--s-5);
  font-size: clamp(24px, 2.6vw, 34px);
  line-height: 1.6;
  letter-spacing: -0.01em;
}

.note {
  margin-top: var(--s-5);
  max-width: 320px;
  font-size: var(--t-sm);
  line-height: 1.9;
  color: var(--ink-3);
}

.shelf-art {
  position: absolute;
  left: var(--s-8);
  right: var(--s-8);
  bottom: 48px;
  display: flex;
  align-items: flex-end;
  gap: 6px;
  opacity: 0.5;
}

.shelf-art span {
  flex: 1;
  border: 1px solid var(--line-strong);
  border-radius: 2px 2px 0 0;
  background: var(--surface-sunken);
}

.shelf-art span:nth-child(3n) {
  background: var(--sage-soft);
  border-color: var(--sage-line);
}

.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--s-8) var(--gutter);
}

.form-card {
  width: 100%;
  max-width: 360px;
}

.back {
  font-size: var(--t-sm);
  color: var(--ink-3);
  transition: color var(--dur-1) var(--ease);
}

.back:hover {
  color: var(--ink);
}

h1 {
  margin-top: var(--s-5);
  font-size: var(--t-2xl);
}

.sub {
  margin-bottom: var(--s-6);
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.link {
  color: var(--sage-deep);
  border-bottom: 1px solid var(--sage-line);
}

.captcha-row {
  display: flex;
  gap: var(--s-3);
}

.captcha-row .field {
  flex: 1;
}

.captcha {
  flex: none;
  width: 104px;
  height: 44px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-m);
  overflow: hidden;
  background: var(--surface);
}

.captcha img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.error {
  margin-top: var(--s-3);
  font-size: var(--t-sm);
  color: var(--pale-red-ink);
}

.submit {
  width: 100%;
  margin-top: var(--s-5);
}

@media (max-width: 880px) {
  .auth {
    grid-template-columns: 1fr;
  }

  .panel {
    display: none;
  }

  .form-side {
    min-height: 100vh;
  }
}
</style>
