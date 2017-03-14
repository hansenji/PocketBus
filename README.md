# PocketBus
Rx based event bus for Android that doesn't leak memory or use reflection

## Setup
Annotate `Application` class with `@Registry`
```java
@Registry
public class App extends Application {
  // ...
}
```

Run the build to generate the `BusRegistry`
Get a singleton instance of the bus and set the registry (usually in `App.onCreate()`)
```java
public void onCreate() {
  super.onCreate();
  Bus.getDefault().setRegistry(new BusRegistry());
  // ...
}
```

## Usage
Annotate methods with `@Subscribe`
```java
@Subscribe
public void handle(FooEvent event) {
  // Some code
}
```

Get a singleton instance of the bus and register the class containing subscriptions in `onStart()`
```java
public void onStart() {
  Bus.getDefault().register(this);
}
```

Post events to the bus by getting the singleton instance and calling
```java
Bus.getDefault().post(new FooEvent());
```
PocketBus will deliver that event to all subscriptions currentlty registered with that bus.

Unsubscribe from the bus in `onStop()`
```java
public void onStop() {
  Bus.getDefault().unregister(this);
}
```

## Special Features
```Java
@Subscribe(ThreadMode.CURRENT) // Runs on the same thread that posted the event asynchronous (default)
@Subscribe(ThreadMode.MAIN) // Runs on the UI thread asynchronous
@Subscribe(ThreadMode.BACKGROUND) // Runs on a background thread asynchronous
```

## Download
Via gradle from *jcenter*
```
compile 'com.vikingsen:pocketbus:1.1.0'
provided 'com.vikingsen:pocketbus-compiler:1.1.0'
```

License
-------

    Copyright 2016 Jordan Hansen, Brian Wernick

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
