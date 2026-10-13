import { defineConfig } from 'vite';
export default defineConfig({
  base: './',
  server: { proxy: {
    '/appointments': 'http://localhost:8080', '/providers': 'http://localhost:8080',
    '/slots': 'http://localhost:8080', '/suggestions': 'http://localhost:8080',
    '/reschedule-jobs': 'http://localhost:8080', '/scheduling': 'http://localhost:8080'
  }},
  build: { outDir: '../src/main/resources/static', emptyOutDir: false }
});
