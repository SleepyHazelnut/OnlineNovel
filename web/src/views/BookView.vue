<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { session } from '@/stores/session'
import { isAuthError } from '@/api/client'
import { bookApi, chapterApi, commentApi, ratingApi, shelfApi, historyApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { useCategories } from '@/composables/useCategories'
import { fmtCount, fmtRelative, scoreText, bookStatusText } from '@/utils/format'
import BookCover from '@/components/BookCover.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const toast = useToast()
const { nameOf, ensure: ensureCategories } = useCategories()

const bookId = computed(() => Number(route.params.id))
const logged = computed(() => Boolean(session.token))
const myId = computed(() => session.user?.id ?? null)

const state = reactive({
  loading: true,
  error: '',
  book: null,
  chapters: [],
  comments: [],
  rating: { score: 0, count: 0, myScore: 0 },
  collected: false,
  last: null
})

const tab = ref('catalog')
const expanded = ref(false)
const commentText = ref('')
const submitting = ref(false)
const shelfBusy = ref(false)

const categoryName = computed(() => nameOf(state.book?.categoryId))
/* 后端 score 已经是 0–5 的均分，不要再除 2 */
const ratingScore = computed(() => Math.max(0, Math.min(5, Number(state.rating.score || 0))))

/* 整星保底 floor，剩下的 0.5 及以上补半星 */
function starLevel(n) {
  if (n <= Math.floor(ratingScore.value)) return 'full'
  return ratingScore.value - (n - 1) >= 0.5 ? 'half' : 'empty'
}
const chapters = computed(() => state.chapters)
const commentTotal = computed(() =>
  state.comments.reduce((sum, item) => sum + 1 + (item.children?.length || 0), 0)
)
const firstChapter = computed(() => chapters.value[0] || null)
const readTarget = computed(() => {
  if (state.last?.chapterId) return state.last.chapterId
  return firstChapter.value?.id || null
})
const readLabel = computed(() => {
  if (state.last?.chapterNum) return `继续阅读 · 第 ${state.last.chapterNum} 章`
  return chapters.value.length ? '从第一章开始读' : '暂无章节'
})

let seq = 0

async function load() {
  const current = ++seq
  state.loading = true
  state.error = ''
  try {
    const book = await bookApi.detail(bookId.value)
    if (current !== seq) return
    state.book = book
    state.rating = { score: Number(book.score || 0), count: Number(book.ratingCount || 0), myScore: 0 }

    const [chapterList, commentList] = await Promise.all([
      chapterApi.list(bookId.value).catch(() => []),
      commentApi.list(bookId.value).catch(() => [])
    ])
    if (current !== seq) return
    state.chapters = chapterList || []
    state.comments = commentList || []

    if (logged.value) {
      const [summary, collected, last] = await Promise.all([
        ratingApi.summary(bookId.value).catch(() => null),
        shelfApi.check(bookId.value).catch(() => false),
        historyApi.last(bookId.value).catch(() => null)
      ])
      if (current !== seq) return
      if (summary) {
        state.rating = {
          score: Number(summary.score || 0),
          count: Number(summary.count ?? summary.ratingCount ?? 0),
          myScore: Number(summary.myScore || 0)
        }
      }
      state.collected = Boolean(collected)
      state.last = last || null
    }
  } catch (err) {
    if (current !== seq) return
    if (isAuthError(err)) return
    state.error = err.message || '加载失败'
  } finally {
    if (current === seq) state.loading = false
  }
}

/* 组件被复用时不重建，换书得手动清掉上一本的痕迹，否则会先闪一下旧数据 */
function reset() {
  state.book = null
  state.chapters = []
  state.comments = []
  state.rating = { score: 0, count: 0, myScore: 0 }
  state.collected = false
  state.last = null
  state.error = ''
  tab.value = 'catalog'
  expanded.value = false
  commentText.value = ''
}

watch(
  () => route.params.id,
  (value, old) => {
    if (!value || value === old) return
    reset()
    load()
  }
)

async function toggleShelf() {
  if (!logged.value) {
    toast.error('请先登录')
    return
  }
  if (shelfBusy.value) return
  shelfBusy.value = true
  try {
    if (state.collected) {
      await shelfApi.remove(bookId.value)
      state.collected = false
      toast.info('已从书架移除')
    } else {
      await shelfApi.add(bookId.value)
      state.collected = true
      toast.success('已加入书架')
    }
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '操作失败')
  } finally {
    shelfBusy.value = false
  }
}

