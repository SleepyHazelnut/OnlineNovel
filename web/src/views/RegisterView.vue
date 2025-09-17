<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '@/api'
import { useToast } from '@/composables/useToast'

const router = useRouter()
const toast = useToast()

const form = reactive({ username: '', password: '', confirm: '' })
const loading = ref(false)
const error = ref('')

async function submit() {
  const username = form.username.trim()
  if (username.length < 4 || username.length > 20) {
    error.value = '用户名长度要在 4 到 20 之间'
    return
  }
  if (form.password.length < 6 || form.password.length > 20) {
    error.value = '密码长度要在 6 到 20 之间'
    return
  }
  if (form.password !== form.confirm) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  error.value = ''
  try {
    await userApi.register({ username, password: form.password })
    toast.success('注册成功，请登录')
    router.replace({ name: 'login', query: { username } })
  } catch (err) {
    error.value = err.message || '注册失败'
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
          从一本书开始，<br />
          慢慢攒一个书架。
        </blockquote>
        <ul class="points">
          <li>书架与阅读进度跟随账号</li>
          <li>可以给作品打分、写评论</li>
          <li>作者端另有独立登录入口</li>
        </ul>
      </div>
    </section>

    <section class="form-side">
      <div class="form-card">
        <RouterLink to="/" class="back">← 回到首页</RouterLink>
        <h1 class="serif">注册</h1>
        <p class="sub">已有账号？<RouterLink to="/login" class="link">直接登录</RouterLink></p>

        <form @submit.prevent="submit">
          <div class="form-row">
            <label class="label" for="reg-username">用户名</label>
            <input
              id="reg-username"
              v-model="form.username"
              class="field"
              autocomplete="username"
              placeholder="4 到 20 个字符"
            />
          </div>

          <div class="form-row">
            <label class="label" for="reg-password">密码</label>
            <input
              id="reg-password"
              v-model="form.password"
              class="field"
              type="password"
              autocomplete="new-password"
              placeholder="6 到 20 位"
            />
          </div>

          <div class="form-row">
            <label class="label" for="reg-confirm">确认密码</label>
            <input
              id="reg-confirm"
              v-model="form.confirm"
              class="field"
              type="password"
              autocomplete="new-password"
              placeholder="再输入一次"
            />
          </div>

          <p v-if="error" class="error">{{ error }}</p>

          <button class="btn btn--solid submit" type="submit" :disabled="loading">
            {{ loading ? '提交中' : '创建账号' }}
            <span class="btn__icon" aria-hidden="true">↗</span>
          </button>
        </form>

        <p class="agree">注册即表示同意站内的内容规范，作品与评论都会经过审核。</p>
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
  display: flex;
  align-items: center;
  padding: var(--s-9) var(--s-8);
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
}

.points {
  margin-top: var(--s-6);
  display: flex;
  flex-direction: column;
  gap: 10px;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.points li {
  display: flex;
  align-items: center;
  gap: 8px;
}

.points li::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--sage);
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

.error {
  margin-top: var(--s-3);
  font-size: var(--t-sm);
  color: var(--pale-red-ink);
}

.submit {
  width: 100%;
  margin-top: var(--s-5);
}

.agree {
  margin-top: var(--s-5);
  font-size: var(--t-xs);
  line-height: 1.9;
  color: var(--ink-3);
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
