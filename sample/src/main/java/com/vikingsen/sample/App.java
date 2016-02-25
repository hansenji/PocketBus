package com.vikingsen.sample;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;
import com.vikingsen.bus.EventBus;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LeakCanary.install(this);
        EventBus eventBus = new EventBus.Builder()
                .setEventCleanupCount(21)
                .build();
        EventBus.setDefault(eventBus);
        EventBus.setDebug(true);
    }
}
