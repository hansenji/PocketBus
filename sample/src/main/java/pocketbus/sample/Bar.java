package pocketbus.sample;

public class Bar implements Foo {
    public Baz<Qux> getRegistrar(Qux target) {
        return null;
    }

    @Override
    public <T> Baz<T> getRegistrar(T target) {
        return null;
    }
}