async function rate(score) {
  if (!logged.value) {
    toast.error('登录后才能评分')
    return
  }
  try {
    const data = await ratingApi.rate(bookId.value, score)
    state.rating = {
      score: Number(data?.score ?? data?.avgScore ?? state.rating.score),
      count: Number(data?.count ?? data?.ratingCount ?? state.rating.count),
      myScore: score
    }
    toast.success(`已评 ${score} 分`)
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '评分失败')
  }
}

async function submitComment() {
  const content = commentText.value.trim()
  if (!content) return
  if (!logged.value) {
    toast.error('登录后才能评论')
    return
  }
  submitting.value = true
  try {
    await commentApi.add({ bookId: bookId.value, content })
    commentText.value = ''
    toast.success('评论已提交，待审核通过后展示')
    state.comments = (await commentApi.list(bookId.value)) || []
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '评论失败')
  } finally {
    submitting.value = false
  }
}

async function removeComment(id) {
  try {
    await commentApi.remove(id)
    state.comments = (await commentApi.list(bookId.value)) || []
    toast.info('评论已删除')
  } catch (err) {
    if (isAuthError(err)) return
    toast.error(err.message || '删除失败')
  }
}

onMounted(() => {
  ensureCategories()
  load()
})
</script>

<template>
  <div class="book container">
    <RouterLink to="/library" class="back">← 书库</RouterLink>

    <div v-if="state.loading" class="loading">
      <div class="skeleton sk-cover" />
      <div class="sk-body">
        <div class="skeleton sk-line lg" />
        <div class="skeleton sk-line" />
        <div class="skeleton sk-line short" />
      </div>
    </div>

    <EmptyState v-else-if="state.error" title="作品暂时打不开" :desc="state.error">
      <button class="btn btn--sm" type="button" @click="load">重试</button>
    </EmptyState>

    <template v-else-if="state.book">
      <section class="top">
        <div class="cover-col">
          <BookCover :book="state.book" :category="categoryName" size="lg" class="cover" priority />

          <div class="rating">
            <div class="score serif">{{ scoreText(state.rating.score) }}</div>
            <div class="stars" :aria-label="`评分 ${state.rating.score}`">
              <button
                v-for="n in 5"
                :key="n"
                class="star"
                :class="`is-${starLevel(n)}`"
                type="button"
                :aria-label="`评 ${n} 分`"
                @click="rate(n)"
              >
                ★
              </button>
            </div>
            <div class="rating-meta">
              {{ state.rating.count }} 人评分
              <template v-if="state.rating.myScore">· 我打了 {{ state.rating.myScore }} 分</template>
            </div>
          </div>
        </div>

        <div class="info">
          <div class="eyebrow">{{ categoryName || '作品' }}</div>
          <h1 class="serif">{{ state.book.bookName }}</h1>
          <div class="author-line">
            <span>{{ state.book.authorName || '佚名' }}</span>
            <span class="dot">·</span>
            <span>{{ bookStatusText(state.book.status) }}</span>
            <span class="dot">·</span>
            <span>{{ fmtCount(state.book.wordCount) }} 字</span>
            <span class="dot">·</span>
            <span>{{ fmtCount(state.book.visitCount) }} 次阅读</span>
          </div>

          <p class="desc" :class="{ 'is-open': expanded }">{{ state.book.bookDesc || '作者还没有填写简介。' }}</p>
          <button
            v-if="(state.book.bookDesc || '').length > 110"
            class="expand"
            type="button"
            @click="expanded = !expanded"
          >
            {{ expanded ? '收起' : '展开全文' }}
          </button>

          <div class="actions">
            <RouterLink
              v-if="readTarget"
              :to="`/read/${bookId}/${readTarget}`"
              class="btn btn--solid"
            >
              {{ readLabel }}
              <span class="btn__icon" aria-hidden="true">↗</span>
            </RouterLink>
            <button v-else class="btn" type="button" disabled>暂无章节</button>

            <button class="btn" type="button" :disabled="shelfBusy" @click="toggleShelf">
              {{ state.collected ? '已在书架' : '加入书架' }}
            </button>

            <span class="update hint">更新于 {{ fmtRelative(state.book.updateTime) }}</span>
          </div>
        </div>
      </section>

      <div class="tabs" role="tablist">
        <button
          class="tab"
          :class="{ 'is-on': tab === 'catalog' }"
          type="button"
          role="tab"
          :aria-selected="tab === 'catalog'"
          @click="tab = 'catalog'"
        >
          目录 <span class="mono">{{ chapters.length }}</span>
        </button>
        <button
          class="tab"
          :class="{ 'is-on': tab === 'comments' }"
          type="button"
          role="tab"
          :aria-selected="tab === 'comments'"
          @click="tab = 'comments'"
        >
          评论 <span class="mono">{{ commentTotal }}</span>
        </button>
      </div>

      <section v-if="tab === 'catalog'" class="catalog">
        <EmptyState v-if="!chapters.length" title="还没有发布章节" desc="作者尚未上传正文。" />
        <ol v-else class="chapter-list">
          <li v-for="chapter in chapters" :key="chapter.id">
            <RouterLink :to="`/read/${bookId}/${chapter.id}`" class="chapter">
              <span class="num mono">{{ String(chapter.chapterNum).padStart(2, '0') }}</span>
              <span class="cname">{{ chapter.chapterName }}</span>
              <span class="cmeta">{{ fmtCount(chapter.wordCount) }} 字</span>
              <span class="cdate">{{ fmtRelative(chapter.createTime) }}</span>
            </RouterLink>
          </li>
        </ol>
      </section>

      <section v-else class="comments">
        <div class="composer">
          <textarea
            v-model="commentText"
            class="field"
            rows="3"
            maxlength="500"
            :placeholder="logged ? '说说你读到哪了' : '登录后可以发表评论'"
            :disabled="!logged"
          />
          <div class="composer-foot">
            <span class="hint">{{ commentText.length }} / 500 · 评论需审核后展示</span>
            <button class="btn btn--sm btn--solid" type="button" :disabled="!logged || submitting" @click="submitComment">
              {{ submitting ? '提交中' : '发表' }}
            </button>
          </div>
        </div>

        <EmptyState v-if="!state.comments.length" title="还没有评论" desc="读完可以留下第一句话。" />

        <ul v-else class="comment-list">
          <li v-for="item in state.comments" :key="item.id" class="comment">
            <div class="avatar">{{ (item.nickName || '读').slice(0, 1) }}</div>
            <div class="cbody">
              <div class="chead">
                <span class="nick">{{ item.nickName || '读者' }}</span>
                <span class="time">{{ fmtRelative(item.createTime) }}</span>
                <button
                  v-if="logged && item.userId === myId"
                  class="del"
                  type="button"
                  @click="removeComment(item.id)"
                >
                  删除
                </button>
              </div>
              <p class="ctext">{{ item.content }}</p>
              <ul v-if="item.children?.length" class="children">
                <li v-for="child in item.children" :key="child.id" class="comment is-child">
                  <div class="avatar">{{ (child.nickName || '读').slice(0, 1) }}</div>
                  <div class="cbody">
                    <div class="chead">
                      <span class="nick">{{ child.nickName || '读者' }}</span>
                      <span class="time">{{ fmtRelative(child.createTime) }}</span>
                    </div>
                    <p class="ctext">{{ child.content }}</p>
                  </div>
                </li>
              </ul>
            </div>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<style scoped>
