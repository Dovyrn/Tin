package dev.dov.tin.metal;

import com.mojang.blaze3d.systems.GpuQueryPool;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class MetalQueryPool implements GpuQueryPool {
    private final MetalDevice device;
    private final int size;

    @Override
    public int size() {
        return size;
    }

    @Override
    public @NotNull OptionalLong getValue(int index) {
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong @NotNull [] getValues(int index, int count) {
        var values = new OptionalLong[count];
        for (int i = 0; i < count; i++) {
            values[i] = OptionalLong.empty();
        }
        return values;
    }

    @Override
    public void close() {
    }
}
