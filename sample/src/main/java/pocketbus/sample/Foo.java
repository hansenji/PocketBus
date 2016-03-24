package pocketbus.sample;

public interface Foo {
    <T> Baz<T> getRegistrar(T target);
}
