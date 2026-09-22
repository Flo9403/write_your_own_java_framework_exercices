package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class InjectorRegistry {

    private final HashMap<Class<?>, Supplier<?>> instances;

    public InjectorRegistry(){
        instances = new HashMap<>();
        super();
    }

    public <T> void registerInstance(Class<T> type, T instance) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(instance);
        registerProvider(type, () -> instance);
    }

    public <T> T lookupInstance(Class<T> type){
        Objects.requireNonNull(type);
        var supplier = instances.get(type);
        if (supplier == null){
            throw new IllegalStateException("instance not registered for type : " + type.getName());
        }
        return type.cast(supplier.get());
    }

    public <T> void registerProvider(Class<T> type, Supplier<? extends T> supplier){
        Objects.requireNonNull(type);
        Objects.requireNonNull(supplier);
        var result = instances.putIfAbsent(type, supplier);
        if (result != null){
            throw new IllegalStateException("Instance already registered for type : " + type.getName());
        }
    }

    //package private
    static List<PropertyDescriptor> findInjectableProperties(Class<?> type){
        Objects.requireNonNull(type);
        var beanInfo = Utils.beanInfo(type);
        var properties = beanInfo.getPropertyDescriptors();
        return Arrays.stream(properties)
                .filter(property -> {
                    var setter = property.getWriteMethod();
                    return setter != null && setter.isAnnotationPresent(Inject.class);
                })
                .toList();
    }

    private static Constructor<?> findConstructor(Class<?> providerClass){
        var constructors = Arrays.stream(providerClass.getConstructors())
                .filter(c -> c.isAnnotationPresent(Inject.class))
                .toList();

        return switch (constructors.size()){
            case 0 -> Utils.defaultConstructor(providerClass);
            case 1 -> constructors.getFirst();
            default -> throw new IllegalStateException();
        };
    }

    public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass){
        Objects.requireNonNull(type);
        Objects.requireNonNull(providerClass);

        var constructor = findConstructor(providerClass);
        var propetiesList = findInjectableProperties(type);

        registerProvider(type, () -> {
            var args = Arrays.stream(constructor.getParameterTypes())
                    .map(this::lookupInstance)
                    .toArray();
            var instance = Utils.newInstance(constructor, args);
            for (var property : propetiesList){
                var setter = property.getWriteMethod();
                var value = lookupInstance(property.getPropertyType());
                Utils.invokeMethod(instance, setter, value);
            }
            return type.cast(instance);
        });
    }

    public void registerProviderClass(Class<?> providerClass){
        Objects.requireNonNull(providerClass);
        registerProviderClassInternal(providerClass);
    }

    private <T> void registerProviderClassInternal(Class<T> providerClass){
        Objects.requireNonNull(providerClass);
        registerProviderClass(providerClass, providerClass);
    }

}