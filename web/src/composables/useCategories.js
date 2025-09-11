import { reactive } from 'vue'
import { categoryApi } from '@/api'

const state = reactive({ list: [], loaded: false, pending: null })

export function useCategories() {
  function ensure() {
    if (state.loaded) return Promise.resolve(state.list)
    if (!state.pending) {
      state.pending = categoryApi
        .list()
        .then((res) => {
          state.list = res || []
          state.loaded = true
          return state.list
        })
        .catch(() => [])
        .finally(() => {
          state.pending = null
        })
    }
    return state.pending
  }

  function nameOf(categoryId) {
    if (categoryId === null || categoryId === undefined) return ''
    const hit = state.list.find((item) => Number(item.id) === Number(categoryId))
    return hit?.categoryName || ''
  }

  return { state, ensure, nameOf }
}
