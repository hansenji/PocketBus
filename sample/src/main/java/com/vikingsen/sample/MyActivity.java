package com.vikingsen.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.vikingsen.bus.EventBus;
import com.vikingsen.bus.Registrar;
import com.vikingsen.bus.Subscribe;
import com.vikingsen.bus.ThreadMode;

import java.util.Locale;

public class MyActivity extends Activity {

    private static final String TAG = "MyActivity";

    private EventBus eventBus = EventBus.getDefault();
    private Registrar registrar = new MyActivityBusRegistrar(this);

    private int i = 0;

    private Button button;
    private TextView textView;
    private ScrollView scrollView;
    private Integer lastEventId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EventBus.enableDebug(true);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample);

        button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                eventBus.post(++i);
            }
        });

        textView = (TextView) findViewById(R.id.textView);
        scrollView = (ScrollView) findViewById(R.id.scrollView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        eventBus.register(registrar);
    }

    @Override
    protected void onStop() {
//        eventBus.unregister(registrar);
        eventBus.post(-1);
        super.onStop();
    }

    @Subscribe
    public void handleDefault(Integer i) {
        eventBus.post(new Foo(i, "handleDefault", Thread.currentThread().getName()));
    }

    @Subscribe(ThreadMode.BACKGROUND)
    public void handleBackground(Integer i) {
        eventBus.post(new Foo(i, "handleBackground", Thread.currentThread().getName()));
        if (i % 5 == 0) {
            eventBus.post(new GC());
        }
    }

    @Subscribe(ThreadMode.MAIN)
    public void handleMain(Integer i) {
        eventBus.post(new Foo(i, "handleMain", Thread.currentThread().getName()));
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
