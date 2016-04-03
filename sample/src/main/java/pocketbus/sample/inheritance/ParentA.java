package pocketbus.sample.inheritance;

import pocketbus.Bus;
import pocketbus.Subscribe;
import pocketbus.SubscriptionRegistration;

public class ParentA {

    protected final Callback callback;
    private final SubscriptionRegistration registration;

    public ParentA(Callback callback) {
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
        void assertGrandChild(GrandChildEvent event);
    }
}
