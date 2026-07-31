import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'in.cauverystore.app',
  appName: 'Cauvery Store',
  webDir: 'build',
  server: {
    url: 'https://cauverystore.in',
    androidScheme: 'https',
    // Google Sign-In must open in the system browser (Google blocks OAuth
    // inside WebViews). Razorpay's checkout is an iframe injected into the
    // page, not a popup, so its domains must be allowed to navigate inside
    // the WebView itself or the iframe silently fails to load any content.
    allowNavigation: ['cauverystore.in', '*.cauverystore.in', '*.razorpay.com', 'razorpay.com']
  }
};

export default config;
