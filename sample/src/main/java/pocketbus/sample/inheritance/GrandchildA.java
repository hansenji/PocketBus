package pocketbus.sample.inheritance;

import pocketbus.Subscribe;

public class GrandChildA extends ChildA {

    public GrandChildA(Callback callback) {
        super(callback);
    }

    @Subscribe
    public void handle(GrandChildEvent event) {
        callback.assertGrandChild(event);
    }
}
