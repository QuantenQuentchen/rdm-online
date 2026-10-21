<script setup lang="ts">
import { ref } from 'vue'
import type { Fundable } from '../types'
import { currentUser } from '../stores/session'
import { committingComponents, interestedComponents, commitToComponent, toggleInterest } from '../stores/fundables'

defineProps<{ fundable: Fundable }>()
const money = (cents: number) => new Intl.NumberFormat(undefined, { style: 'currency', currency: 'EUR' }).format(cents / 100)
const commitment = ref<Record<number, string>>({})
const historyOpen = ref(false)
const userId = () => Number(currentUser.value?.id ?? 0)
const commit = async (componentId: number) => {
  const amount = Math.round(Number(commitment.value[componentId] ?? 0) * 100)
  if (amount > 0 && userId() > 0 && await commitToComponent(componentId, amount, userId())) commitment.value[componentId] = ''
}
</script>

<template>
  <aside class="inspector">
    <div class="inspector__header">
      <div>
        <span class="inspector__id">{{ fundable.publicId ?? `FP-${String(fundable.id).padStart(4, '0')}` }}</span>
        <h2>{{ fundable.title }}</h2>
      </div>
      <slot name="close" />
    </div>

    <p v-if="fundable.description" class="inspector__description">{{ fundable.description }}</p>

    <dl class="facts">
      <div><dt>Origin</dt><dd v-if="fundable.origin?.url"><a :href="fundable.origin.url">{{ fundable.origin.title }}</a></dd><dd v-else>{{ fundable.origin?.title ?? 'Council resolution reference pending' }}</dd></div>
      <div><dt>Created by</dt><dd>{{ fundable.createdBy ?? 'Creator reference pending' }}</dd></div>
      <div><dt>Last edited</dt><dd>{{ fundable.updatedAt ?? 'Not provided' }}</dd></div>
    </dl>

    <section class="components">
      <h3>Fundable components</h3>
      <article v-for="component in fundable.components" :key="component.id" class="component">
        <header class="component__header">
          <div><span class="component__id">{{ component.publicId ?? `${fundable.publicId ?? 'FP'}-C${String(component.id).padStart(2, '0')}` }}</span><h4>{{ component.name }}</h4></div>
          <strong>{{ money(component.amountCent) }}</strong>
        </header>
        <p v-if="component.description">{{ component.description }}</p>
        <p v-if="component.useOfFunds" class="use-of-funds"><strong>Use of funds:</strong> {{ component.useOfFunds }}</p>
        <div class="component__numbers">
          <span><strong>{{ money(component.amountCommitted) }}</strong> committed</span>
          <span><strong>{{ money(Math.max(0, component.amountCent - component.amountCommitted)) }}</strong> remaining</span>
          <span><strong>{{ component.interestedCount }}</strong> interested</span>
          <span><strong>{{ money(component.equalDivisionOnRemaining) }}</strong> projected per interested member</span>
        </div>
        <div class="component__actions">
          <button class="action" type="button" @click="toggleInterest(component.id, userId())">{{ interestedComponents.has(component.id) ? 'Withdraw interest' : 'Show interest' }}</button>
          <form @submit.prevent="commit(component.id)">
            <input v-model="commitment[component.id]" type="number" min="0.01" step="0.01" placeholder="€ amount" />
            <button class="action action--primary" type="submit" :disabled="committingComponents.has(component.id)">Record commitment</button>
          </form>
        </div>
      </article>
    </section>

    <button class="history-toggle" type="button" @click="historyOpen = !historyOpen">
      {{ historyOpen ? 'Hide edit history' : 'View edit history' }}
      <span v-if="fundable.editHistory?.length">({{ fundable.editHistory.length }})</span>
    </button>
    <div v-if="historyOpen" class="history">
      <p v-if="!fundable.editHistory?.length">No edits recorded.</p>
      <div v-for="revision in fundable.editHistory" :key="revision.id" class="history__entry">
        <strong>{{ revision.summary }}</strong><span>{{ revision.editedAt }}</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.inspector { display: grid; gap: 1.25rem; height: 100%; padding: 1.35rem; border: 1px solid var(--line); border-radius: 14px; background: var(--surface); color: var(--ink); overflow: auto; }
.inspector__header { display: flex; justify-content: space-between; gap: 1rem; border-bottom: 1px solid var(--line); padding-bottom: 1rem; }.inspector__id, .component__id { color: var(--accent); font: 700 .72rem ui-monospace, monospace; }.inspector h2 { margin: .3rem 0 0; font-size: 1.35rem; line-height: 1.15; }.inspector__description { margin: 0; color: var(--muted); white-space: pre-wrap; }
.facts { display: grid; gap: .8rem; margin: 0; }.facts div { display: grid; grid-template-columns: 7rem 1fr; gap: .75rem; }.facts dt { color: var(--muted); font-size: .78rem; }.facts dd { margin: 0; font-size: .88rem; }.facts a { color: var(--accent); }
.components h3 { margin: 0 0 .7rem; font-size: .9rem; }.component { display: grid; gap: .65rem; padding: 1rem 0; border-top: 1px solid var(--line); }.component__header { display: flex; justify-content: space-between; gap: 1rem; }.component h4 { margin: .25rem 0 0; font-size: 1rem; }.component p { margin: 0; color: var(--muted); font-size: .86rem; white-space: pre-wrap; }.use-of-funds { color: var(--ink) !important; }.component__numbers { display: grid; grid-template-columns: 1fr 1fr; gap: .45rem .8rem; color: var(--muted); font-size: .76rem; }.component__numbers strong { display: block; color: var(--ink); font-size: .92rem; }.component__actions { display: flex; flex-wrap: wrap; gap: .5rem; }.component__actions form { display: flex; gap: .4rem; }.component__actions input { width: 90px; border: 1px solid var(--line); border-radius: 8px; padding: .45rem; background: var(--surface); color: var(--ink); }.action, .history-toggle { border: 1px solid var(--line); border-radius: 8px; padding: .45rem .65rem; background: var(--surface); color: var(--ink); font: inherit; font-size: .76rem; cursor: pointer; }.action--primary { border-color: var(--accent); background: var(--accent); color: white; }.history-toggle { text-align: left; }.history { display: grid; gap: .65rem; color: var(--muted); font-size: .8rem; }.history p { margin: 0; }.history__entry { display: grid; gap: .15rem; padding-left: .75rem; border-left: 2px solid var(--line); }.history__entry span { font-size: .72rem; }
</style>
