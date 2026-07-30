import { registerPlugin } from '@capacitor/core';

// Bridges to GoogleAuthPlugin.java (native Google Sign-In inside the Android app).
// Only usable when Capacitor.isNativePlatform() is true; on the regular website
// this plugin has no native implementation and is simply never called.
const GoogleAuthNative = registerPlugin('GoogleAuth');

export default GoogleAuthNative;
