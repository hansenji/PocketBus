package pocketbus.sample.inheritance;

import pocketbus.Bus;
import pocketbus.Subscribe;
import pocketbus.SubscriptionRegistration;

public class ParentB {

    protected final Callback callback;
    private final SubscriptionRegistration registration;

    public ParentB(Callback callback) {
        this.callback = callback;
        registration = Bus.getDefault().register(this);
    }

    @Subscribe
    public void handle(ParentEvent event) {
        callback.assertParent(event);
    }

    public void unregister() {
        Bus.getDefault().unregister(registration);
    }

    public interface Callback {
        void assertParent(ParentEvent event);
        void assertChild(ChildEvent event);
    }
}
