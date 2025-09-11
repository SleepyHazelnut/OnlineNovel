const observer = new IntersectionObserver(
  (entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue
      entry.target.classList.add('is-in')
      observer.unobserve(entry.target)
    }
  },
  { rootMargin: '0px 0px -8% 0px', threshold: 0.05 }
)

export default {
  mounted(el, binding) {
    el.classList.add('reveal')
    const delay = Number(binding.value || 0)
    if (delay) el.style.transitionDelay = `${delay}ms`
    observer.observe(el)
  },
  unmounted(el) {
    observer.unobserve(el)
  }
}
