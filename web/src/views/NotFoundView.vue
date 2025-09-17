<template>
  <div class="nf container">
    <div class="code mono">404</div>
    <h1 class="serif">这一页不在书里</h1>
    <p class="desc">链接可能过期了，或者作品已经下架。</p>
    <div class="actions">
      <RouterLink to="/" class="btn btn--solid">
        回到首页
        <span class="btn__icon" aria-hidden="true">↗</span>
      </RouterLink>
      <RouterLink to="/library" class="btn">去书库</RouterLink>
    </div>
  </div>
</template>

<style scoped>
.nf {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  min-height: 62vh;
  padding-top: var(--s-8);
}

.code {
  font-size: 64px;
  line-height: 1;
  color: var(--line-strong);
  letter-spacing: -0.04em;
}

h1 {
  margin-top: var(--s-4);
  font-size: var(--t-2xl);
}

.desc {
  margin-top: var(--s-3);
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.actions {
  display: flex;
  gap: var(--s-3);
  margin-top: var(--s-6);
}
</style>