.book {
  padding-top: var(--s-6);
}

.back {
  display: inline-block;
  margin-bottom: var(--s-5);
  font-size: var(--t-sm);
  color: var(--ink-3);
  transition: color var(--dur-1) var(--ease);
}

.back:hover {
  color: var(--ink);
}

.loading {
  display: flex;
  gap: var(--s-6);
  padding-top: var(--s-5);
}

.sk-cover {
  width: 220px;
  aspect-ratio: 3 / 4;
  border-radius: var(--r-s);
}

.sk-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding-top: 12px;
}

.sk-line {
  height: 14px;
  width: 60%;
}

.sk-line.lg {
  height: 30px;
  width: 40%;
}

.sk-line.short {
  width: 30%;
}

.top {
  display: grid;
  grid-template-columns: 220px 1fr;
  gap: var(--s-7);
  align-items: start;
}

.cover {
  border: 1px solid rgba(47, 52, 55, 0.07);
  box-shadow: 0 12px 28px -18px rgba(47, 52, 55, 0.4);
}

.rating {
  margin-top: var(--s-4);
  padding-top: var(--s-4);
  border-top: 1px solid var(--line);
}

.score {
  font-size: 34px;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.stars {
  display: flex;
  gap: 2px;
  margin-top: 6px;
}

.star {
  font-size: 15px;
  color: var(--line-strong);
  transition: color var(--dur-1) var(--ease), transform var(--dur-1) var(--spring);
}

.star:hover {
  transform: scale(1.12);
}

.star.is-full {
  color: #b98a3c;
}

/* 半星用两段渐变裁在字面上，比额外套一层 span 少一半节点 */
.star.is-half {
  color: transparent;
  background-image: linear-gradient(90deg, #b98a3c 50%, var(--line-strong) 50%);
  -webkit-background-clip: text;
  background-clip: text;
}

.rating-meta {
  margin-top: 6px;
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.info h1 {
  margin-top: 8px;
  font-size: var(--t-2xl);
  line-height: 1.25;
}

.author-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: var(--s-3);
  font-size: var(--t-sm);
  color: var(--ink-2);
}

.dot {
  color: var(--line-strong);
}

.desc {
  margin-top: var(--s-5);
  max-width: 640px;
  font-size: var(--t-base);
  line-height: 2;
  color: var(--ink-2);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
}

.desc.is-open {
  display: block;
  -webkit-line-clamp: unset;
}

.expand {
  margin-top: 6px;
  font-size: var(--t-xs);
  color: var(--sage-deep);
}

.actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--s-3);
  margin-top: var(--s-6);
}

