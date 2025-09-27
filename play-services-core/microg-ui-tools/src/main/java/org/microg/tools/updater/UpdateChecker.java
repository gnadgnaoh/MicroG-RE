package org.microg.tools.updater;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONException;
import org.json.JSONObject;
import org.microg.tools.ui.R;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.CompletableFuture;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/WSTxda/MicroG-RE/releases/latest";
    private static final String GITHUB_RELEASE_LINK = "https://github.com/WSTxda/MicroG-RE/releases/latest";

    private final WeakReference<Context> contextRef;
    private final OkHttpClient client;

    public UpdateChecker(Context context) {
        this.contextRef = new WeakReference<>(context);
        this.client = new OkHttpClient.Builder().retryOnConnectionFailure(true).build();
    }

    public void checkForUpdates(View view, Runnable onComplete) {
        CompletableFuture.supplyAsync(this::fetchLatestVersion).thenAccept(latestVersion -> runOnMainThread(() -> {
            handleLatestVersion(latestVersion, view);
            onComplete.run();
        })).exceptionally(throwable -> {
            runOnMainThread(() -> {
                handleError(throwable, view);
                onComplete.run();
            });
            return null;
        });
    }

    private String fetchLatestVersion() {
        Request request = new Request.Builder().url(GITHUB_API_URL).build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return parseLatestVersion(response.body().string());
            } else {
                throw new IOException("Unsuccessful response: " + response.code());
            }
        } catch (IOException e) {
            throw new RuntimeException("Connection error", e);
        }
    }

    private String parseLatestVersion(String jsonData) {
        try {
            JSONObject jsonObject = new JSONObject(jsonData);
            return jsonObject.optString("tag_name", "");
        } catch (JSONException e) {
            throw new RuntimeException("Error processing JSON", e);
        }
    }

    private void handleLatestVersion(String latestVersion, View view) {
        Context context = contextRef.get();
        if (context == null || view == null) return;

        String appVersion = context.getString(R.string.github_tag_version);

        if (appVersion.compareTo(latestVersion) < 0) {
            showSnackbarWithAction(view, context.getString(R.string.update_available), context.getString(R.string.snackbar_button_download), v -> openGitHubReleaseLink(context));
        } else {
            showSnackbar(view, context.getString(R.string.no_update_available));
        }
    }

    private void handleError(Throwable throwable, View view) {
        Context context = contextRef.get();
        if (context == null || view == null) return;

        String errorMessage = throwable.getMessage() != null && throwable.getMessage().toLowerCase().contains("connection") ? context.getString(R.string.error_connection) + " " + throwable.getMessage() : context.getString(R.string.error_others) + " " + throwable.getMessage();
        showSnackbar(view, errorMessage);
    }

    private void showSnackbar(View view, String message) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG);
        configureSnackbar(snackbar);
        snackbar.show();
    }

    private void showSnackbarWithAction(View view, String message, String actionText, View.OnClickListener actionListener) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG).setAction(actionText, actionListener);
        configureSnackbar(snackbar);
        snackbar.show();
    }

    private void configureSnackbar(Snackbar snackbar) {
        ViewCompat.setOnApplyWindowInsetsListener(snackbar.getView(), (v, insets) -> {
            int bottomPadding = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = bottomPadding;
            v.setLayoutParams(params);
            return insets;
        });
    }

    private void openGitHubReleaseLink(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASE_LINK));
        context.startActivity(intent);
    }

    private void runOnMainThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}