import type { Config } from 'tailwindcss';

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#172033',
        line: '#d7dde8',
        brand: '#1f7a6d',
        'brand-dark': '#155e55',
        canvas: '#f4f7f9',
      },
      boxShadow: {
        panel: '0 1px 2px rgba(23, 32, 51, 0.06), 0 8px 24px rgba(23, 32, 51, 0.04)',
        elevated: '0 16px 48px rgba(15, 23, 42, 0.14)',
      },
    },
  },
  plugins: [],
} satisfies Config;
