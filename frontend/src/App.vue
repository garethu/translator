<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import {
  ArrowRight,
  ArrowRightLeft,
  Check,
  Copy,
  Languages,
  LoaderCircle,
  Trash2,
} from "@lucide/vue";
import {
  readPreferences,
  savePreferences,
  type Preferences,
} from "./preferences";
import "./translator.css";
import LanguageSelect from "./LanguageSelect.vue";

type Language = { code: string; label: string; rtl: boolean };
type Result = { formal: string; informal: string };
const baseUrl = import.meta.env.BASE_URL;
const languages = ref<Language[]>([]);
const preferences = ref<Preferences>(readPreferences());
const text = ref("");
const result = ref<Result | null>(null);
const busy = ref(false);
const error = ref("");
const catalogError = ref(false);
const copied = ref("");
const storageAvailable = ref(true);
const serviceAvailable = ref(false);
const checkingService = ref(true);
const controlsDisabled = computed(() => busy.value || !serviceAvailable.value);
let availabilityTimer: ReturnType<typeof setTimeout> | undefined;
const availabilityAbort = new AbortController();
const outputLanguage = ref("en");
const outputRtl = computed(
  () =>
    languages.value.find((language) => language.code === outputLanguage.value)
      ?.rtl ?? false,
);
const targetLabel = computed(
  () =>
    languages.value.find(
      (language) => language.code === preferences.value.targetLanguage,
    )?.label ?? "",
);
const hasLanguagePair = computed(
  () =>
    languages.value.some(
      (language) => language.code === preferences.value.sourceLanguage,
    ) &&
    languages.value.some(
      (language) => language.code === preferences.value.targetLanguage,
    ),
);
const invalidPair = computed(
  () =>
    hasLanguagePair.value &&
    preferences.value.sourceLanguage === preferences.value.targetLanguage,
);
const canTranslate = computed(
  () =>
    !!text.value.trim() &&
    !controlsDisabled.value &&
    hasLanguagePair.value &&
    !invalidPair.value &&
    languages.value.length > 0,
);
let generation = 0;
watch(serviceAvailable, (available) => {
  if (!available) {
    generation++;
    result.value = null;
    copied.value = "";
    error.value = "";
  }
});
watch(
  [
    text,
    () => preferences.value.sourceLanguage,
    () => preferences.value.targetLanguage,
  ],
  () => {
    generation++;
    result.value = null;
    copied.value = "";
    error.value = "";
  },
);
watch(
  preferences,
  (value) => {
    storageAvailable.value = savePreferences(value);
  },
  { deep: true },
);

async function loadLanguages() {
  catalogError.value = false;
  try {
    const response = await fetch(`${import.meta.env.BASE_URL}api/languages`);
    if (!response.ok) throw new Error();
    languages.value = await response.json();
    if (
      !languages.value.some(
        (language) => language.code === preferences.value.targetLanguage,
      )
    )
      preferences.value.targetLanguage = "";
    if (
      !languages.value.some(
        (language) => language.code === preferences.value.sourceLanguage,
      )
    )
      preferences.value.sourceLanguage = "";
  } catch {
    catalogError.value = true;
  }
}

async function checkAvailability() {
  try {
    const response = await fetch(`${baseUrl}api/availability`, {
      cache: "no-store",
      signal: AbortSignal.any([
        availabilityAbort.signal,
        AbortSignal.timeout(10000),
      ]),
    });
    serviceAvailable.value = response.ok;
  } catch {
    serviceAvailable.value = false;
  } finally {
    checkingService.value = false;
    if (!availabilityAbort.signal.aborted) {
      availabilityTimer = setTimeout(checkAvailability, 10000);
    }
  }
}

async function translate() {
  if (!canTranslate.value) return;
  const currentGeneration = generation;
  const target = preferences.value.targetLanguage;
  busy.value = true;
  error.value = "";
  result.value = null;
  try {
    const response = await fetch(`${import.meta.env.BASE_URL}api/translations`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ text: text.value, ...preferences.value }),
      signal: AbortSignal.timeout(135000),
    });
    if (!response.ok) {
      if (response.status === 503) {
        serviceAvailable.value = false;
        return;
      }
      const messages: Record<number, string> = {
        400: "Check your text and choose two different languages.",
        429: "Another translation is running. Please try again shortly.",
        502: "The model returned an invalid translation or the wrong language. Please try again.",
      };
      throw new Error(
        messages[response.status] ?? "Translation failed. Please try again.",
      );
    }
    const payload: Result = await response.json();
    if (!payload.formal?.trim() || !payload.informal?.trim())
      throw new Error(
        "The model returned an incomplete translation. Please try again.",
      );
    if (currentGeneration === generation) {
      result.value = payload;
      outputLanguage.value = target;
    }
  } catch (failure) {
    if (failure instanceof Error && failure.name === "TypeError") {
      serviceAvailable.value = false;
      return;
    }
    if (currentGeneration === generation)
      error.value =
        failure instanceof Error &&
        failure.name !== "TypeError" &&
        failure.name !== "TimeoutError"
          ? failure.message
          : "Cannot reach the translation service, or the request timed out. Please try again.";
  } finally {
    busy.value = false;
  }
}

