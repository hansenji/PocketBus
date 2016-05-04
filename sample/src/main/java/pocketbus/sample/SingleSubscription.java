package pocketbus.sample;

import pocketbus.Bus;
import pocketbus.Subscribe;

public class SingleSubscription {

    private final Callback callback;

    public SingleSubscription(Callback callback) {
        this.callback = callback;
        Bus.getDefault().register(this);
    }

    @Subscribe
    public void handle(Object event) {
        callback.assertEvent(event);
    }

    public void unregister() {
        Bus.getDefault().unregister(this);
    }

    public interface Callback {
        void assertEvent(Object event);
    }
}