.update {
  margin-left: 4px;
}

.tabs {
  display: flex;
  gap: var(--s-5);
  margin-top: var(--s-8);
  border-bottom: 1px solid var(--line);
}

.tab {
  padding: 0 2px 12px;
  font-size: var(--t-base);
  color: var(--ink-3);
  border-bottom: 1px solid transparent;
  margin-bottom: -1px;
  transition: color var(--dur-1) var(--ease), border-color var(--dur-1) var(--ease);
}

.tab span {
  font-size: var(--t-xs);
  color: var(--ink-3);
  margin-left: 2px;
}

.tab:hover {
  color: var(--ink);
}

.tab.is-on {
  color: var(--ink);
  border-bottom-color: var(--ink);
}

.catalog,
.comments {
  padding-top: var(--s-5);
}

.chapter-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 var(--s-7);
}

.chapter {
  display: flex;
  align-items: baseline;
  gap: var(--s-3);
  padding: 12px 4px;
  border-bottom: 1px solid var(--line);
  transition: background var(--dur-1) var(--ease);
  /* 目录动辄上千条，先让浏览器跳过视口外的布局，高度按实测值占位避免滚动条跳动 */
  content-visibility: auto;
  contain-intrinsic-size: auto 48px;
}

.chapter:hover {
  background: var(--paper-warm);
}

.num {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.cname {
  flex: 1;
  min-width: 0;
  font-size: var(--t-base);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.cmeta,
.cdate {
  font-size: var(--t-xs);
  color: var(--ink-3);
  white-space: nowrap;
}

.cdate {
  min-width: 58px;
  text-align: right;
}

.composer {
  padding: var(--s-4);
  border: 1px solid var(--line);
  border-radius: var(--r-m);
  background: var(--surface);
}

.composer .field {
  border: none;
  padding: 0;
  background: transparent;
}

.composer .field:focus {
  box-shadow: none;
}

.composer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--s-3);
  margin-top: var(--s-3);
  padding-top: var(--s-3);
  border-top: 1px solid var(--line);
}

.comment-list {
  margin-top: var(--s-6);
}

.comment {
  display: flex;
  gap: var(--s-3);
  padding-block: var(--s-4);
  border-bottom: 1px solid var(--line);
}

.children .comment {
  border-bottom: none;
  padding-block: var(--s-3) 0;
}

.avatar {
  flex: none;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--sage-soft);
  color: var(--sage-deep);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--t-sm);
}

.cbody {
  flex: 1;
  min-width: 0;
}

.chead {
  display: flex;
  align-items: center;
  gap: var(--s-3);
}

.nick {
  font-size: var(--t-sm);
  color: var(--ink);
}

.time {
  font-size: var(--t-xs);
  color: var(--ink-3);
}

.del {
  margin-left: auto;
  font-size: var(--t-xs);
  color: var(--ink-3);
  transition: color var(--dur-1) var(--ease);
}

.del:hover {
  color: var(--pale-red-ink);
}

.ctext {
  margin-top: 4px;
  font-size: var(--t-base);
  line-height: 1.9;
  color: var(--ink-2);
  white-space: pre-wrap;
  word-break: break-word;
}

.children {
  margin-top: var(--s-3);
  padding-left: var(--s-4);
  border-left: 1px solid var(--line);
}

@media (max-width: 760px) {
  .top {
    grid-template-columns: 140px 1fr;
    gap: var(--s-5);
  }

  .info h1 {
    font-size: var(--t-xl);
  }

  .chapter-list {
    grid-template-columns: 1fr;
  }

  .cdate {
    display: none;
  }
}

@media (max-width: 480px) {
  .top {
    grid-template-columns: 1fr;
  }

  .cover-col {
    display: flex;
    gap: var(--s-4);
    align-items: flex-start;
  }

  .cover {
    width: 120px;
    flex: none;
  }

  .rating {
    margin-top: 0;
    padding-top: 0;
    border-top: none;
    flex: 1;
  }

  .loading {
    flex-direction: column;
  }
}
</style>
