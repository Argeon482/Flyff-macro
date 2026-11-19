package com.example.dualweb;

import android.os.Bundle;
import android.os.IBinder;

interface IAccount2WebViewService {
    Bundle attachSurface(IBinder hostToken, int displayId, int width, int height);
    void resizeSurface(int width, int height);
    void detachSurface();

    void loadUrl(String url);
    void reload();
    void goBack();
    void setDesktopUserAgent(boolean desktop);

    boolean dispatchKey(int keyCode, long holdMs, int metaState, int dispatchMode);
    boolean dispatchCanvasTap(float normX, float normY, long holdMs, int dispatchMode);

    void clearAccount2Session();
    Bundle getStatus();
}
