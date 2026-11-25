package org.openvoyah.youtube;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.openvoyah.youtube.update.UpdateSettings;

/**
 * Android opens this screen from the app's
 * page in Settings instead of a button in the app.
 */
public final class SettingsActivity extends Activity {
    private UpdateSettings updates;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int gap = Math.round(16 * getResources().getDisplayMetrics().density);
        int text = getColor(R.color.youtube_text);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(getColor(R.color.youtube_background));
        column.setFitsSystemWindows(true);
        column.setPadding(gap * 3, gap * 2, gap * 3, gap * 2);

        TextView title = new TextView(this);
        title.setText(R.string.settings);
        title.setTextColor(text);
        title.setTextSize(26f);
        column.addView(title, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Switch intro = new Switch(this);
        intro.setText(R.string.startup_animation);
        intro.setTextColor(text);
        intro.setTextSize(20f);
        intro.setMinHeight(gap * 4);
        intro.setChecked(Preferences.startupAnimation(this));
        intro.setOnCheckedChangeListener((view, checked) -> Preferences.setStartupAnimation(this, checked));
        LinearLayout.LayoutParams row = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        row.topMargin = gap;
        column.addView(intro, row);

        TextView hint = new TextView(this);
        hint.setText(R.string.startup_animation_hint);
        hint.setTextColor(getColor(R.color.youtube_muted));
        hint.setTextSize(15f);
        column.addView(hint, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        TextView heading = new TextView(this);
        heading.setText(R.string.updates);
        heading.setTextSize(22f);
        heading.setTextColor(text);
        LinearLayout.LayoutParams section = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        section.topMargin = gap * 2;
        column.addView(heading, section);

        TextView version = new TextView(this);
        version.setTextColor(getColor(R.color.youtube_muted));
        Switch startup = new Switch(this);
        startup.setText(R.string.startup_updates);
        startup.setMinHeight(gap * 3);
        Button check = new Button(this);
        check.setText(R.string.check_updates);
        check.setAllCaps(false);
        TextView status = new TextView(this);
        status.setTextColor(getColor(R.color.youtube_muted));
        Button install = new Button(this);
        install.setAllCaps(false);
        column.addView(version);
        column.addView(startup);
        column.addView(check, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        column.addView(status);
        column.addView(install, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
        updates = new UpdateSettings(this, version, startup, check, status, install);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(getColor(R.color.youtube_background));
        scroll.setFitsSystemWindows(true);
        scroll.setFillViewport(true);
        column.setFitsSystemWindows(false);
        scroll.addView(column);
        setContentView(scroll);
    }

    @SuppressWarnings("deprecation")
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        updates.onActivityResult(request, result);
    }

    @Override protected void onDestroy() {
        updates.close();
        super.onDestroy();
    }
}
