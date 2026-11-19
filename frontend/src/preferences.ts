export type Preferences = { sourceLanguage: string; targetLanguage: string };
const key = "translator.languages.v1";
const defaults: Preferences = { sourceLanguage: "", targetLanguage: "" };

export function readPreferences(): Preferences {
  try {
    const value = JSON.parse(localStorage.getItem(key) ?? "null");
    if (
      value &&
      typeof value.sourceLanguage === "string" &&
      typeof value.targetLanguage === "string"
    ) {
      return {
        sourceLanguage:
          value.sourceLanguage === "auto" ? "" : value.sourceLanguage,
        targetLanguage:
          value.targetLanguage === "auto" ? "" : value.targetLanguage,
      };
    }
  } catch {}
  return { ...defaults };
}

export function savePreferences(value: Preferences): boolean {
  try {
    localStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch {
    return false;
  }
}
