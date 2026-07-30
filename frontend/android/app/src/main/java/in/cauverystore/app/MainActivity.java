package in.cauverystore.app;

import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import androidx.browser.customtabs.CustomTabsIntent;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

/**
 * Google Sign-In is done natively via GoogleAuthPlugin (Play Services), not the
 * web flow, since Google blocks OAuth inside embedded WebViews
 * ("disallowed_useragent"). The URL interception below is kept as a defensive
 * fallback in case anything ever navigates to accounts.google.com directly.
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

    private void openInCustomTab(String url) {
        CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
        customTabsIntent.launchUrl(this, Uri.parse(url));
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
                        openInCustomTab(request.getUrl().toString());
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
