import { ref } from 'vue'

const storageKey = 'democracy-online.dark-mode'
const stored = window.localStorage.getItem(storageKey)
export const darkMode = ref(stored === 'true')

export function toggleDarkMode(): void {
  darkMode.value = !darkMode.value
  window.localStorage.setItem(storageKey, String(darkMode.value))
}
