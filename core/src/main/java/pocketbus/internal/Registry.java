package pocketbus.internal;

import pocketbus.Registrar;

public interface Registry {
    <T> Registrar getRegistrar(T target);
}
