package com.example.dialertheme;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.VideoProfile;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

public class InCallActivity extends Activity {

    private AppTheme t;
    private TextView avatar, nameTv, numTv, statusTv;
    private View ring;
    private LinearLayout controls;
    private ObjectAnimator pulse;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean finishScheduled = false;
    private FrameLayout avatarBox;
    private boolean showKeypad = false;
    private final StringBuilder dtmf = new StringBuilder();
    private TextView dtmfDisplay;
    private String builtKey = "";

    private final CallManager.Listener listener = new CallManager.Listener() {
        @Override
        public void onChanged() {
            refresh();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            Call c = CallManager.primary();
            if (c != null) statusTv.setText(statusText(c));
            handler.postDelayed(this, 1000);
        }
    };

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        t = AppTheme.current(this);
        Ui.styleWindow(this, t);

        if (CallManager.primary() == null) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(24), dp(56), dp(24), dp(28));

        // avatar with pulsing neon ring
        avatarBox = new FrameLayout(this);
        ring = new View(this);
        GradientDrawable rg = new GradientDrawable();
        rg.setShape(GradientDrawable.OVAL);
        rg.setColor(Color.TRANSPARENT);
        rg.setStroke(dp(3), t.primary);
        ring.setBackground(rg);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(136), dp(136));
        rp.gravity = Gravity.CENTER;
        avatarBox.addView(ring, rp);

        avatar = Ui.text(this, "", 44, t.primary);
        avatar.setTypeface(null, Typeface.BOLD);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(Ui.avatarBg(this, t));
        Ui.glowText(avatar, t.primary, t);
        FrameLayout.LayoutParams ap = new FrameLayout.LayoutParams(dp(120), dp(120));
        ap.gravity = Gravity.CENTER;
        avatarBox.addView(avatar, ap);
        root.addView(avatarBox, new LinearLayout.LayoutParams(dp(180), dp(180)));

        nameTv = Ui.text(this, "", 28, t.text);
        nameTv.setTypeface(null, Typeface.BOLD);
        nameTv.setGravity(Gravity.CENTER);
        Ui.glowText(nameTv, t.accent, t);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        np.topMargin = dp(12);
        root.addView(nameTv, np);

        numTv = Ui.text(this, "", 16, t.text2);
        numTv.setGravity(Gravity.CENTER);
        numTv.setTextDirection(View.TEXT_DIRECTION_LTR);
        root.addView(numTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        statusTv = Ui.text(this, "", 18, t.primary);
        statusTv.setGravity(Gravity.CENTER);
        // keep the timer crisp: no glow/shadow on it
        statusTv.setShadowLayer(0f, 0f, 0f, 0);
        statusTv.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(10);
        root.addView(statusTv, sp);

        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1f));

        controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setGravity(Gravity.CENTER);
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        CallManager.addListener(listener);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusTv != null) {
            handler.removeCallbacks(tick);
            handler.post(tick);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        CallManager.removeListener(listener);
        handler.removeCallbacksAndMessages(null);
        setPulse(false);
        super.onDestroy();
    }

    private void setPulse(boolean on) {
        if (ring == null) return;
        if (on) {
            if (pulse == null) {
                pulse = ObjectAnimator.ofPropertyValuesHolder(ring,
                        PropertyValuesHolder.ofFloat("scaleX", 1f, 1.3f),
                        PropertyValuesHolder.ofFloat("scaleY", 1f, 1.3f),
                        PropertyValuesHolder.ofFloat("alpha", 0.9f, 0.05f));
                pulse.setDuration(1300);
                pulse.setRepeatCount(ValueAnimator.INFINITE);
                pulse.setInterpolator(new DecelerateInterpolator());
                pulse.start();
            }
        } else if (pulse != null) {
            pulse.cancel();
            pulse = null;
            ring.setScaleX(1f);
            ring.setScaleY(1f);
            ring.setAlpha(0.6f);
        }
    }

    private void syncAudio() {
        CallService svc = CallManager.service;
        if (svc == null) return;
        CallAudioState as = svc.getCallAudioState();
        if (as != null) {
            CallManager.muted = as.isMuted();
            CallManager.route = as.getRoute();
        }
    }

    private void refresh() {
        if (statusTv == null) return;
        final Call c = CallManager.primary();
        if (c == null) {
            setPulse(false);
            statusTv.setText("تماس پایان یافت");
            controls.removeAllViews();
            builtKey = "";
            if (!finishScheduled) {
                finishScheduled = true;
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                }, 800);
            }
            return;
        }
        syncAudio();
        String title = CallManager.title(this, c);
        String number = CallManager.number(c);
        nameTv.setText(title);
        numTv.setText((number != null && !number.equals(title)) ? number : "");
        avatar.setText(initial(title));
        statusTv.setText(statusText(c));
        int s = c.getState();
        setPulse(s == Call.STATE_RINGING || s == Call.STATE_DIALING || s == Call.STATE_CONNECTING);
        buildControls(c);
    }

    private String initial(String title) {
        if (TextUtils.isEmpty(title)) return "#";
        char ch = title.charAt(0);
        return Character.isLetter(ch) ? String.valueOf(ch).toUpperCase() : "#";
    }

    private String statusText(Call c) {
        switch (c.getState()) {
            case Call.STATE_RINGING:
                return "تماس ورودی";
            case Call.STATE_DIALING:
            case Call.STATE_CONNECTING:
                return "در حال برقراری تماس…";
            case Call.STATE_HOLDING:
                return "در انتظار";
            case Call.STATE_ACTIVE:
                long ct = c.getDetails().getConnectTimeMillis();
                long sec = ct > 0 ? (System.currentTimeMillis() - ct) / 1000 : 0;
                if (sec < 0) sec = 0;
                return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60);
            case Call.STATE_DISCONNECTED:
                return "پایان تماس";
            default:
                return "…";
        }
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = dp(24);
        r.setLayoutParams(p);
        return r;
    }

    private View circleButton(int icon, String label, Drawable bg, int iconColor,
                              boolean glowIcon, View.OnClickListener l) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER);
        c.setBackground(bg);
        c.setOnClickListener(l);
        c.addView(new IconView(this, icon, iconColor, glowIcon),
                new LinearLayout.LayoutParams(dp(32), dp(32)));
        col.addView(c, new LinearLayout.LayoutParams(dp(74), dp(74)));

        TextView lb = Ui.text(this, label, 13, t.text2);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, dp(6), 0, 0);
        col.addView(lb);
        return col;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private View toggle(int icon, String label, boolean active, View.OnClickListener l) {
        Drawable bg = active ? Ui.glowButton(this, t.primary) : Ui.keyBg(this, t);
        int ic = active ? t.onPrimary : t.text;
        return circleButton(icon, label, bg, ic, !active && t.neon, l);
    }

    @Override
    public void onBackPressed() {
        if (showKeypad) {
            showKeypad = false;
            refresh();
        } else {
            super.onBackPressed();
        }
    }

    private void updateDtmf() {
        if (dtmfDisplay != null) dtmfDisplay.setText(dtmf.toString());
    }

    private View dtmfKey(final Call c, final String k) {
        TextView v = Ui.text(this, k, 24, t.text);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Ui.keyBg(this, t));
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        view.setPressed(true);
                        c.playDtmfTone(k.charAt(0));
                        dtmf.append(k);
                        updateDtmf();
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        view.setPressed(false);
                        c.stopDtmfTone();
                        return true;
                    default:
                        return false;
                }
            }
        });
        return v;
    }

    private void buildKeypad(final Call c) {
        dtmfDisplay = Ui.text(this, dtmf.toString(), 26, t.text);
        dtmfDisplay.setShadowLayer(0f, 0f, 0f, 0);
        dtmfDisplay.setGravity(Gravity.CENTER);
        dtmfDisplay.setSingleLine(true);
        dtmfDisplay.setEllipsize(TextUtils.TruncateAt.START);
        dtmfDisplay.setTextDirection(View.TEXT_DIRECTION_LTR);
        dtmfDisplay.setMinHeight(dp(40));
        controls.addView(dtmfDisplay, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"*", "0", "#"}};
        for (String[] rowKeys : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            r.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            for (String k : rowKeys) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(58), dp(58));
                p.setMargins(dp(8), dp(4), dp(8), dp(4));
                r.addView(dtmfKey(c, k), p);
            }
            controls.addView(r);
        }

        LinearLayout bottom = row();
        ((LinearLayout.LayoutParams) bottom.getLayoutParams()).topMargin = dp(10);
        bottom.addView(circleButton(IconView.KEYPAD, "بستن صفحه‌کلید", Ui.keyBg(this, t),
                t.text, t.neon, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showKeypad = false;
                        refresh();
                    }
                }), weight());
        bottom.addView(circleButton(IconView.END, "پایان تماس", Ui.glowButton(this, AppTheme.RED),
                Color.WHITE, false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        c.disconnect();
                    }
                }), weight());
        controls.addView(bottom);
    }

    private void buildControls(final Call c) {
        int s = c.getState();
        String key = s + "|" + CallManager.muted + "|" + CallManager.route + "|" + showKeypad;
        if (key.equals(builtKey) && controls.getChildCount() > 0) return;
        builtKey = key;

        controls.removeAllViews();
        avatarBox.setVisibility((showKeypad && s != Call.STATE_RINGING) ? View.GONE : View.VISIBLE);
        if (showKeypad && s != Call.STATE_RINGING) {
            buildKeypad(c);
            return;
        }

        if (s == Call.STATE_RINGING) {
            LinearLayout r = row();
            r.addView(circleButton(IconView.END, "رد", Ui.glowButton(this, AppTheme.RED),
                    Color.WHITE, false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            c.reject(false, null);
                        }
                    }), weight());
            r.addView(circleButton(IconView.PHONE, "پاسخ", Ui.glowButton(this, AppTheme.GREEN),
                    Color.WHITE, false, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            c.answer(VideoProfile.STATE_AUDIO_ONLY);
                        }
                    }), weight());
            controls.addView(r);
            return;
        }

        boolean muted = CallManager.muted;
        boolean spk = CallManager.route == CallAudioState.ROUTE_SPEAKER;
        boolean hold = s == Call.STATE_HOLDING;

        LinearLayout r = row();
        r.addView(toggle(muted ? IconView.MIC_OFF : IconView.MIC, "بی‌صدا", muted,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CallService svc = CallManager.service;
                        if (svc != null) svc.setMuted(!CallManager.muted);
                    }
                }), weight());
        r.addView(toggle(IconView.SPEAKER, "بلندگو", spk,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        CallService svc = CallManager.service;
                        if (svc != null) {
                            boolean on = CallManager.route == CallAudioState.ROUTE_SPEAKER;
                            svc.setAudioRoute(on ? CallAudioState.ROUTE_WIRED_OR_EARPIECE
                                    : CallAudioState.ROUTE_SPEAKER);
                        }
                    }
                }), weight());
        if (s == Call.STATE_ACTIVE || s == Call.STATE_HOLDING) {
            r.addView(toggle(IconView.KEYPAD, "صفحه‌کلید", false,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            showKeypad = true;
                            refresh();
                        }
                    }), weight());
            r.addView(toggle(IconView.PAUSE, "نگه‌داشتن", hold,
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (c.getState() == Call.STATE_HOLDING) c.unhold();
                            else c.hold();
                        }
                    }), weight());
        }
        controls.addView(r);

        LinearLayout end = row();
        end.addView(circleButton(IconView.END, "پایان تماس", Ui.glowButton(this, AppTheme.RED),
                Color.WHITE, false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        c.disconnect();
                    }
                }), weight());
        controls.addView(end);
    }
}
