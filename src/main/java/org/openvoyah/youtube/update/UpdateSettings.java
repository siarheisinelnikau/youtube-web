package org.openvoyah.youtube.update;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.View;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.openvoyah.youtube.R;

import java.util.function.Consumer;

public final class UpdateSettings implements AutoCloseable {
    private static final int ALLOW_INSTALL = 811;
    private static final int INSTALL = 812;

    private final Activity activity;
    private final Updates updates;
    private final TextView check;
    private final TextView status;
    private final TextView install;
    private final Consumer<Updates.Screen> observer = this::render;

    private Updates.Screen screen;
    private boolean installRequested;

    public UpdateSettings(Activity activity, TextView version, Switch startup, TextView check,
                          TextView status, TextView install) {
        this.activity = activity;
        this.updates = Updates.get(activity);
        this.check = check;
        this.status = status;
        this.install = install;
        version.setText(activity.getString(R.string.installed_version, updates.installed()));
        startup.setChecked(updates.startupEnabled());
        startup.setOnCheckedChangeListener((view, checked) -> updates.setStartupEnabled(checked));
        check.setOnClickListener(view -> updates.check());
        install.setOnClickListener(view -> {
            if (screen.phase() == Updates.Phase.READY && screen.file() != null && screen.file().isFile()) {
                openInstaller();
            } else {
                installRequested = true;
                updates.download();
            }
        });
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        updates.observe(observer);
    }

    private void render(Updates.Screen next) {
        screen = next;
        String text = switch (screen.phase()) {
            case IDLE -> "";
            case CHECKING -> activity.getString(R.string.checking);
            case CURRENT -> activity.getString(R.string.up_to_date);
            case AVAILABLE -> activity.getString(R.string.update_available, screen.version());
            case CHECK_FAILED -> activity.getString(R.string.check_failed);
            case DOWNLOADING -> activity.getString(R.string.downloading, screen.percent());
            case READY -> activity.getString(R.string.ready_to_install, screen.version());
            case DOWNLOAD_FAILED -> activity.getString(R.string.download_failed);
        };
        status.setText(text);
        status.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        check.setEnabled(screen.phase() != Updates.Phase.CHECKING && screen.phase() != Updates.Phase.DOWNLOADING);
        boolean canInstall = screen.phase() == Updates.Phase.AVAILABLE
                || screen.phase() == Updates.Phase.DOWNLOAD_FAILED || screen.phase() == Updates.Phase.READY;
        install.setVisibility(canInstall ? View.VISIBLE : View.GONE);
        install.setText(screen.phase() == Updates.Phase.READY ? R.string.install_update : R.string.download_install);
        if (screen.phase() == Updates.Phase.DOWNLOAD_FAILED) installRequested = false;
        if (screen.phase() == Updates.Phase.READY && installRequested) {
            installRequested = false;
            if (activity.hasWindowFocus() && !activity.isFinishing()) openInstaller();
        }
    }

    @SuppressWarnings("deprecation")
    private void openInstaller() {
        try {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Intent permission = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(permission, ALLOW_INSTALL);
                return;
            }
            if (screen == null || screen.file() == null || !screen.file().isFile()) {
                installRequested = true;
                updates.download();
                return;
            }
            Intent installer = new Intent(Intent.ACTION_INSTALL_PACKAGE, UpdateFiles.uri(activity))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            activity.startActivityForResult(installer, INSTALL);
        } catch (ActivityNotFoundException | SecurityException error) {
            showInstallError();
        }
    }

    public void onActivityResult(int request, int result) {
        if (request == ALLOW_INSTALL && activity.getPackageManager().canRequestPackageInstalls()) openInstaller();
        if (request == INSTALL && result == Activity.RESULT_FIRST_USER) showInstallError();
    }

    private void showInstallError() {
        Toast.makeText(activity, R.string.install_failed, Toast.LENGTH_LONG).show();
    }

    @Override public void close() {
        updates.remove(observer);
    }
}
