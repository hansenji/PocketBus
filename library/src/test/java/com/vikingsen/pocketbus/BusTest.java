package com.vikingsen.pocketbus;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import rx.schedulers.Schedulers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class)
public class BusTest {

    private Subscription<Foo> subscriptionMain = new Subscription<Foo>() {
        @Override
        public void handle(Foo foo) {
            assertMain(foo);
        }

        @Override
        public Class<Foo> getEventClass() {
            return Foo.class;
        }

        @Override
        public ThreadMode getThreadMode() {
            return ThreadMode.MAIN;
        }
    };

    private Subscription<Foo> subscriptionBackground = new Subscription<Foo>() {
        @Override
        public void handle(Foo foo) {
            assertBackground(foo);
        }

        @Override
        public Class<Foo> getEventClass() {
            return Foo.class;
        }

        @Override
        public ThreadMode getThreadMode() {
            return ThreadMode.BACKGROUND;
        }
    };

    private Subscription<Foo> subscriptionCurrent = new Subscription<Foo>() {
        @Override
        public void handle(Foo foo) {
            assertCurrent(foo);
        }

        @Override
        public Class<Foo> getEventClass() {
            return Foo.class;
        }

        @Override
        public ThreadMode getThreadMode() {
            return ThreadMode.CURRENT;
        }
    };
    private String uid;
    private int eventCount = 0;

    @Test
    public void testDefault() {
        Bus oDefault = Bus.getDefault();
        Bus.setDefault(new Bus.Builder().build());
        Bus nDefault = Bus.getDefault();
        assertNotEquals(oDefault, nDefault);
    }

    @Test
    public void testPost() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .build();

        String uid = UUID.randomUUID().toString();
        setTestUid(uid);

        bus.register(subscriptionMain);
        bus.register(subscriptionBackground);
        bus.register(subscriptionCurrent);

        bus.post(new Foo(uid));
        setTestUid(null);

        bus.unregister(subscriptionMain);
        bus.unregister(subscriptionBackground);
        bus.unregister(subscriptionCurrent);
        bus.post(new Foo("FAIL"));
        assertEquals(3, eventCount);
    }

    @Test
    public void testSticky() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .build();
        bus.postSticky(0);
        bus.postSticky(new Foo("foo"));
        final int[] count = {0};
        Subscription<Integer> subscription1 = new Subscription<Integer>() {
            @Override
            public void handle(Integer integer) {
                assertEquals(Integer.valueOf(0), integer);
                count[0]++;
            }

            @Override
            public Class<Integer> getEventClass() {
                return Integer.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        };
        Subscription<Integer> subscription2 = new Subscription<Integer>() {
            @Override
            public void handle(Integer integer) {
                assertEquals(Integer.valueOf(0), integer);
                count[0]++;
            }

            @Override
            public Class<Integer> getEventClass() {
                return Integer.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        };
        bus.register(subscription1);
        assertEquals(1, count[0]);
        bus.removeSticky(Integer.class);
        bus.register(subscription2);
        assertEquals(1, count[0]);
    }

    @Test
    public void testRegistrar() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .build();
        Bus.setDebug(true);
        String uid = UUID.randomUUID().toString();
        setTestUid(uid);

        Registrar registrar = new Registrar() {
            @Override
            public List<Subscription<?>> getSubscriptions() {
                return Collections.unmodifiableList(Arrays.<Subscription<?>>asList(subscriptionMain, subscriptionBackground, subscriptionCurrent));
            }
        };

        bus.register(registrar);

        bus.post(new Foo(uid));
        bus.post(1);
        setTestUid(null);

        bus.unregister(registrar);

        bus.post(new Foo("FAIL"));
        assertEquals(3, eventCount);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderException() {
        Bus.Builder builder = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundThreadPoolSize(1);
        assertEquals(1, builder.backgroundThreadPoolSize);
        new Bus.Builder().setBackgroundThreadPoolSize(0);
    }

    @Test
    public void testCleanupCount() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .setEventCleanupCount(1)
                .build();
        assertEquals(1, bus.eventCleanupCount);
    }

    @Test
    public void testNullClass() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .setEventCleanupCount(1)
                .build();
        Subscription<Foo> subscription = new Subscription<Foo>() {
            @Override
            public void handle(Foo foo) {

            }

            @Override
            public Class<Foo> getEventClass() {
                return null;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        };
        try {
            bus.register(subscription);
            fail("NullPointerException should be thrown on register");
        } catch (NullPointerException e) {
            // nothing needed
        }
        try {
            bus.unregister(subscription);
            fail("NullPointerException should be thrown on unregister");
        } catch (NullPointerException e) {
            // nothing needed
        }
    }

    @Test
    public void testAutoUnregister() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .setEventCleanupCount(1)
                .build();
        Subscription<Foo> subscription1 = new Subscription<Foo>() {
            @Override
            public void handle(Foo foo) {
                assertMain(foo);
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        };
        Subscription<Foo> subscription2 = new Subscription<Foo>() {
            @Override
            public void handle(Foo foo) {
                assertMain(foo);
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        };
        Subscription<Integer> subscription3 = new Subscription<Integer>() {
            @Override
            public void handle(Integer integer) {
                fail("Method handle in Integer Subscription should never be called");
            }

            @Override
            public Class<Integer> getEventClass() {
                return Integer.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }
        };
        Subscription<Foo> subscription4 = new Subscription<Foo>() {
            @Override
            public void handle(Foo foo) {
                assertBackground(foo);
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.BACKGROUND;
            }
        };
        Subscription<Foo> subscription5 = new Subscription<Foo>() {
            @Override
            public void handle(Foo foo) {
                assertMain(foo);
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.MAIN;
            }
        };
        bus.register(subscription1);
        bus.register(subscription2);
        bus.register(subscription3);
        bus.register(subscription4);
        bus.register(subscription5);
        setTestUid("1");
        bus.post(new Foo("1"));
        subscription1 = null;
        subscription3 = null;
        subscription4 = null;
        subscription5 = null;
        System.gc();
        setTestUid("2");
        bus.post(new Foo("2"));
        bus.post(0);
        assertEquals(5, eventCount); // This may fail due to other system gcs and how they clean up things
    }

    @Test
    public void testPostNull() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.immediate())
                .setBackgroundScheduler(Schedulers.immediate())
                .setCurrentScheduler(Schedulers.immediate())
                .build();
        bus.register(subscriptionCurrent);
        try {
            bus.post(null);
            fail("NullPointerException not thrown");
        } catch (NullPointerException e) {
            // Nothing needed
        }
        try {
            bus.postSticky(null);
            fail("NullPointerException not thrown");
        } catch (NullPointerException e) {
            // Nothing needed
        }
    }

    private void setTestUid(String uid) {
        this.uid = uid;
    }

    private void assertMain(Foo foo) {
        assertEquals(uid, foo.uid);
        eventCount++;
    }

    private void assertBackground(Foo foo) {
        assertEquals(uid, foo.uid);
        eventCount++;
    }

    private void assertCurrent(Foo foo) {
        assertEquals(uid, foo.uid);
        eventCount++;
    }
}