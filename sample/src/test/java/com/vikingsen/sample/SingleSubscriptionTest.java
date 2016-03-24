package com.vikingsen.sample;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import pocketbus.BuildConfig;
import pocketbus.Bus;
import pocketbus.sample.SingleSubscription;
import rx.schedulers.Schedulers;

import static junit.framework.Assert.assertEquals;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class SingleSubscriptionTest implements SingleSubscription.Callback {
    private int eventCount = 0;

    @Test
    public void test() throws Exception {
        Bus bus = new Bus.Builder().setCurrentScheduler(Schedulers.immediate()).build();
        Bus.setDefault(bus);
        SingleSubscription singleSubscription = new SingleSubscription(this);
        bus.post(1);
        singleSubscription.unregister();
        assertEquals(1, eventCount);
    }

    @Override
    public void assertEvent(Object event) {
        eventCount++;
    }
}