package com.nexos.youtubeblocker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.webkit.WebView;

public class BackgroundMediaWebView extends WebView {
    public BackgroundMediaWebView(Context context) {
        super(context);
    }

    public BackgroundMediaWebView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    public BackgroundMediaWebView(Context context, AttributeSet attributeSet,
            int i) {
        super(context, attributeSet, i);
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        if (visibility != View.GONE) {
            super.onWindowVisibilityChanged(visibility);
        }
    }
}
