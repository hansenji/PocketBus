package pocketbus.sample;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;
import pocketbus.Bus;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LeakCanary.install(this);
        Bus bus = new Bus.Builder()
                .setEventCleanupCount(21)
                .build();
        Bus.setDefault(bus);
        Bus.setDebug(true);
    }
}
