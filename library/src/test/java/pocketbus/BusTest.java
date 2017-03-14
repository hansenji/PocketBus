package pocketbus;

import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import io.reactivex.schedulers.Schedulers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(constants = BuildConfig.class, sdk = Build.VERSION_CODES.LOLLIPOP)
public class BusTest {

    private Subscription<Foo> subscriptionMain = new Subscription<Foo>() {
        private Foo target = new Foo("subMain");

        @Override
        public boolean handle(Foo foo) {
            assertMain(foo);
            return true;
        }

        @Override
        public Class<Foo> getEventClass() {
            return Foo.class;
        }

        @Override
        public ThreadMode getThreadMode() {
            return ThreadMode.MAIN;
        }

        @Override
        public Foo getTarget() {
            return target;
        }
    };

    private Subscription<Foo> subscriptionBackground = new Subscription<Foo>() {
        private Foo target = new Foo("subBack");

        @Override
        public boolean handle(Foo foo) {
            assertBackground(foo);
            return true;
        }

        @Override
        public Class<Foo> getEventClass() {
            return Foo.class;
        }

        @Override
        public ThreadMode getThreadMode() {
            return ThreadMode.BACKGROUND;
        }

        @Override
        public Foo getTarget() {
            return target;
        }
    };

    private Subscription<Foo> subscriptionCurrent = new Subscription<Foo>() {
        private Foo target = new Foo("subCurr");

        @Override
        public boolean handle(Foo foo) {
            assertCurrent(foo);
            return true;
        }

        @Override
        public Class<Foo> getEventClass() {
            return Foo.class;
        }

        @Override
        public ThreadMode getThreadMode() {
            return ThreadMode.CURRENT;
        }

        @Override
        public Foo getTarget() {
            return target;
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
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
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
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
                .build();
        bus.postSticky(0);
        bus.postSticky(new Foo("foo"));
        final int[] count = {0};
        Subscription<Integer> subscription1 = new Subscription<Integer>() {
            private Integer target = 1;

            @Override
            public boolean handle(Integer integer) {
                assertEquals(Integer.valueOf(0), integer);
                count[0]++;
                return true;
            }

            @Override
            public Class<Integer> getEventClass() {
                return Integer.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }

            @Override
            public Integer getTarget() {
                return target;
            }
        };
        Subscription<Integer> subscription2 = new Subscription<Integer>() {
            public Integer target = 2;

            @Override
            public boolean handle(Integer integer) {
                assertEquals(Integer.valueOf(0), integer);
                count[0]++;
                return true;
            }

            @Override
            public Class<Integer> getEventClass() {
                return Integer.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }

            @Override
            public Integer getTarget() {
                return target;
            }
        };
        bus.register(subscription1);
        assertEquals(1, count[0]);
        assertEquals(Integer.valueOf(0), bus.getSticky(Integer.class));
        assertTrue("Remove Sticky Event", bus.removeSticky(Integer.class));
        assertFalse("Remove Sticky Event", bus.removeSticky(Integer.class));
        assertNull(bus.getSticky(Integer.class));
        bus.register(subscription2);
        assertEquals(1, count[0]);
    }

    @Test
    public void testRegistrar() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
                .build();
        Bus.setDebug(true);
        String uid = UUID.randomUUID().toString();
        setTestUid(uid);

        SubscriptionRegistration subscriptionRegistration = new SubscriptionRegistration() {
            @Override
            public List<Subscription<?>> getSubscriptions() {
                return Collections.unmodifiableList(Arrays.<Subscription<?>>asList(subscriptionMain, subscriptionBackground, subscriptionCurrent));
            }
        };

        bus.register(subscriptionRegistration);

        bus.post(new Foo(uid));
        bus.post(1);
        setTestUid(null);

        bus.unregister(subscriptionRegistration);

        bus.post(new Foo("FAIL"));
        assertEquals(3, eventCount);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuilderException() {
        Bus.Builder builder = new Bus.Builder()
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundThreadPoolSize(1);
        assertEquals(1, builder.backgroundThreadPoolSize);
        new Bus.Builder().setBackgroundThreadPoolSize(0);
    }

    @Test
    public void testCleanupCount() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
                .setEventCleanupCount(1)
                .build();
        assertEquals(1, bus.eventCleanupCount);
    }

    @Test
    public void testNullClass() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
                .setEventCleanupCount(1)
                .build();
        Subscription<Foo> subscription = new Subscription<Foo>() {
            private Foo target = new Foo("sub");

            @Override
            public boolean handle(Foo foo) {
                return true;
            }

            @Override
            public Class<Foo> getEventClass() {
                return null;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }

            @Override
            public Foo getTarget() {
                return target;
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
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
                .setEventCleanupCount(1)
                .build();
        Subscription<Foo> subscription1 = new Subscription<Foo>() {
            private Foo target = new Foo("sub1");

            @Override
            public boolean handle(Foo foo) {
                assertMain(foo);
                return true;
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }

            @Override
            public Foo getTarget() {
                return target;
            }
        };
        Subscription<Foo> subscription2 = new Subscription<Foo>() {
            private Foo target = new Foo("sub2");

            @Override
            public boolean handle(Foo foo) {
                assertMain(foo);
                return true;
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }

            @Override
            public Foo getTarget() {
                return target;
            }
        };
        Subscription<Integer> subscription3 = new Subscription<Integer>() {
            private Integer target = null;

            @Override
            public boolean handle(Integer integer) {
                if (target == null) {
                    return false;
                }
                fail("Method handle in Integer Subscription should never be called");
                return false;
            }

            @Override
            public Class<Integer> getEventClass() {
                return Integer.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.CURRENT;
            }

            @Override
            public Integer getTarget() {
                return target;
            }
        };
        Subscription<Foo> subscription4 = new Subscription<Foo>() {
            private Foo target = new Foo("sub4");

            @Override
            public boolean handle(Foo foo) {
                assertBackground(foo);
                return true;
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.BACKGROUND;
            }

            @Override
            public Foo getTarget() {
                return target;
            }
        };
        Subscription<Foo> subscription5 = new Subscription<Foo>() {
            private Foo target = new Foo("sub5");

            @Override
            public boolean handle(Foo foo) {
                assertMain(foo);
                return true;
            }

            @Override
            public Class<Foo> getEventClass() {
                return Foo.class;
            }

            @Override
            public ThreadMode getThreadMode() {
                return ThreadMode.MAIN;
            }

            @Override
            public Foo getTarget() {
                return target;
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
//        assertEquals(5, eventCount); // This may fail due to other system gcs and how they clean up things
        // TODO fix this test
    }

    @Test
    public void testPostNull() {
        Bus bus = new Bus.Builder()
                .setMainScheduler(Schedulers.trampoline())
                .setBackgroundScheduler(Schedulers.trampoline())
                .setCurrentScheduler(Schedulers.trampoline())
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