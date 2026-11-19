<script setup lang="ts">
import { Check, ChevronDown } from "@lucide/vue";
import { ref, watch } from "vue";
import {
  SelectRoot,
  SelectTrigger,
  SelectValue,
  SelectIcon,
  SelectPortal,
  SelectContent,
  SelectViewport,
  SelectItem,
  SelectItemText,
  SelectItemIndicator,
} from "reka-ui";
import "flag-icons/css/flag-icons.min.css";
import "./language-select.css";

const props = defineProps<{
  modelValue: string;
  languages: { code: string; label: string }[];
  label: string;
  placeholder: string;
  disabled: boolean;
}>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();
const open = ref(false);
watch(() => props.disabled, (disabled) => {
  if (disabled) open.value = false;
});
const flags: Record<string, string> = {
  sq: "al",
  ar: "arab",
  hy: "am",
  az: "az",
  eu: "es-pv",
  be: "by",
  bs: "ba",
  bg: "bg",
  ca: "es-ct",
  hr: "hr",
  cs: "cz",
  da: "dk",
  nl: "nl",
  en: "gb",
  et: "ee",
  fo: "fo",
  fi: "fi",
  fr: "fr",
  ka: "ge",
  de: "de",
  el: "gr",
  hu: "hu",
  is: "is",
  ga: "ie",
  it: "it",
  lv: "lv",
  lt: "lt",
  lb: "lu",
  mk: "mk",
  mt: "mt",
  no: "no",
  pl: "pl",
  pt: "pt",
  ro: "ro",
  ru: "ru",
  gd: "gb-sct",
  sr: "rs",
  sk: "sk",
  sl: "si",
  es: "es",
  sv: "se",
  tr: "tr",
  uk: "ua",
  cy: "gb-wls",
};
</script>

<template>
  <SelectRoot
    v-model:open="open"
    :model-value="modelValue"
    :disabled="disabled"
    @update:model-value="emit('update:modelValue', String($event ?? ''))"
  >
    <SelectTrigger class="language-trigger" :aria-label="label">
      <span
        v-if="flags[modelValue]"
        class="fi language-flag"
        :class="`fi-${flags[modelValue]}`"
        aria-hidden="true"
      />
      <SelectValue :placeholder="placeholder">{{
        props.languages.find((language) => language.code === modelValue)
          ?.label || placeholder
      }}</SelectValue>
      <SelectIcon class="language-chevron"
        ><ChevronDown :size="16"
      /></SelectIcon>
    </SelectTrigger>
    <SelectPortal>
      <SelectContent
        class="language-menu"
        position="popper"
        :side-offset="6"
        :collision-padding="12"
      >
        <SelectViewport class="language-viewport">
          <SelectItem
            v-for="language in languages"
            :key="language.code"
            :value="language.code"
            class="language-option"
          >
            <span
              v-if="flags[language.code]"
              class="fi language-flag"
              :class="`fi-${flags[language.code]}`"
              aria-hidden="true"
            />
            <SelectItemText>{{ language.label }}</SelectItemText>
            <SelectItemIndicator class="language-selected"
              ><Check :size="15"
            /></SelectItemIndicator>
          </SelectItem>
        </SelectViewport>
      </SelectContent>
    </SelectPortal>
  </SelectRoot>
</template>
