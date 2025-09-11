<script setup>
import BookCard from './BookCard.vue'

defineProps({
  books: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  skeletonCount: { type: Number, default: 5 }
})
</script>

<template>
  <div class="grid">
    <template v-if="loading">
      <div v-for="i in skeletonCount" :key="i" class="cell">
        <div class="skeleton cover" />
        <div class="skeleton line" />
        <div class="skeleton line short" />
      </div>
    </template>
    <BookCard
      v-for="(book, i) in books"
      v-else
      :key="book.id ?? book.bookId ?? i"
      v-reveal="(i % 5) * 45"
      :book="book"
    />
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: var(--s-5) var(--s-4);
}

.cell {
  min-width: 0;
}

.cover {
  aspect-ratio: 3 / 4;
  border-radius: var(--r-s);
}

.line {
  height: 12px;
  margin-top: 10px;
  width: 80%;
}

.line.short {
  width: 46%;
  margin-top: 8px;
}

@media (max-width: 1000px) {
  .grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 680px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: var(--s-4) var(--s-3);
  }
}

@media (max-width: 420px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
