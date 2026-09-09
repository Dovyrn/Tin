package dev.dov.tin.metal;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

@UtilityClass
public class AutoreleasePool {
    private final SymbolLookup OBJC = SymbolLookup.libraryLookup("/usr/lib/libobjc.A.dylib", Arena.global());
    private final MethodHandle PUSH = Linker.nativeLinker().downcallHandle(
            OBJC.find("objc_autoreleasePoolPush").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS));
    private final MethodHandle POP = Linker.nativeLinker().downcallHandle(
            OBJC.find("objc_autoreleasePoolPop").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    @SneakyThrows
    public <T> T get(Supplier<T> body) {
        var pool = (MemorySegment) PUSH.invokeExact();
        try {
            return body.get();
        } finally {
            POP.invokeExact(pool);
        }
    }

    public void run(Runnable body) {
        get(() -> {
            body.run();
            return null;
        });
    }
}
