package pocketbus.sample.inheritance;

import pocketbus.Bus;
import pocketbus.Subscribe;

public class ParentB {

    protected final Callback callback;

    public ParentB(Callback callback) {
        this.callback = callback;
        Bus.getDefault().register(this);
    }

    @Subscribe
    public void handle(ParentEvent event) {
        callback.assertParent(event);
    }

    public void unregister() {
        Bus.getDefault().unregister(this);
    }

    public interface Callback {
        void assertParent(ParentEvent event);
        void assertChild(ChildEvent event);
    }
}
