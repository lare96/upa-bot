package me.upa.variable;

import me.upa.UpaBot;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import static me.upa.variable.SystemVariableRepository.BASE_PATH;

/**
 * A persistent global value important to the systems of the bot. Values must be thread-safe.
 * @param <T>
 */
public class SystemVariable<T> {

    public static <T> SystemVariable<T> of(T defaultValue, String name) {
        return new SystemVariable<>(defaultValue, name);
    }

    private volatile AtomicReference<T> value = new AtomicReference<>();
    private final String name;

    private final Path filePath;

    private SystemVariable(T defaultValue, String name) {
        this.name = name;
        value.set(defaultValue);
        filePath = BASE_PATH.resolve(name + ".bin");
    }

    public void access(Function<AtomicReference<T>, Boolean> accessFunc) {
        if(accessFunc.apply(value)) {
            save();
        }
    }
    public void accessValue(Function<T, Boolean> accessFunc) {
        if(accessFunc.apply(value.get())) {
            save();
        }
    }
    void load() {
        AtomicReference<T> newValue = UpaBot.load(filePath);
        if(newValue != null) {
            value = newValue;
        }
    }

    void save() {
        UpaBot.save(filePath, value);
    }

    public T getValue() {
        return value.get();
    }

    public String getName() {
        return name;
    }
}
