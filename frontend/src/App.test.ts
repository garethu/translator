// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { nextTick } from "vue";
import App from "./App.vue";
import LanguageSelect from "./LanguageSelect.vue";

const catalog = [
  { code: "en", label: "English", rtl: false },
  { code: "nl", label: "Dutch", rtl: false },
  { code: "ar", label: "Arabic", rtl: true },
];
let wrapper: VueWrapper;
let fetchMock: ReturnType<typeof vi.fn>;
const answer = {
  formal: "Would you kindly send the report?",
  informal: "Could you send the report?",
};

beforeEach(() => {
  vi.useFakeTimers();
  vi.stubEnv("BASE_URL", "/translator/");
  localStorage.clear();
  fetchMock = vi
    .fn()
    .mockImplementation(async (url: string) => ({
      ok: true,
      json: async () => url.endsWith("/api/languages") ? catalog : undefined,
    }));
  vi.stubGlobal("fetch", fetchMock);
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: { writeText: vi.fn().mockResolvedValue(undefined) },
  });
});
afterEach(() => {
  wrapper?.unmount();
  vi.useRealTimers();
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
});

async function setup() {
  wrapper = mount(App);
  await flushPromises();
}
function languageSelect(label: string) {
  const select = wrapper
    .findAllComponents(LanguageSelect)
    .find((component) => component.props("label") === label);
  expect(select, `LanguageSelect labeled ${label}`).toBeDefined();
  return select!;
}
async function chooseLanguage(label: string, value: string) {
  languageSelect(label).vm.$emit("update:modelValue", value);
  await nextTick();
}
async function submit() {
  await chooseLanguage("Source language", "nl");
  if (!languageSelect("Target language").props("modelValue")) {
    await chooseLanguage("Target language", "en");
  }
  await wrapper.get("textarea").setValue("Kun je het rapport sturen?");
  await wrapper.get("form").trigger("submit");
  await flushPromises();
}

