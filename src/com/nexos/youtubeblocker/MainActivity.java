package com.nexos.youtubeblocker;

import adrt.ADRTLogCatReader;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.util.Locale;

/**
 * Tube Lite v1.1 - reconstruido desde el APK v1.0.
 * Mejoras v1.1 (solo salto de anuncios): MutationObserver para saltar
 * casi al instante (mas intervalo de respaldo), cierre de banners
 * overlay y silencio durante anuncios. Resto identico a v1.0.
 */
public class MainActivity extends Activity {
    private static final String HOME = "https://www.youtube.com/";
    private static MainActivity activeActivity;
    private SharedPreferences prefs;
    private Button speedButton;
    private TextView statusView;
    private Button toggleButton;
    private BackgroundMediaWebView webView;
    private boolean blockerEnabled = true;
    private double playbackSpeed = 1.0d;
    private int adsDetected = 0;
    private int adsSkipped = 0;
    private int blockedCount = 0;
    private Handler sleepHandler;
    private Runnable sleepTask;
    private LinearLayout toolbarLayout;
    private ProgressBar progressBar;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient webChromeClient;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private String lastTitle = "";

    @Override
    public void onCreate(Bundle bundle) {
        ADRTLogCatReader.onContext(this, "com.aide.ui");
        super.onCreate(bundle);
        activeActivity = this;
        prefs = getSharedPreferences("ytb_settings", MODE_PRIVATE);
        blockerEnabled = prefs.getBoolean("auto_skip", true);
        playbackSpeed = Double.longBitsToDouble(prefs.getLong(
                "speed_bits", Double.doubleToLongBits(1.0d)));
        if (!isValidSpeed(playbackSpeed)) {
            playbackSpeed = 1.0d;
        }
        adsDetected = prefs.getInt("ads_detected", 0);
        adsSkipped = prefs.getInt("ads_skipped", 0);
        startPlaybackService();
        buildUi();
        configureWebView();
        webView.loadUrl(HOME);
    }

