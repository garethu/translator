// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  DOMWrapper,
  flushPromises,
  mount,
  type VueWrapper,
} from "@vue/test-utils";
import LanguageSelect from "./LanguageSelect.vue";

const languages = [
  { code: "en", label: "English" },
  { code: "nl", label: "Dutch" },
  { code: "ar", label: "Arabic" },
];
let wrapper: VueWrapper;
const browserMethods = ["scrollIntoView", "hasPointerCapture"] as const;
const originalMethods = browserMethods.map((name) =>
  Object.getOwnPropertyDescriptor(Element.prototype, name),
);

beforeEach(() => {
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
  for (const name of browserMethods) {
    Object.defineProperty(Element.prototype, name, {
      configurable: true,
      value: vi.fn(() => false),
    });
  }
});

afterEach(() => {
  wrapper?.unmount();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  browserMethods.forEach((name, index) => {
    const original = originalMethods[index];
    if (original) Object.defineProperty(Element.prototype, name, original);
    else Reflect.deleteProperty(Element.prototype, name);
  });
});

function setup(modelValue = "", disabled = false, label = "Source language") {
  wrapper = mount(LanguageSelect, {
    attachTo: document.body,
    props: {
      modelValue,
      languages,
      label,
      placeholder: "Choose a language",
      disabled,
      "onUpdate:modelValue": (value: string) =>
        wrapper.setProps({ modelValue: value }),
    },
  });
  return wrapper.get('[role="combobox"]');
}

function options() {
  return new DOMWrapper(document.body).findAll('[role="option"]');
}

describe("language select", () => {
  it("shows a named placeholder without a flag", () => {
    const trigger = setup();
    expect(trigger.attributes("aria-label")).toBe("Source language");
    expect(trigger.text()).toContain("Choose a language");
    expect(trigger.find(".language-flag").exists()).toBe(false);
    expect(wrapper.emitted("update:modelValue")).toBeUndefined();
  });

  it("opens a portaled menu and selects a language by click", async () => {
    const trigger = setup("en");
    await trigger.trigger("pointerdown", { pointerType: "mouse" });
    await flushPromises();
    await trigger.trigger("pointerup", { pointerType: "mouse" });
    await trigger.trigger("click");
    await flushPromises();

    const menu = document.body.querySelector('[role="listbox"]');
    expect(menu).not.toBeNull();
    expect(wrapper.element.contains(menu)).toBe(false);
    expect(options().map((option) => option.text())).toEqual([
      "English",
      "Dutch",
      "Arabic",
    ]);
    for (const option of options()) {
      expect(option.get(".language-flag").attributes("aria-hidden")).toBe(
        "true",
      );
      expect(
        document.getElementById(option.attributes("aria-labelledby")!)
          ?.textContent,
      ).toBe(option.text());
    }
    expect(options()[0]!.attributes("aria-selected")).toBe("true");
    expect(options()[1]!.get(".language-flag").classes()).toContain("fi-nl");
    await options()[1]!.trigger("pointerdown", { pointerType: "mouse" });
    await options()[1]!.trigger("pointerup", { pointerType: "mouse" });
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")).toEqual([["nl"]]);
    expect(trigger.text()).toContain("Dutch");
    expect(trigger.get(".language-flag").classes()).toContain("fi-nl");
    expect(document.body.querySelector('[role="listbox"]')).toBeNull();
  });

  it("opens and selects a language with the keyboard", async () => {
    const trigger = setup("en", false, "Target language");
    expect(trigger.attributes("aria-label")).toBe("Target language");
    (trigger.element as HTMLElement).focus();
    await trigger.trigger("keydown", { key: "ArrowDown" });
    await flushPromises();
    expect(options()).toHaveLength(3);

    await new DOMWrapper(document.activeElement!).trigger("keydown", {
      key: "End",
    });
    await flushPromises();
    await vi.waitFor(() =>
      expect(document.activeElement?.textContent).toContain("Arabic"),
    );
    await new DOMWrapper(document.activeElement!).trigger("keydown", {
      key: "Enter",
    });
    await flushPromises();

    expect(wrapper.emitted("update:modelValue")).toEqual([["ar"]]);
    expect(trigger.text()).toContain("Arabic");
    expect(trigger.get(".language-flag").classes()).toContain("fi-arab");
    expect(document.body.querySelector('[role="listbox"]')).toBeNull();
  });

  it("does not open a disabled trigger by click or keyboard", async () => {
    const trigger = setup("nl", true);
    expect(trigger.attributes("disabled")).toBeDefined();
    await trigger.trigger("pointerdown", { pointerType: "mouse" });
    await trigger.trigger("click");
    await trigger.trigger("keydown", { key: "ArrowDown" });
    await flushPromises();

    expect(trigger.text()).toContain("Dutch");
    expect(trigger.get(".language-flag").classes()).toContain("fi-nl");
    expect(document.body.querySelector('[role="listbox"]')).toBeNull();
    expect(wrapper.emitted("update:modelValue")).toBeUndefined();
  });
});
