import vue from "@vitejs/plugin-vue";
import { defineConfig } from "vite";

export default defineConfig({
  base: "/translator/",
  plugins: [vue()],
  server: {
    host: "127.0.0.1",
    proxy: {
      "/translator/api": {
        target: process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
        timeout: 150000,
        proxyTimeout: 150000,
      },
    },
  },
});
