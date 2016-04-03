package pocketbus.sample;

import pocketbus.Bus;
import pocketbus.Subscribe;
import pocketbus.SubscriptionRegistration;

public class SingleSubscription {

    private final Callback callback;
    private final SubscriptionRegistration registration;

    public SingleSubscription(Callback callback) {
        this.callback = callback;
        registration = Bus.getDefault().register(this);
    }

    @Subscribe
    public void handle(Object event) {
        callback.assertEvent(event);
    }

    public void unregister() {
        Bus.getDefault().unregister(registration);
    }

    public interface Callback {
        void assertEvent(Object event);
    }
}
