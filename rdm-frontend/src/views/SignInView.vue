<script setup lang="ts">
import { ref } from 'vue'
import config, { setDebugFlag } from '../config'
import { session, signInWithDiscord, clearSessionError } from '../stores/session'
import UiButton from '../components/UiButton.vue'

const loading = ref(false)
const isDev = import.meta.env.DEV

async function start(): Promise<void> {
  clearSessionError()
  loading.value = true
  try {
    await signInWithDiscord()
    // Real mode: the browser is about to navigate away to the backend's
    // /login route — nothing else to do here.
  } catch {
    loading.value = false
  }
}
</script>

<template>
  <main class="gate">
    <div class="gate__inner">
      <p class="gate__edition">{{ config.brand.appName }}</p>
      <h1 class="gate__title">A better way to participate</h1>
      <div class="gate__rule" aria-hidden="true" />
      <p class="gate__lede">
        One place for community decisions, shared projects, and the ideas that move us forward.
      </p>

      <UiButton class="gate__cta" variant="gold" size="lg" :loading="loading" @click="start">
        <svg class="discord" viewBox="0 0 24 18" aria-hidden="true">
          <path
            d="M20.3 1.6A19 19 0 0 0 15.6.2l-.2.5a17.7 17.7 0 0 1 4.2 1.4 15.9 15.9 0 0 0-15.2 0A17.6 17.6 0 0 1 8.6.7L8.4.2A19 19 0 0 0 3.7 1.6C.8 6 0 10.3.4 14.5a19.2 19.2 0 0 0 5.8 3l1.2-2a12.5 12.5 0 0 1-2-1l.5-.4a13.6 13.6 0 0 0 12.2 0l.5.4a12.5 12.5 0 0 1-2 1l1.2 2a19.1 19.1 0 0 0 5.8-3c.5-4.9-.8-9.1-3.3-12.9ZM8 11.9c-1.1 0-2-1-2-2.3s.9-2.3 2-2.3 2 1 2 2.3-.9 2.3-2 2.3Zm8 0c-1.1 0-2-1-2-2.3s.9-2.3 2-2.3 2 1 2 2.3-.9 2.3-2 2.3Z"
            fill="currentColor"
          />
        </svg>
        Continue with Discord
      </UiButton>

      <p v-if="session.error" class="gate__error" role="alert">{{ session.error }}</p>
      <p class="gate__fineprint">Sign in securely with Discord. No other login is required.</p>

      <button v-if="isDev" class="skip" type="button" @click="setDebugFlag('bypassAuth', true)">
        Skip sign-in (debug)
      </button>
    </div>
  </main>
</template>

<style scoped>
.gate {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem;
  text-align: center;
  background: #f7f8fc;
}

.gate__inner {
  width: min(44rem, 100%);
  padding: 3.5rem;
  border: 1px solid #e4e8f0;
  border-radius: 28px;
  background: #fff;
  box-shadow: 0 24px 80px rgba(28, 39, 63, .1);
}

.gate__edition {
  margin: 0 0 1.1rem;
  font-size: 0.78rem;
  letter-spacing: 0.44em;
  text-indent: 0.44em;
  color: #4f46e5;
}

.gate__title {
  font-family: var(--body);
  font-size: clamp(2.4rem, 6vw, 4.5rem);
  line-height: 1;
  letter-spacing: -0.05em;
  color: #172033;
  text-transform: none;
}

.gate__rule {
  width: 320px;
  height: 1px;
  margin: 1.75rem auto;
  background: #e4e8f0;
}

.gate__lede {
  margin: 0 auto 2.25rem;
  max-width: 48ch;
  color: #667085;
}

.discord {
  width: 20px;
  height: 15px;
}

.gate__error {
  margin: 1.25rem 0 0;
  color: var(--danger);
  font-size: 0.9rem;
}

.gate__fineprint {
  margin: 2.5rem 0 0;
  font-size: 0.78rem;
  color: var(--faint);
}

.skip {
  margin-top: 1rem;
  background: none;
  border: none;
  color: var(--spotlight);
  font-family: var(--body);
  font-size: 0.78rem;
  text-decoration: underline;
  cursor: pointer;
}
</style>
