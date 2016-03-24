package pocketbus.sample;

import android.app.Application;

import com.squareup.leakcanary.LeakCanary;
import pocketbus.Bus;
import pocketbus.Registry;

@Registry
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        LeakCanary.install(this);
        Bus bus = new Bus.Builder()
                .setEventCleanupCount(21)
                .build();
        bus.setRegistry(new BusRegistry());
        Bus.setDefault(bus);
        Bus.setDebug(true);
    }
}
