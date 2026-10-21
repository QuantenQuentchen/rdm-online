<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue'
import {
  fundables,
  items,
  isLoading,
  isRefreshing,
  hasExternalUpdate,
  acknowledgeExternalUpdate,
  loadFundables,
  startFundableUpdates,
  stopFundableUpdates
} from '../stores/fundables'
import FundableCard from '../components/FundableCard.vue'
import FundableInspector from '../components/FundableInspector.vue'
import StatusNote from '../components/StatusNote.vue'
import UiButton from '../components/UiButton.vue'

const search = ref('')
const categorySearch = ref('')
const selectedId = ref<number | null>(null)
const inspectorOpen = ref(true)
const filteredItems = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return items.value
  return items.value.filter((item) => `${item.name} ${item.title} ${item.description ?? ''}`.toLowerCase().includes(term))
})
const selectedFundable = computed(() => items.value.find((item) => item.id === selectedId.value) ?? null)
function selectFundable(id: number): void {
  selectedId.value = id
  inspectorOpen.value = true
}
function closeInspector(): void {
  inspectorOpen.value = false
}

onMounted(() => {
  void loadFundables();
  startFundableUpdates()
})
onUnmounted(stopFundableUpdates)
</script>

<template>
  <section class="funds-view">
    <header class="page-header"><span class="kicker">Council projects</span>
      <h1>Funding proposals</h1>
      <p>Review active proposals, record interest, and note provisional commitments for council planning.</p></header>
    <div class="filters">
      <label class="search"><span aria-hidden="true">⌕</span><input v-model="search" type="search"
                                                                    placeholder="Search proposals"/></label>
      <label class="search search--category"><span>Category</span><input v-model="categorySearch" type="search"
                                                                         placeholder="Filter by category"/></label>
    </div>
    <p v-if="isRefreshing" class="refreshing" role="status">Updating proposal figures…</p>
    <button v-if="hasExternalUpdate.value" class="update-notice" type="button" @click="acknowledgeExternalUpdate">New fundable information is available — figures updated</button>
    <div v-if="isLoading && items.length === 0" class="loading">Loading fundables…</div>
    <StatusNote v-else-if="fundables.status === 'error'" tone="error" title="Funds didn't load"
                :message="fundables.error ?? 'Unknown error'">
      <UiButton size="sm" variant="ghost" @click="loadFundables(true)">Try again</UiButton>
    </StatusNote>
    <StatusNote v-else-if="filteredItems.length === 0" title="No matching proposals"
                message="Try a different search term."/>
    <div v-else class="fund-layout">
      <div class="fund-list">
        <FundableCard v-for="fundable in filteredItems" :key="fundable.id" :fundable="fundable" :selected="fundable.id === selectedId" @select="selectFundable(fundable.id)" />
      </div>
      <aside v-if="inspectorOpen" class="inspector-panel">
        <FundableInspector v-if="selectedFundable" :fundable="selectedFundable">
          <template #close><button class="close-button" type="button" aria-label="Collapse details" @click="closeInspector">×</button></template>
        </FundableInspector>
        <div v-else class="empty-inspector">
          <strong>Select a fundable</strong>
          <p>Choose a record to review its origin, components, commitments, and edit history.</p>
        </div>
      </aside>
      <button v-else class="show-inspector" type="button" @click="inspectorOpen = true">Show details</button>
    </div>
  </section>
</template>

<style scoped>
.funds-view {
  max-width: 1120px;
  margin: 0 auto;
}

.page-header {
  max-width: 680px;
  margin-bottom: 1.75rem;
}

.kicker {
  color: var(--accent);
  font-size: .75rem;
  font-weight: 700;
  letter-spacing: .14em;
  text-transform: uppercase;
}

.page-header h1 {
  margin: .5rem 0 .75rem;
  color: var(--ink);
  font-size: clamp(2.4rem, 5vw, 4.5rem);
  line-height: 1;
  letter-spacing: -.04em;
}

.page-header p {
  margin: 0;
  color: var(--muted);
  font-size: 1.05rem;
}

.filters {
  display: flex;
  gap: .75rem;
  margin-bottom: 1.5rem;
}

.search {
  display: flex;
  align-items: center;
  gap: .6rem;
  flex: 1;
  padding: .7rem .85rem;
  border: 1px solid var(--line);
  border-radius: 11px;
  background: var(--surface);
  color: var(--muted);
}

.search input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
  font: inherit;
}

.search--category {
  max-width: 280px;
}

.search--category span {
  white-space: nowrap;
  font-size: .8rem;
  font-weight: 700;
}

.fund-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(340px, 430px); gap: 1rem; align-items: start; }
.fund-list { display: grid; gap: .65rem; }
.inspector-panel { position: sticky; top: calc(var(--topbar) + 1rem); min-height: 480px; max-height: calc(100vh - var(--topbar) - 2rem); }
.empty-inspector { display: grid; place-content: center; min-height: 300px; padding: 2rem; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); color: var(--ink); text-align: center; }.empty-inspector p { color: var(--muted); }
.close-button { border: 0; background: transparent; color: var(--muted); font-size: 1.5rem; cursor: pointer; }.show-inspector, .update-notice { border: 1px solid var(--line); border-radius: 8px; padding: .6rem .8rem; background: var(--surface); color: var(--ink); cursor: pointer; }.update-notice { margin-bottom: 1rem; border-color: var(--accent); color: var(--accent); }

.loading {
  color: var(--muted);
}

.refreshing {
  margin: -0.75rem 0 1rem;
  color: var(--muted);
  font-size: .82rem;
}

@media (max-width: 680px) {
  .filters {
    flex-direction: column;
  }

  .search--category {
    max-width: none;
  }
  .fund-layout { grid-template-columns: 1fr; }.inspector-panel { position: static; max-height: none; }
}
</style>
