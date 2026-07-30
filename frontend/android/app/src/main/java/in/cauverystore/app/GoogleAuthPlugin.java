package in.cauverystore.app;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

/**
 * Native Google Sign-In for the wrapped Android app.
 *
 * Google blocks the web-based OAuth flow inside embedded WebViews, so instead of
 * rendering Google's web sign-in button, the app calls this plugin's signIn()
 * method, which uses Android's native Google Sign-In (Play Services) UI. The ID
 * token it returns has the same audience (WEB_CLIENT_ID) as the web flow, so it
 * verifies against the exact same backend endpoint (POST /api/auth/google) with
 * no server-side changes needed.
 */
@CapacitorPlugin(name = "GoogleAuth")
public class GoogleAuthPlugin extends Plugin {

    // Must match the Web application OAuth client ID used by the website's
    // Google Sign-In button (REACT_APP_GOOGLE_CLIENT_ID), not the Android client ID.
    private static final String WEB_CLIENT_ID =
            "1030938967337-je414s546r37p9ii8rcvidbggmull1j1.apps.googleusercontent.com";

    private GoogleSignInClient signInClient;

    @Override
    public void load() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(WEB_CLIENT_ID)
                .requestEmail()
                .build();
        signInClient = GoogleSignIn.getClient(getContext(), gso);
    }

    @PluginMethod
    public void signIn(PluginCall call) {
        // Always sign out first so the account chooser appears instead of silently
        // reusing whichever Google account last succeeded on this device.
        signInClient.signOut().addOnCompleteListener(task -> {
            Intent intent = signInClient.getSignInIntent();
            startActivityForResult(call, intent, "handleSignInResult");
        });
    }

    @ActivityCallback
    private void handleSignInResult(PluginCall call, ActivityResult result) {
        if (call == null) {
            return;
        }
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            JSObject ret = new JSObject();
            ret.put("idToken", account.getIdToken());
            ret.put("email", account.getEmail());
            ret.put("name", account.getDisplayName());
            call.resolve(ret);
        } catch (ApiException e) {
            call.reject("Google sign-in failed (code " + e.getStatusCode() + ")", e);
        }
    }
}
