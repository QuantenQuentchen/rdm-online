import {computed, reactive, readonly, type DeepReadonly} from 'vue'
import fundablesApi from '../api/fundables'
import config from '../config'
import type {Fundable} from '../types'

type Status = 'idle' | 'loading' | 'ready' | 'error'

interface State {
    status: Status;
    error: string | null;
    items: Fundable[]
}

const state = reactive<State>({status: 'idle', error: null, items: []})
let source: EventSource | null = null
let reconnectTimer: number | undefined

export const fundables = readonly(state) as DeepReadonly<State>
export const isLoading = computed(() => state.status === 'loading')
export const isRefreshing = computed(() => state.status === 'loading' && state.items.length > 0)
export const items = computed(() => state.items)
export const interestedComponents = reactive(new Set<number>())
export const committingComponents = reactive(new Set<number>())
export const interestErrors = reactive<Record<number, string>>({})
export const hasExternalUpdate = reactive({ value: false })

export async function loadFundables(force = false): Promise<void> {
    if (state.status === 'loading' || (state.status === 'ready' && !force)) return
    if (state.items.length === 0) state.status = 'loading'
    state.error = null
    try {
        state.items = await fundablesApi.list()
        state.status = 'ready'
    } catch (error) {
        state.error = (error as Error).message
        state.status = 'error'
    }

}

export function acknowledgeExternalUpdate(): void {
    hasExternalUpdate.value = false
}

export async function toggleInterest(componentId: number, userId: number): Promise<boolean> {
    try {
        if (interestedComponents.has(componentId)) {
            await fundablesApi.removeInterest(componentId, userId)
            interestedComponents.delete(componentId)
        } else {
            await fundablesApi.addInterest(componentId)
            interestedComponents.add(componentId)
        }
        await loadFundables(true)
        return true
    } catch (error) {
        interestErrors[componentId] = (error as Error).message
        return false
    }
}

export async function commitToComponent(componentId: number, amountCents: number, userId: number): Promise<boolean> {
    committingComponents.add(componentId)
    try {
        await fundablesApi.commit(componentId, {
            userId,
            amountCents,
            paymentSchedule: {type: 'one_time'},
        })
        await loadFundables(true)
        return true
    } catch (error) {
        interestErrors[componentId] = (error as Error).message
        return false
    } finally {
        committingComponents.delete(componentId)
    }
}

function connectEvents(): void {
    if (config.debug.useMockApi || typeof EventSource === 'undefined' || source) return
    const url = new URL(`${config.api.baseUrl}${config.endpoints.events}`, window.location.origin)
    url.searchParams.append('scope', 'fundable')
    source = new EventSource(url.toString(), {withCredentials: true})
    const refresh = () => {
        if (state.status === 'ready') hasExternalUpdate.value = true
        void loadFundables(true)
    }
    source.onmessage = refresh
    for (const eventName of [
        'FundableAdded',
        'FundableRemoved',
        'FundableComponentChanged',
        'CommitmentChanged',
        'InterestChanged',
        'Resync',
    ]) {
        source.addEventListener(eventName, refresh)
    }
    source.onerror = () => {
        source?.close()
        source = null
        reconnectTimer = window.setTimeout(connectEvents, 4000)
    }
}

export function startFundableUpdates(): void {
    connectEvents()
}

export function stopFundableUpdates(): void {
    source?.close()
    source = null
    if (reconnectTimer !== undefined) window.clearTimeout(reconnectTimer)
}

export function resetFundables(): void {
    stopFundableUpdates()
    state.status = 'idle'
    state.error = null
    state.items = []
    interestedComponents.clear()
    committingComponents.clear()
    hasExternalUpdate.value = false
}
