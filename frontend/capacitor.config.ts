import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'in.cauverystore.app',
  appName: 'Cauvery Store',
  webDir: 'build',
  server: {
    url: 'https://cauverystore.in',
    androidScheme: 'https',
    // Google Sign-In and Razorpay checkout must open in the system browser,
    // not the app's embedded WebView (Google blocks OAuth inside WebViews).
    allowNavigation: ['cauverystore.in', '*.cauverystore.in']
  }
};

export default config;
