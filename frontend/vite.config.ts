import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const backendUrl = env.VITE_BACKEND_URL || "http://localhost:8080";
  const websocketTarget = backendUrl.replace(/^http/, "ws");

  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": backendUrl,
        "/ws-logs": {
          target: `${websocketTarget}/ws-logs`,
          ws: true,
        },
      },
    },
  };
});
