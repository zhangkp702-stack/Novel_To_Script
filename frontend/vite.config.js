import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        configure(proxy) {
          proxy.on("proxyRes", (proxyRes, req) => {
            if (req.url?.includes("/stream")) {
              proxyRes.headers["cache-control"] = "no-cache";
              proxyRes.headers["x-accel-buffering"] = "no";
              const contentType = proxyRes.headers["content-type"];
              if (contentType && !String(contentType).toLowerCase().includes("charset")) {
                proxyRes.headers["content-type"] = `${contentType};charset=UTF-8`;
              }
            }
          });
        }
      }
    }
  }
});
