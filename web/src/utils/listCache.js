/* 列表页的内存快照：返回时先铺回数据再对齐滚动位置，不必等接口也不会被骨架屏裁掉。
   只放首页/书库/榜单，阅读页要靠 onMounted 拉章节，缓存了反而拿到旧正文。 */
const store = new Map()

export function readList(key) {
  return store.get(key) || null
}

/* signature 用来标识当前筛选条件：换了页码或分类就把滚动位置归零，别沿用上一次的 */
export function writeList(key, data, signature = '') {
  const prev = store.get(key)
  store.set(key, {
    data,
    signature,
    scrollY: prev && prev.signature === signature ? prev.scrollY : 0
  })
}

/* 在 onBeforeRouteLeave 里调用：此时路由还没切换，window.scrollY 还是本页的位置 */
export function rememberScroll(key) {
  const entry = store.get(key)
  if (entry) entry.scrollY = window.scrollY
}
