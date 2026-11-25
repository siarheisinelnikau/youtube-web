package org.openvoyah.youtube;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Lays its children out at 1/{@link #getScale scale} of its own size and draws them scale times larger.
 *
 * <p>The WebView takes its pixel ratio from the display and ignores the density a window gets from a per-app DPI
 * profile, so the page would keep the display's scale whatever the profile says. Inside this frame a 1775 px window
 * with a 240 dpi profile on a 160 dpi display becomes a 1183 CSS px page drawn at 1.5x: the layout YouTube would pick
 * on a 240 dpi screen. Touches reach the children through the inverse transform, as for any scaled view.
 */
final class ScaledFrame extends FrameLayout {
    private float scale = 1f;

    ScaledFrame(Context context) {
        super(context);
    }

    float getScale() {
        return scale;
    }

    void setScale(float value) {
        if (scale == value) return;
        scale = value;
        requestLayout();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width, height);
        int childWidth = MeasureSpec.makeMeasureSpec(Math.round(width / scale), MeasureSpec.EXACTLY);
        int childHeight = MeasureSpec.makeMeasureSpec(Math.round(height / scale), MeasureSpec.EXACTLY);
        for (int index = 0; index < getChildCount(); index++) getChildAt(index).measure(childWidth, childHeight);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < getChildCount(); index++) {
            View child = getChildAt(index);
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
            child.setPivotX(0f);
            child.setPivotY(0f);
            child.setScaleX(scale);
            child.setScaleY(scale);
        }
    }
}
