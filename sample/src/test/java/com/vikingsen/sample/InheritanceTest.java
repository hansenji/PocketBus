package com.vikingsen.sample;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import pocketbus.BuildConfig;
import pocketbus.Bus;
import pocketbus.sample.BusRegistry;
import pocketbus.sample.inheritance.ChildA;
import pocketbus.sample.inheritance.ChildB;
import pocketbus.sample.inheritance.ChildEvent;
import pocketbus.sample.inheritance.GrandChildA;
import pocketbus.sample.inheritance.GrandChildEvent;
import pocketbus.sample.inheritance.ParentA;
import pocketbus.sample.inheritance.ParentB;
import pocketbus.sample.inheritance.ParentEvent;
import rx.schedulers.Schedulers;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 23)
public class InheritanceTest implements ParentA.Callback, ParentB.Callback {
    private int eventCount = 0;

    @Test
    public void testInheritance() throws Exception {
        Bus bus = new Bus.Builder().setCurrentScheduler(Schedulers.immediate()).build();
        bus.setRegistry(new BusRegistry());
        Bus.setDefault(bus);
        GrandChildA grandChildA = new GrandChildA(this);
        ChildA childA = new ChildA(this);
        ParentA parentA = new ParentA(this);
        ChildB childB = new ChildB(this);
        ParentB parentB = new ParentB(this);
        bus.post(new Object());
        bus.post(new ParentEvent());
        bus.post(new ChildEvent());
        bus.post(new GrandChildEvent());
        grandChildA.unregister();
        childA.unregister();
        parentA.unregister();
        childB.unregister();
        parentB.unregister();
        bus.post(new GrandChildEvent());
        assertEquals(9, eventCount);
    }

    @Override
    public void assertParent(ParentEvent event) {
        eventCount++;
    }

    @Override
    public void assertChild(ChildEvent event) {
        eventCount++;
    }

    public void assertGrandChild(GrandChildEvent event) {
        eventCount++;
    }
}