    private void startPlaybackService() {
        try {
            Intent intent = new Intent(this, PlaybackService.class);
            if (Build.VERSION.SDK_INT >= 26) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        } catch (Exception e) {
            // Sin servicio no hay controles; la app sigue funcionando.
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(24, 24, 24));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        bar.setBackgroundColor(Color.rgb(32, 32, 32));
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                bar.setElevation(dp(4));
            } catch (Exception e) {
            }
        }

        Button back = makeButton("‹");
        back.setContentDescription("Atrás");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                }
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        Button reload = makeButton("↻");
        reload.setContentDescription("Recargar");
        reload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (webView != null) {
                    webView.reload();
                }
            }
        });
        bar.addView(reload, new LinearLayout.LayoutParams(dp(42), dp(42)));

        TextView title = new TextView(this);
        title.setText("Tube Lite");
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(8), 0, dp(4), 0);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        Button menu = makeButton("☰");
        menu.setContentDescription("Abrir menú de herramientas");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showToolsMenu();
            }
        });
        bar.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(42)));
        toolbarLayout = bar;
        root.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(50)));

        progressBar = new ProgressBar(this, null,
                android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        if (Build.VERSION.SDK_INT >= 21) {
            try {
                progressBar.getProgressDrawable().setColorFilter(
                        0xFFFF0000, android.graphics.PorterDuff.Mode.SRC_IN);
            } catch (Exception e) {
            }
        }
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4)));
        statusView = new TextView(this);
        statusView.setTextColor(Color.LTGRAY);
        statusView.setTextSize(11);
        statusView.setSingleLine(true);
        statusView.setPadding(dp(10), dp(4), dp(10), dp(4));
        statusView.setBackgroundColor(Color.rgb(27, 27, 27));
        statusView.setSingleLine(true);
        statusView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        webView = new BackgroundMediaWebView(this);
        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        FrameLayout decor = new FrameLayout(this);
        decor.addView(root, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        fullscreenContainer = new FrameLayout(this);
        fullscreenContainer.setVisibility(View.GONE);
        fullscreenContainer.setBackgroundColor(Color.BLACK);
        decor.addView(fullscreenContainer, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(decor);
        updateStatus();
    }

    private void showSpeedDialog() {
        final String[] labels = {"0.5x", "0.75x", "1.0x", "1.25x",
                "1.5x", "1.75x", "2.0x", "2.5x", "3.0x", "Otra…"};
        new AlertDialog.Builder(this).setTitle("Velocidad")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which == labels.length - 1) {
                            showCustomSpeedDialog();
                            return;
                        }
                        setPlaybackSpeed(Double.parseDouble(labels[which]
                                .substring(0, labels[which].length() - 1)));
                    }
                }).show();
    }

    private void showCustomSpeedDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.valueOf(playbackSpeed));
        new AlertDialog.Builder(this).setTitle("Velocidad personalizada")
                .setMessage("Entre 0.5 y 3.0")
                .setView(input)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Aplicar",
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        try {
                            double value = Double.parseDouble(input.getText()
                                    .toString().trim().replace(',', '.'));
                            if (!isValidSpeed(value)) {
                                Toast.makeText(MainActivity.this,
                                        "Fuera de rango (0.5–3.0)",
                                        Toast.LENGTH_SHORT).show();
                                return;
                            }
                            setPlaybackSpeed(value);
                        } catch (NumberFormatException e) {
                            Toast.makeText(MainActivity.this,
                                    "Número inválido",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }).show();
    }

    private void setPlaybackSpeed(double speed) {
        playbackSpeed = speed;
        prefs.edit().putLong("speed_bits",
                Double.doubleToLongBits(playbackSpeed)).apply();
        if (webView != null) {
            applyPlaybackSpeed(webView);
        }
        updateStatus();
        Toast.makeText(this, "Velocidad: " + playbackSpeed + "x",
                Toast.LENGTH_SHORT).show();
    }

    private void toggleAmplifier() {
        boolean on = !prefs.getBoolean("amp_enabled", false);
        prefs.edit().putBoolean("amp_enabled", on).apply();
        PlaybackService.setAmplifier(this, on);
        updateStatus();
        Toast.makeText(this, on ? "Amplificador +15% activado"
                : "Amplificador desactivado", Toast.LENGTH_SHORT).show();
    }

    private void showSleepDialog() {
        final String[] labels = {"Apagado", "15 minutos", "30 minutos",
                "60 minutos", "90 minutos"};
        final int[] minutes = {0, 15, 30, 60, 90};
        new AlertDialog.Builder(this).setTitle("Apagado automático")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        scheduleSleep(minutes[which]);
                    }
                }).show();
    }

    private void scheduleSleep(int minutes) {
        if (sleepHandler == null) {
            sleepHandler = new Handler();
        }
        if (sleepTask != null) {
            sleepHandler.removeCallbacks(sleepTask);
            sleepTask = null;
        }
        if (minutes <= 0) {
            Toast.makeText(this, "Apagado automático desactivado",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final WebView target = webView;
        sleepTask = new Runnable() {
            @Override
            public void run() {
                if (target != null) {
                    target.evaluateJavascript(
                            "(function(){var v=document.querySelector("
                            + "'video');if(v)v.pause();})();", null);
                }
                Toast.makeText(MainActivity.this,
                        "Apagado automático: pausa", Toast.LENGTH_SHORT).show();
                sleepTask = null;
            }
        };
        sleepHandler.postDelayed(sleepTask, minutes * 60L * 1000L);
        Toast.makeText(this, "Apagado en " + minutes + " minutos",
                Toast.LENGTH_SHORT).show();
    }

    private void showToolsMenu() {
        String[] items = new String[8];
        items[0] = blockerEnabled
                ? "Bloqueador automático: ACTIVADO"
                : "Bloqueador automático: DESACTIVADO";
        items[1] = "Velocidad de reproducción: " + playbackSpeed
                + "x (cambiar)";
        items[2] = "Estadísticas locales";
        items[3] = "Diagnóstico de segundo plano";
        items[4] = "Limpiar caché de la página";
        items[5] = "Restablecer preferencias";
        items[6] = "Apagado automático";
        items[7] = prefs.getBoolean("amp_enabled", false)
                ? "Amplificador +15%: ACTIVADO"
                : "Amplificador +15%: DESACTIVADO";
        new AlertDialog.Builder(this).setTitle("Herramientas")
                .setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    blockerEnabled = !blockerEnabled;
                    prefs.edit().putBoolean("auto_skip", blockerEnabled)
                            .apply();
                    updateStatus();
                    if (webView != null) {
                        injectAdSkipScript(webView);
                    }
                    Toast.makeText(MainActivity.this,
                            blockerEnabled ? "Auto-saltar activado"
                                    : "Auto-saltar desactivado",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (which == 1) {
                    showSpeedDialog();
                    return;
                }
                if (which == 2) {
                    showInfo("Estadísticas locales",
                            "Anuncios detectados: " + adsDetected
                            + "\nIntentos de salto: " + adsSkipped
                            + "\nVelocidad actual: " + playbackSpeed
                            + "x\nBloqueador: "
                            + (blockerEnabled ? "activado" : "desactivado"));
                    return;
                }
                if (which == 3) {
                    showInfo("Diagnóstico",
                            "Servicio de reproducción: iniciado al abrir la aplicación\n"
                            + "WebView de segundo plano: habilitado\n"
                            + "Controles multimedia: disponibles en la notificación\n"
                            + "\nAndroid o YouTube aún pueden imponer "
                            + "restricciones según el dispositivo.");
                    return;
                }
                if (which == 4) {
                    if (webView != null) {
                        webView.clearCache(true);
                    }
                    Toast.makeText(MainActivity.this,
                            "Caché de la página limpiada",
                            Toast.LENGTH_SHORT).show();
                } else if (which == 5) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Restablecer preferencias")
                            .setMessage("Se restaurará Auto-saltar activado y "
                                    + "velocidad 1x. No se borrarán las "
                                    + "estadísticas.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Restablecer",
                                    new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    blockerEnabled = true;
                                    playbackSpeed = 1.0d;
                                    prefs.edit()
                                            .putBoolean("auto_skip", true)
                                            .putLong("speed_bits",
                                                    Double.doubleToLongBits(
                                                            1.0d))
                                            .apply();
                                    updateStatus();
                                    if (webView != null) {
                                        injectAdSkipScript(webView);
                                        applyPlaybackSpeed(webView);
                                    }
                                    Toast.makeText(MainActivity.this,
                                            "Preferencias restablecidas",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }).show();
                } else if (which == 6) {
                    showSleepDialog();
                } else if (which == 7) {
                    toggleAmplifier();
                }
            }
        }).show();
    }

    private void showInfo(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("Cerrar", null).show();
    }

    private Button makeButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setAllCaps(false);
        button.setPadding(dp(1), 0, dp(1), 0);
        if (Build.VERSION.SDK_INT >= 21) {
            button.setBackgroundTintList(ColorStateList.valueOf(
                    Color.rgb(55, 55, 55)));
            try {
                button.setElevation(dp(2));
            } catch (Exception e) {
            }
        }
        return button;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setBackgroundColor(Color.BLACK);
        webView.addJavascriptInterface(new PlaybackBridge(), "YTBBridge");
        webChromeClient = new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progressBar == null) {
                    return;
                }
                if (progress < 100) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(progress);
                } else {
                    progressBar.setProgress(100);
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onShowCustomView(View view,
                    WebChromeClient.CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                toolbarLayout.setVisibility(View.GONE);
                webView.setVisibility(View.GONE);
                fullscreenContainer.setVisibility(View.VISIBLE);
                fullscreenContainer.addView(view,
                        new FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT));
                setRequestedOrientation(android.content.pm.ActivityInfo
                        .SCREEN_ORIENTATION_LANDSCAPE);
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) {
                    return;
                }
                fullscreenContainer.removeView(customView);
                customView = null;
                fullscreenContainer.setVisibility(View.GONE);
                toolbarLayout.setVisibility(View.VISIBLE);
                webView.setVisibility(View.VISIBLE);
                setRequestedOrientation(android.content.pm.ActivityInfo
                        .SCREEN_ORIENTATION_UNSPECIFIED);
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
            }
        };
        webView.setWebChromeClient(webChromeClient);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                    String url) {
                if (blockerEnabled && shouldBlock(url)) {
                    recordBlocked(url);
                    return emptyResponse();
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view,
                    WebResourceRequest request) {
                String url = request == null || request.getUrl() == null
                        ? "" : request.getUrl().toString();
                if (blockerEnabled && shouldBlock(url)) {
                    recordBlocked(url);
                    return emptyResponse();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAdSkipScript(view);
                applyPlaybackSpeed(view);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) {
                    return false;
                }
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    return false;
                }
                openExternal(url);
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                    WebResourceRequest request) {
                if (request == null || request.getUrl() == null
                        || !request.isForMainFrame()) {
                    return false;
                }
                String url = request.getUrl().toString();
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    return false;
                }
                openExternal(url);
                return true;
            }
        });
    }

    private void openExternal(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No se puede abrir el enlace",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private boolean shouldBlock(String rawUrl) {
        if (rawUrl == null || rawUrl.length() == 0) {
            return false;
        }
        String url = rawUrl.toLowerCase(Locale.US);
        if (url.contains("googleads.g.doubleclick.net/pagead/id")) {
            return true;
        }
        if (url.contains("static.doubleclick.net/instream/ad_status.js")) {
            return true;
        }
        if (url.contains("youtube.com/pagead/adview")) {
            return true;
        }
        if (url.contains("youtube.com/pagead/interaction/")) {
            return true;
        }
        if (url.contains("youtube.com/pagead/1p-user-list/")) {
            return true;
        }
        if (url.contains("google.com/pagead/1p-user-list/")) {
            return true;
        }
        if (url.contains("youtube.com/pcs/activeview")) {
            return true;
        }
        if (url.contains("google.com/pagead/aclk")) {
            return true;
        }
        return false;
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse("text/plain", "UTF-8",
                new ByteArrayInputStream(new byte[0]));
    }

    private void recordBlocked(String url) {
        blockedCount++;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateStatus();
            }
        });
    }

    private void injectAdSkipScript(WebView webView) {
        if (webView == null) {
            return;
        }
        String script = "(function(){try{"
                + "if(window.__ytbSkipTimer){clearInterval(window.__ytbSkipTimer);}"
                + "if(window.__ytbObserver){try{window.__ytbObserver.disconnect();}catch(e){}window.__ytbObserver=null;}"
                + "window.__ytbAdWasActive=false;window.__ytbAdVideo=null;window.__ytbAdOldRate=1;"
                + "window.__ytbAdOldMuted=false;window.__ytbAdCounted=false;window.__ytbSkipReported=false;"
                + "window.__ytbLastCheck=0;"
                + "window.__ytbAutoEnabled="
                + (blockerEnabled ? "true" : "false") + ";"
                + "window.__ytbUserRate=" + playbackSpeed + ";"
                + "window.__ytbCheck=function(){try{"
                + "window.__ytbLastCheck=Date.now();"
                + "var p=document.querySelector('#movie_player');"
                + "var ad=!!(p&&(p.classList.contains('ad-showing')||p.classList.contains('ad-interrupting')));"
                + "var v=p?p.querySelector('video'):document.querySelector('video');"
                + "if(ad&&v){"
                + "if(!window.__ytbAdWasActive||window.__ytbAdVideo!==v){"
                + "window.__ytbAdVideo=v;window.__ytbAdOldRate=v.playbackRate||window.__ytbUserRate||1;"
                + "window.__ytbAdOldMuted=!!v.muted;window.__ytbAdCounted=false;window.__ytbSkipReported=false;}"
                + "window.__ytbAdWasActive=true;"
                + "if(window.__ytbAutoEnabled){try{v.playbackRate=16;}catch(e){}try{v.muted=true;}catch(e){}}"
                + "if(!window.__ytbAdCounted){window.__ytbAdCounted=true;if(window.YTBBridge)YTBBridge.adDetected();}"
                + "var sels=['.ytp-ad-skip-button','.ytp-ad-skip-button-modern','.ytp-skip-ad-button','.ytp-ad-skip-button-slot','.ytp-ad-skip-button-container button'];"
                + "var b=null;"
                + "for(var i=0;i<sels.length&&!b;i++){var e=document.querySelector(sels[i]);"
                + "if(e){var inner=null;try{inner=e.querySelector('button');}catch(x){}b=inner||e;}}"
                + "if(!b){var a=p?p.querySelectorAll('button,[role=button]'):[];"
                + "for(var j=0;j<a.length;j++){"
                + "var t=((a[j].innerText||'')+' '+(a[j].getAttribute('aria-label')||'')).toLowerCase();"
                + "if(t.indexOf('saltar')>=0||t.indexOf('omitir')>=0||t.indexOf('pular')>=0||t.indexOf('skip ad')>=0||t==='skip'){b=a[j];break;}}}"
                + "if(!b){try{var d=document.querySelectorAll('button');"
                + "for(var k=0;k<d.length&&k<300;k++){"
                + "var t2=((d[k].innerText||'')+' '+(d[k].getAttribute('aria-label')||'')).toLowerCase();"
                + "if(t2.indexOf('saltar')>=0||t2.indexOf('omitir')>=0||t2.indexOf('pular')>=0||t2.indexOf('skip ad')>=0||t2==='skip'){b=d[k];break;}}}catch(x){}}"
                + "if(window.__ytbAutoEnabled&&b){"
                + "var r=b.getBoundingClientRect(),s=getComputedStyle(b);"
                + "if(r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'&&!b.disabled){"
                + "b.click();if(!window.__ytbSkipReported){window.__ytbSkipReported=true;if(window.YTBBridge)YTBBridge.adSkipped();}}}"
                + "}else if(window.__ytbAdWasActive){"
                + "if(window.__ytbAdVideo){"
                + "try{window.__ytbAdVideo.playbackRate=window.__ytbUserRate||window.__ytbAdOldRate||1;}catch(e){}"
                + "try{window.__ytbAdVideo.muted=!!window.__ytbAdOldMuted;}catch(e){}}"
                + "window.__ytbAdWasActive=false;window.__ytbAdVideo=null;window.__ytbAdCounted=false;window.__ytbSkipReported=false;}"
                + "try{"
                + "if(!ad&&v&&v!==window.__ytbLastVideo){window.__ytbLastVideo=v;"
                + "try{v.playbackRate=window.__ytbUserRate||1;}catch(e){}}"
                + "var csels=['.ytp-ad-overlay-close-button','.ytp-ad-overlay-close-container button'];"
                + "for(var k=0;k<csels.length;k++){"
                + "var list=document.querySelectorAll(csels[k]);"
                + "for(var m=0;m<list.length&&m<5;m++){var c=list[m];"
                + "if(window.__ytbAutoEnabled&&c){"
                + "var cr=c.getBoundingClientRect(),cs=getComputedStyle(c);"
                + "if(cr.width>0&&cr.height>0&&cs.display!=='none'&&cs.visibility!=='hidden'&&!c.disabled){"
                + "try{c.click();}catch(e){}}}}}"
                + "}catch(e){}"
                + "var vid=document.querySelector('#movie_player video')||document.querySelector('video');"
                + "if(vid){"
                + "var actualState=!vid.paused&&!vid.ended;"
                + "if(!document.hidden){window.__ytbWasPlaying=actualState;}"
                + "else if(window.__ytbWasPlaying&&!actualState&&!vid.ended){"
                + "try{var resumePromise=vid.play();if(resumePromise&&resumePromise.catch)resumePromise.catch(function(){});}catch(e){}}"
                + "if(window.YTBBridge){var title=(document.title||'YouTube').replace(/ - YouTube$/,'');"
                + "var state=actualState||(document.hidden&&!!window.__ytbWasPlaying&&!vid.ended);"
                + "if(state!==window.__ytbLastState||title!==window.__ytbLastTitle){"
                + "window.__ytbLastState=state;window.__ytbLastTitle=title;YTBBridge.playbackState(state,title);}}}"
                + "}catch(e){}};"
                + "window.__ytbSkipTimer=setInterval(window.__ytbCheck,1200);"
                + "try{if(window.MutationObserver){"
                + "window.__ytbObserver=new MutationObserver(function(){"
                + "if(Date.now()-window.__ytbLastCheck<150)return;"
                + "try{window.__ytbCheck();}catch(e){}});"
                + "window.__ytbObserver.observe(document.documentElement,"
                + "{childList:true,subtree:true,attributes:true,attributeFilter:['class']});"
                + "}}catch(e){}"
                + "if(window.__ytbVisibilityHandler){document.removeEventListener('visibilitychange',window.__ytbVisibilityHandler);}"
                + "window.__ytbVisibilityHandler=function(){"
                + "if(document.hidden&&window.__ytbWasPlaying){"
                + "var v=document.querySelector('#movie_player video')||document.querySelector('video');"
                + "if(v&&!v.ended){try{var p=v.play();if(p&&p.catch)p.catch(function(){});}catch(e){}}}};"
                + "document.addEventListener('visibilitychange',window.__ytbVisibilityHandler,false);"
                + "}catch(e){}})();";
        webView.evaluateJavascript(script, null);
    }

    private void applyPlaybackSpeed(WebView webView) {
        if (webView == null) {
            return;
        }
        String script = "(function(){"
                + "var v=document.querySelector('#movie_player video')"
                + "||document.querySelector('video');"
                + "if(v&&!document.querySelector('#movie_player.ad-showing')){"
                + "try{v.playbackRate=" + playbackSpeed + ";}catch(e){}}"
                + "window.__ytbUserRate=" + playbackSpeed + ";})();";
        webView.evaluateJavascript(script, null);
    }

    private String speedLabel() {
        return "Vel " + playbackSpeed + "x";
    }

    private boolean isValidSpeed(double speed) {
        return speed >= 0.5d && speed <= 3.0d;
    }

    private String nowPlayingSuffix() {
        if (lastTitle == null || lastTitle.length() == 0) {
            return "";
        }
        String t = lastTitle.trim();
        if (t.length() > 40) {
            t = t.substring(0, 40) + "…";
        }
        return " · " + t;
    }

    private void updateStatus() {
        if (toggleButton != null) {
            toggleButton.setText(blockerEnabled ? "Auto: ON" : "Auto: OFF");
        }
        if (speedButton != null) {
            speedButton.setText(speedLabel());
        }
        if (statusView != null) {
            statusView.setText((blockerEnabled ? "Auto-saltar activo"
                    : "Auto-saltar apagado")
                    + " · Velocidad " + playbackSpeed
                    + "x · Anuncios detectados: " + adsDetected
                    + " · Saltados: " + adsSkipped
                    + " · Controles de fondo en notificación"
                    + " · Bloqueadas: " + blockedCount
                    + " · Amp: " + (prefs.getBoolean("amp_enabled", false)
                            ? "ON" : "OFF")
                    + nowPlayingSuffix());
        }
    }

    private int dp(int value) {
        return (int) ((value * getResources().getDisplayMetrics().density)
                + 0.5f);
    }

    public class PlaybackBridge {
        @JavascriptInterface
        public void playbackState(boolean playing, String title) {
            lastTitle = title == null ? "" : title;
            PlaybackService.updatePlayback(MainActivity.this, playing, title);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStatus();
                }
            });
        }

        @JavascriptInterface
        public void adDetected() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adsDetected++;
                    prefs.edit().putInt("ads_detected", adsDetected).apply();
                    updateStatus();
                }
            });
        }

        @JavascriptInterface
        public void adSkipped() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    adsSkipped++;
                    prefs.edit().putInt("ads_skipped", adsSkipped).apply();
                    updateStatus();
                    Toast.makeText(MainActivity.this,
                            "Intento de saltar anuncio",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public static void performMediaAction(final String action) {
        final MainActivity activity = activeActivity;
        if (activity == null || activity.webView == null) {
            return;
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String script;
                if ("toggle".equals(action)) {
                    script = "(function(){var v=document.querySelector("
                            + "'#movie_player video')||document.querySelector"
                            + "('video');if(v){if(v.paused){var p=v.play();"
                            + "if(p&&p.catch)p.catch(function(){});}"
                            + "else v.pause();}})();";
                } else if ("next".equals(action)) {
                    script = "(function(){var b=document.querySelector("
                            + "'.ytp-next-button');if(b)b.click();else "
                            + "location.href='https://www.youtube.com/';})();";
                } else {
                    script = "(function(){var b=document.querySelector("
                            + "'.ytp-prev-button');if(b)b.click();else {var v="
                            + "document.querySelector('#movie_player video')"
                            + "||document.querySelector('video');"
                            + "if(v)v.currentTime=0;}})();";
                }
                activity.webView.evaluateJavascript(script, null);
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (customView != null && webChromeClient != null) {
            webChromeClient.onHideCustomView();
            return;
        }
        if (webView == null || !webView.canGoBack()) {
            moveTaskToBack(true);
        } else {
            webView.goBack();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (activeActivity == this) {
            activeActivity = null;
        }
        if (sleepHandler != null && sleepTask != null) {
            sleepHandler.removeCallbacks(sleepTask);
            sleepTask = null;
        }
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.removeJavascriptInterface("YTBBridge");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

}
