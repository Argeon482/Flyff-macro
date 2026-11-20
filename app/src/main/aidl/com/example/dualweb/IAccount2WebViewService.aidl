package com.example.dualweb;

import android.os.Bundle;
import android.os.IBinder;
import android.view.MotionEvent;

interface IAccount2WebViewService {
    Bundle attachSurface(IBinder hostToken, int displayId, int width, int height);
    void resizeSurface(int width, int height);
    void detachSurface();

    void loadUrl(String url);
    void reload();
    void goBack();
    void setDesktopUserAgent(boolean desktop);

    oneway void dispatchMotionEvent(in MotionEvent event);
    boolean dispatchKey(int keyCode, long holdMs, int metaState, int dispatchMode);
    boolean dispatchCanvasTap(float normX, float normY, long holdMs, int dispatchMode);
    boolean dispatchCanvasSwipe(
        float startNormX,
        float startNormY,
        float endNormX,
        float endNormY,
        long durationMs,
        int steps,
        int dispatchMode
    );
    boolean dispatchText(String text, boolean pressEnter, int dispatchMode);

    void clearAccount2Session();
    Bundle getStatus();
}
