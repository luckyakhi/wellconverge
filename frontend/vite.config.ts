import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// The API base is proxied so the browser only ever talks to the frontend origin.
// In Docker, VITE_API_TARGET points at the backend service; locally it defaults to localhost.
const apiTarget = process.env.VITE_API_TARGET ?? "http://localhost:8080";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    port: 5173,
    proxy: {
      "/api": { target: apiTarget, changeOrigin: true },
    },
  },
});
