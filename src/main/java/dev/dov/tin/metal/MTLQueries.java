package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.systems.GpuQueryPool;
import java.util.OptionalLong;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MTLQueries implements GpuQueryPool {
    @Getter
    private final long handle;
    private final int size;

    @Override
    public int size() {
        return size;
    }

    @Override
    public OptionalLong getValue(int index) {
        return getValues(index, 1)[0];
    }

    @Override
    public OptionalLong[] getValues(int index, int count) {
        long[] raw = Native.nQueriesValues(handle, index, count);
        var out = new OptionalLong[count];
        for (int i = 0; i < count; i++) {
            out[i] = raw[i * 2] != 0 ? OptionalLong.of(raw[i * 2 + 1]) : OptionalLong.empty();
        }
        return out;
    }

    @Override
    public void close() {
        Native.nQueriesClose(handle);
    }
}
