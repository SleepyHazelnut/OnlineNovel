<template>
  <footer class="foot">
    <div class="container top">
      <div class="brand-col">
        <div class="brand">
          <span class="mark" aria-hidden="true" />
          <span class="serif name">墨屿小说</span>
        </div>
        <p class="desc">按分类找书，按章节追更，阅读进度与书架跟随账号。</p>
      </div>

      <div class="link-col">
        <h3>浏览</h3>
        <RouterLink to="/library">书库</RouterLink>
        <RouterLink to="/rank">榜单</RouterLink>
        <RouterLink to="/shelf">书架</RouterLink>
      </div>

      <div class="link-col">
        <h3>技术</h3>
        <span>Spring Cloud Alibaba</span>
        <span>Vue 3 + Vite</span>
        <span>MySQL · Redis · Elasticsearch</span>
      </div>
    </div>

    <div class="container bottom">
      <span>© 2025 墨屿小说</span>
      <span class="mono">v1.0</span>
    </div>
  </footer>
</template>

<style scoped>
.foot {
  margin-top: var(--s-9);
  border-top: 1px solid var(--line);
  padding-top: var(--s-7);
  padding-bottom: var(--s-6);
}

.top {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr;
  gap: var(--s-7);
}

.brand {
  display: flex;
  align-items: center;
  gap: 9px;
}

.mark {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: var(--sage-soft);
  border: 1px solid var(--sage-line);
}

.name {
  font-size: var(--t-md);
  letter-spacing: 0.12em;
}

.desc {
  margin-top: var(--s-3);
  max-width: 300px;
  font-size: var(--t-sm);
  line-height: 1.9;
  color: var(--ink-3);
}

.link-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: var(--t-sm);
  color: var(--ink-3);
}

.link-col h3 {
  margin-bottom: 4px;
  font-size: var(--t-xs);
  letter-spacing: 0.16em;
  color: var(--ink-3);
  font-weight: 400;
}

.link-col a {
  transition: color var(--dur-1) var(--ease);
}

.link-col a:hover {
  color: var(--ink);
}

.bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: var(--s-7);
  padding-top: var(--s-4);
  border-top: 1px solid var(--line);
  font-size: var(--t-xs);
  color: var(--ink-3);
}

@media (max-width: 760px) {
  .top {
    grid-template-columns: 1fr 1fr;
    gap: var(--s-5);
  }

  .brand-col {
    grid-column: 1 / -1;
  }
}
</style>
