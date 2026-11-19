import { beforeEach, describe, expect, it, vi } from "vitest";
import { readPreferences, savePreferences } from "./preferences";

describe("language preferences", () => {
  beforeEach(() => {
    const storage = new Map<string, string>();
    vi.stubGlobal("localStorage", {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => storage.set(key, value),
    });
  });
  it("requires both language choices by default", () => {
    expect(readPreferences()).toEqual({
      sourceLanguage: "",
      targetLanguage: "",
    });
  });
  it("clears legacy automatic detection without discarding the selected target", () => {
    localStorage.setItem(
      "translator.languages.v1",
      JSON.stringify({ sourceLanguage: "auto", targetLanguage: "nl" }),
    );
    expect(readPreferences()).toEqual({
      sourceLanguage: "",
      targetLanguage: "nl",
    });
  });
  it("persists and replaces the language pair", () => {
    savePreferences({ sourceLanguage: "nl", targetLanguage: "ar" });
    expect(readPreferences()).toEqual({
      sourceLanguage: "nl",
      targetLanguage: "ar",
    });
    savePreferences({ sourceLanguage: "en", targetLanguage: "nl" });
    expect(readPreferences()).toEqual({
      sourceLanguage: "en",
      targetLanguage: "nl",
    });
  });
  it("recovers from malformed stored values", () => {
    localStorage.setItem("translator.languages.v1", "broken");
    expect(readPreferences().targetLanguage).toBe("");
    localStorage.setItem("translator.languages.v1", '{"sourceLanguage":42}');
    expect(readPreferences().sourceLanguage).toBe("");
  });
  it("works when browser storage is unavailable", () => {
    vi.stubGlobal("localStorage", {
      getItem: () => {
        throw new Error();
      },
      setItem: () => {
        throw new Error();
      },
    });
    expect(readPreferences().targetLanguage).toBe("");
    expect(
      savePreferences({ sourceLanguage: "en", targetLanguage: "nl" }),
    ).toBe(false);
  });
});
