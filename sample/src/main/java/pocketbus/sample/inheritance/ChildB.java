package pocketbus.sample.inheritance;

import pocketbus.Subscribe;

public class ChildB extends ParentB {

    public ChildB(Callback callback) {
        super(callback);
    }

    @Subscribe
    public void handle(ChildEvent event) {
        callback.assertChild(event);
    }
}