function swap() {
  if (controlsDisabled.value || !hasLanguagePair.value) return;
  preferences.value = {
    sourceLanguage: preferences.value.targetLanguage,
    targetLanguage: preferences.value.sourceLanguage,
  };
}
async function copyResult(tone: keyof Result) {
  if (!serviceAvailable.value) return;
  const value = result.value?.[tone];
  if (!value) return;
  try {
    await navigator.clipboard.writeText(value);
    copied.value = tone;
  } catch {
    error.value =
      "Clipboard access was denied. Select the translation to copy it.";
  }
}
onMounted(() => {
  void loadLanguages();
  void checkAvailability();
});
onUnmounted(() => {
  clearTimeout(availabilityTimer);
  availabilityAbort.abort();
});
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" :href="serviceAvailable ? baseUrl : undefined"
        :aria-disabled="!serviceAvailable" aria-label="Translator home"
        ><span class="brand-icon"><Languages :size="22" /></span>
        Translator<span class="brand-dot">.</span></a
      >
      <span class="local-badge"><span /> Local workspace</span>
    </header>
    <main>
      <div class="page-heading">
        <div>
          <p class="eyebrow">TRANSLATION WORKSPACE</p>
          <h1>Translator</h1>
        </div>
        <p class="heading-note">The right words. The right tone.</p>
      </div>
      <div v-if="!serviceAvailable && !checkingService" class="notice error" role="alert">
        The translation service is not available. Check that Ollama is running.
      </div>
      <div v-if="checkingService" class="notice" role="status">
        Checking translation service availability...
      </div>
      <div v-if="catalogError && serviceAvailable" class="notice error" role="alert">
        Cannot load languages. Check that the backend is running.
        <button :disabled="controlsDisabled" @click="loadLanguages">Retry</button>
      </div>
      <div v-if="!storageAvailable" class="notice" role="status">
        Browser storage is unavailable. Language choices will last for this
        session only.
      </div>
      <section class="language-bar" aria-label="Translation languages">
        <label
          ><span>FROM</span
          ><LanguageSelect
            v-model="preferences.sourceLanguage"
            label="Source language"
            placeholder="Choose source language"
            :languages="languages"
            :disabled="controlsDisabled"
        /></label>
        <button
          class="icon-button swap"
          title="Swap languages"
          aria-label="Swap languages"
          :disabled="controlsDisabled || !hasLanguagePair"
          @click="swap"
        >
          <ArrowRightLeft :size="19" />
        </button>
        <label
          ><span>TO</span
          ><LanguageSelect
            v-model="preferences.targetLanguage"
            label="Target language"
            placeholder="Choose target language"
            :languages="languages"
            :disabled="controlsDisabled"
        /></label>
        <span class="saved-label"
          ><Check :size="14" />
          {{
            storageAvailable ? "Languages remembered" : "Session preferences"
          }}</span
        >
      </section>
      <form @submit.prevent="translate">
        <section class="source-section" aria-labelledby="source-title">
          <div class="section-title">
            <h2 id="source-title">Your message</h2>
            <button
              type="button"
              class="icon-button"
              title="Clear text"
              aria-label="Clear text"
              :disabled="!text || controlsDisabled"
              @click="text = ''"
            >
              <Trash2 :size="17" />
            </button>
          </div>
          <textarea
            v-model="text"
            aria-label="Text to translate"
            dir="auto"
            maxlength="6000"
            :disabled="controlsDisabled"
            placeholder="What would you like to say?"
          />
          <div class="source-footer">
            <span class="character-count"
              >{{ text.length.toLocaleString() }} / 6,000</span
            ><button class="translate-button" :disabled="!canTranslate">
              <LoaderCircle v-if="busy" class="spin" :size="17" /><span>{{
                busy ? "Translating" : "Translate"
              }}</span
              ><ArrowRight v-if="!busy" :size="18" />
            </button>
          </div>
        </section>
      </form>
      <p v-if="invalidPair" class="notice error" role="alert">
        Choose a different target language.
      </p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <div class="results-heading">
        <h2>Your translations</h2>
        <span>{{ targetLabel }}</span>
      </div>
      <div class="results-grid" :aria-busy="busy">
        <section
          v-for="tone in ['formal', 'informal'] as const"
          :key="tone"
          class="result-panel"
          :class="tone"
          :aria-labelledby="`${tone}-title`"
        >
          <header class="result-header">
            <div>
              <span class="tone-mark">{{
                tone === "formal" ? "01" : "02"
              }}</span>
              <h3 :id="`${tone}-title`">
                {{ tone === "formal" ? "Formal" : "Informal" }}
              </h3>
              <p>
                {{
                  tone === "formal" ? "For your client" : "For your colleague"
                }}
              </p>
            </div>
            <button
              class="icon-button"
              :title="copied === tone ? 'Copied' : `Copy ${tone} translation`"
              :aria-label="`Copy ${tone} translation`"
              :disabled="!serviceAvailable || !result"
              @click="copyResult(tone)"
            >
              <Check v-if="copied === tone" :size="19" /><Copy
                v-else
                :size="19"
              />
            </button>
          </header>
          <p
            v-if="result"
            class="translated-text"
            :dir="outputRtl ? 'rtl' : 'auto'"
            :lang="outputLanguage"
          >
            {{ result[tone] }}
          </p>
          <div v-else class="result-placeholder">
            <LoaderCircle v-if="busy" class="spin" :size="20" /><span>{{
              busy
                ? "Finding the right words..."
                : "Your translation will appear here."
            }}</span>
          </div>
          <footer>
            <span class="tone-line" /><span>{{
              tone === "formal"
                ? "Polished & professional"
                : "Natural & conversational"
            }}</span>
          </footer>
        </section>
      </div>
      <p class="sr-only" role="status">
        {{
          copied
            ? `${copied} translation copied.`
            : result
              ? "Both translations are ready."
              : busy
                ? "Translation in progress."
                : ""
        }}
      </p>
      <footer class="page-footer">
        <span>Translator</span><span>Powered by your local model</span>
      </footer>
    </main>
  </div>
</template>