describe("translation workspace", () => {
  it("requires both explicit languages and nonblank text before enabling Translate", async () => {
    await setup();
    expect(wrapper.text()).not.toContain("Detect language");
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    await wrapper.get("textarea").setValue("Hello");
    await wrapper.get("form").trigger("submit");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeDefined();
    await chooseLanguage("Source language", "en");
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeDefined();
    await chooseLanguage("Target language", "nl");
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeUndefined();
    await wrapper.get("textarea").setValue("   ");
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeDefined();
  });
  it("requires a replacement for a saved automatic source", async () => {
    localStorage.setItem(
      "translator.languages.v1",
      JSON.stringify({ sourceLanguage: "auto", targetLanguage: "nl" }),
    );
    await setup();
    await wrapper.get("textarea").setValue("Hello");
    expect(languageSelect("Source language").props("modelValue")).toBe("");
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeDefined();
  });
  it("renders and copies both variants from a single request", async () => {
    await setup();
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => answer });
    await submit();
    expect(wrapper.get(".formal .translated-text").text()).toBe(answer.formal);
    expect(wrapper.get(".informal .translated-text").text()).toBe(
      answer.informal,
    );
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[0]![0]).toBe("/translator/api/languages");
    expect(fetchMock.mock.calls[1]![0]).toBe("/translator/api/availability");
    expect(fetchMock.mock.calls[2]![0]).toBe("/translator/api/translations");
    expect(wrapper.get('[aria-label="Translator home"]').attributes("href")).toBe("/translator/");
    const request = JSON.parse(fetchMock.mock.calls[2]![1].body);
    expect(request).toEqual({
      text: "Kun je het rapport sturen?",
      sourceLanguage: "nl",
      targetLanguage: "en",
    });
    await wrapper
      .get('[aria-label="Copy formal translation"]')
      .trigger("click");
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith(
      answer.formal,
    );
    await wrapper
      .get('[aria-label="Copy informal translation"]')
      .trigger("click");
    expect(navigator.clipboard.writeText).toHaveBeenLastCalledWith(
      answer.informal,
    );
    await wrapper.get("textarea").setValue("Changed message");
    expect(wrapper.find(".translated-text").exists()).toBe(false);
  });
  it("restores saved choices and renders Arabic right to left", async () => {
    localStorage.setItem(
      "translator.languages.v1",
      JSON.stringify({ sourceLanguage: "nl", targetLanguage: "ar" }),
    );
    await setup();
    expect(languageSelect("Target language").props("modelValue")).toBe("ar");
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => answer });
    await submit();
    expect(
      wrapper
        .findAll(".translated-text")
        .every((element) => element.attributes("dir") === "rtl"),
    ).toBe(true);
    await chooseLanguage("Target language", "en");
    expect(
      JSON.parse(localStorage.getItem("translator.languages.v1")!)
        .targetLanguage,
    ).toBe("en");
    expect(wrapper.find(".translated-text").exists()).toBe(false);
  });
  it("locks the app when a translation returns service unavailable", async () => {
    await setup();
    fetchMock.mockResolvedValueOnce({ ok: false, status: 503 });
    await submit();
    expect(wrapper.get('[role="alert"]').text()).toContain("The translation service is not available");
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeDefined();
    expect(wrapper.findAll("button").every((button) => button.attributes("disabled") !== undefined)).toBe(true);
  });
  it("rejects identical language choices", async () => {
    await setup();
    await chooseLanguage("Source language", "en");
    await chooseLanguage("Target language", "en");
    await wrapper.get("textarea").setValue("Hello");
    expect(
      wrapper.get(".translate-button").attributes("disabled"),
    ).toBeDefined();
    expect(wrapper.get('[role="alert"]').text()).toContain("different");
  });
  it("can reload the catalog after a backend failure", async () => {
    fetchMock.mockRejectedValueOnce(new Error("offline"));
    await setup();
    expect(wrapper.get('[role="alert"]').text()).toContain(
      "Cannot load languages",
    );
    await wrapper.get(".notice button").trigger("click");
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
  });
  it("disables all controls until the initial availability check succeeds", async () => {
    let resolveCheck!: (response: { ok: boolean }) => void;
    fetchMock.mockImplementation((url: string) =>
      url.endsWith("/api/availability")
        ? new Promise((resolve) => { resolveCheck = resolve; })
        : Promise.resolve({ ok: true, json: async () => catalog }),
    );
    await setup();
    expect(wrapper.text()).toContain("Checking translation service availability");
    expect(wrapper.findAll("button").every((button) => button.attributes("disabled") !== undefined)).toBe(true);
    expect(wrapper.get("textarea").attributes("disabled")).toBeDefined();
    resolveCheck({ ok: true });
    await flushPromises();
    expect(wrapper.get("textarea").attributes("disabled")).toBeUndefined();
  });
  it.each(["unreachable", "unhealthy"])("locks the app when Ollama is %s and recovers automatically", async (failure) => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url.endsWith("/api/availability")) {
        if (failure === "unreachable") throw new TypeError("Failed to fetch");
        return { ok: false, status: 503 };
      }
      return { ok: true, json: async () => catalog };
    });
    await setup();
    expect(wrapper.get('[role="alert"]').text()).toContain("The translation service is not available");
    expect(wrapper.findAll("button").every((button) => button.attributes("disabled") !== undefined)).toBe(true);
    expect(wrapper.get("textarea").attributes("disabled")).toBeDefined();
    expect(languageSelect("Source language").props("disabled")).toBe(true);
    expect(languageSelect("Target language").props("disabled")).toBe(true);
    expect(wrapper.get('[aria-label="Translator home"]').attributes("href")).toBeUndefined();
    await wrapper.get("form").trigger("submit");
    expect(fetchMock).toHaveBeenCalledTimes(2);

    fetchMock.mockResolvedValue({ ok: true });
    await vi.advanceTimersByTimeAsync(10000);
    await flushPromises();
    expect(wrapper.find('[role="alert"]').exists()).toBe(false);
    expect(wrapper.get("textarea").attributes("disabled")).toBeUndefined();
    expect(languageSelect("Source language").props("disabled")).toBe(false);
    expect(fetchMock).toHaveBeenLastCalledWith("/translator/api/availability", expect.objectContaining({ cache: "no-store" }));
  });
  it("clears results and disables copying if Ollama stops while the app is open", async () => {
    await setup();
    fetchMock.mockResolvedValueOnce({ ok: true, json: async () => answer });
    await submit();
    expect(wrapper.find(".translated-text").exists()).toBe(true);
    fetchMock.mockResolvedValue({ ok: false, status: 503 });
    await vi.advanceTimersByTimeAsync(10000);
    await flushPromises();
    expect(wrapper.find(".translated-text").exists()).toBe(false);
    expect(wrapper.findAll("button").every((button) => button.attributes("disabled") !== undefined)).toBe(true);
  });
  it("stops availability checks when unmounted", async () => {
    await setup();
    wrapper.unmount();
    await vi.advanceTimersByTimeAsync(30000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});
