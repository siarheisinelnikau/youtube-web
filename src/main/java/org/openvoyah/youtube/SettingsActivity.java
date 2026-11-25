package org.openvoyah.youtube;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The startup animation switch. YouTube Web shows nothing but the page, so Android opens this screen from the app's
 * page in Settings instead of a button in the app.
 */
public final class SettingsActivity extends Activity {
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
        setContentView(column);
    }
}
