package pocketbus.sample;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

import pocketbus.Bus;
import pocketbus.Subscribe;
import pocketbus.ThreadMode;

public class MyActivity extends Activity {

    private static final String TAG = "MyActivity";

    private Bus bus = Bus.getDefault();

    private int i = 0;
    private int stickyI = 0;

    private Button button;
    private TextView textView;
    private ScrollView scrollView;
    private Integer lastEventId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Bus.setDebug(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermission();
        }

        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bus.post(++i);
            }
        });

        textView = (TextView) findViewById(R.id.textView);
        scrollView = (ScrollView) findViewById(R.id.scrollView);

        if (savedInstanceState != null) {
            stickyI = savedInstanceState.getInt(TAG);
        }

        bus.postSticky(++stickyI);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(TAG, stickyI);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermission() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bus.register(this);
    }

    @Override
    protected void onStop() {
        bus.unregister(this);
        bus.post(-1);
        super.onStop();
    }

    @Subscribe
    public void handleDefault(Integer i) {
        bus.post(new Foo(i, "handleDefault", Thread.currentThread().getName()));
    }

    @Subscribe(ThreadMode.BACKGROUND)
    public void handleBackground(Integer i) {
        bus.post(new Foo(i, "handleBackground", Thread.currentThread().getName()));
        if (i % 5 == 0) {
            bus.post(new GC());
        }
    }

    @Subscribe(ThreadMode.MAIN)
    public void handleMain(Integer i) {
        bus.post(new Foo(i, "handleMain", Thread.currentThread().getName()));
    }

    @Subscribe(ThreadMode.MAIN)
    public synchronized void handleCurrent(Foo foo) {
        if (!foo.eventId.equals(lastEventId)) {
            lastEventId = foo.eventId;
            textView.setText(getString(R.string.text, textView.getText(), "\n"));
        }
        textView.setText(getString(R.string.text, textView.getText(), foo));
        scrollView.fullScroll(ScrollView.FOCUS_DOWN);
    }

    @Subscribe
    public void handleGC(GC event) {
        Log.d(TAG, "GC Thread: " + Thread.currentThread().getName());
        System.gc();
    }

    public static class Foo {

        private final Integer eventId;
        private final String subscription;
        private final String threadName;

        public Foo(Integer eventId, String subscription, String threadName) {
            this.eventId = eventId;
            this.subscription = subscription;
            this.threadName = threadName;
        }

        @Override
        public String toString() {
            return String.format(Locale.getDefault(), "Event %d:\tSubscription %s:\tThread %s", eventId, subscription, threadName);
        }
    }

    public static class GC {
    }
}
