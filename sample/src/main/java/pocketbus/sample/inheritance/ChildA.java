package pocketbus.sample.inheritance;

import pocketbus.Subscribe;

public class ChildA extends ParentA {

    public ChildA(Callback callback) {
        super(callback);
    }

    @Subscribe
    public void handle(ChildEvent event) {
        callback.assertChild(event);
    }
}
