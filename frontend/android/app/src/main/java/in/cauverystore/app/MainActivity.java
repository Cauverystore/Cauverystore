package in.cauverystore.app;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.Toast;
import androidx.browser.customtabs.CustomTabsIntent;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

/**
 * Google Sign-In is done natively via GoogleAuthPlugin (Play Services), not the
 * web flow, since Google blocks OAuth inside embedded WebViews
 * ("disallowed_useragent"). The URL interception below is kept as a defensive
 * fallback in case anything ever navigates to accounts.google.com directly.
 *
 * Separately, UPI payment apps (Google Pay, PhonePe, Paytm, ...) are launched via
 * custom URL schemes like upi://pay, not http(s). A plain WebView has no idea how
 * to hand those off to another app, so without this, Razorpay silently hides the
 * UPI option inside the app. Any non-http(s) URL is now launched as a generic
 * Android intent so the OS can route it to whichever app registered that scheme.
 */
public class MainActivity extends BridgeActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        registerPlugin(GoogleAuthPlugin.class);
        super.onCreate(savedInstanceState);
    }

    private static boolean isGoogleAuthUrl(String url) {
        return url != null && url.contains("accounts.google.com");
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private void openInCustomTab(String url) {
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
    }

    private boolean tryLaunchExternalApp(String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to handle this. Is the payment app installed?", Toast.LENGTH_LONG).show();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        WebView webView = this.bridge.getWebView();

        webView.setWebViewClient(new BridgeWebViewClient(this.bridge) {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isGoogleAuthUrl(url)) {
                    openInCustomTab(url);
                    return true;
                }
                if (!isHttpUrl(url)) {
                    return tryLaunchExternalApp(url);
                }
                return super.shouldOverrideUrlLoading(view, request);
            }
        });

        webView.getSettings().setSupportMultipleWindows(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                // Popup window requests don't expose the target URL directly, so use a
                // throwaway WebView purely to capture the URL, then hand it to Custom Tabs.
                WebView popup = new WebView(view.getContext());
                popup.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView popupView, WebResourceRequest request) {
                        String popupUrl = request.getUrl().toString();
                        if (!isHttpUrl(popupUrl)) {
                            return tryLaunchExternalApp(popupUrl);
                        }
                        openInCustomTab(popupUrl);
                        return true;
                    }
                });
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(popup);
                resultMsg.sendToTarget();
                return true;
            }
        });
    }
}
