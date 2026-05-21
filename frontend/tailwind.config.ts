import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#172033',
        line: '#d7dde8',
        brand: '#1f7a6d',
      },
      boxShadow: {
        panel: '0 1px 2px rgba(23, 32, 51, 0.08)',
      },
    },
  },
  plugins: [],
} satisfies Config;
