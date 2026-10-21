<script setup lang="ts">
import type { Fundable } from '../types'

defineProps<{ fundable: Fundable; selected: boolean }>()
const emit = defineEmits<{ select: [] }>()
</script>

<template>
  <button class="fund-row" :class="{ 'fund-row--selected': selected }" type="button" @click="emit('select')">
    <span class="fund-row__id">{{ fundable.publicId ?? `FP-${String(fundable.id).padStart(4, '0')}` }}</span>
    <span class="fund-row__main">
      <strong>{{ fundable.title }}</strong>
      <span>{{ fundable.origin?.title ?? 'Origin reference pending' }}</span>
    </span>
    <span class="fund-row__status" :class="{ 'fund-row__status--complete': fundable.fullyFunded }">
      {{ fundable.fullyFunded ? 'Funded' : 'Open' }}
    </span>
    <span class="fund-row__count">{{ fundable.components.length }} component{{ fundable.components.length === 1 ? '' : 's' }}</span>
    <span class="fund-row__arrow" aria-hidden="true">›</span>
  </button>
</template>

<style scoped>
.fund-row {
  display: grid;
  grid-template-columns: 7rem minmax(0, 1fr) auto auto auto;
  align-items: center;
  gap: 1rem;
  width: 100%;
  padding: 1rem 1.1rem;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  transition: border-color .16s ease, background .16s ease;
}
.fund-row:hover, .fund-row--selected { border-color: var(--accent); background: var(--surface-alt); }
.fund-row__id { color: var(--accent); font-family: ui-monospace, monospace; font-size: .78rem; font-weight: 700; }
.fund-row__main { display: grid; gap: .18rem; min-width: 0; }
.fund-row__main strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.fund-row__main span, .fund-row__count { color: var(--muted); font-size: .8rem; }
.fund-row__status { color: var(--accent); font-size: .78rem; font-weight: 700; }
.fund-row__status--complete { color: var(--success); }
.fund-row__arrow { color: var(--muted); font-size: 1.4rem; }
@media (max-width: 720px) {
  .fund-row { grid-template-columns: 5.5rem minmax(0, 1fr) auto; }
  .fund-row__count { display: none; }
}
</style>
