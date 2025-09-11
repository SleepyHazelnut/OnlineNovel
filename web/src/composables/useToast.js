import { reactive } from 'vue'

const state = reactive({ items: [] })
let seq = 0

function push(type, text, duration = 2600) {
  const id = ++seq
  state.items.push({ id, type, text })
  window.setTimeout(() => {
    const index = state.items.findIndex((item) => item.id === id)
    if (index > -1) state.items.splice(index, 1)
  }, duration)
}

export function useToast() {
  return {
    state,
    info: (text) => push('info', text),
    success: (text) => push('success', text),
    error: (text) => push('error', text)
  }
}
