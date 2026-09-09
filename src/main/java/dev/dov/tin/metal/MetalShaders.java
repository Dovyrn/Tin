package dev.dov.tin.metal;

import com.google.gson.Gson;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.lwjgl.system.MemoryUtil;

@UtilityClass
public class MetalShaders {
    private final Gson GSON = new Gson();
    private final Arena ARENA = Arena.global();
    private final SymbolLookup LIBRARY = load();
    private final MethodHandle TRANSLATE = Linker.nativeLinker().downcallHandle(
            LIBRARY.find("tin_translate").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private final MethodHandle FREE = Linker.nativeLinker().downcallHandle(LIBRARY.find("tin_free").orElseThrow(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    @SneakyThrows
    private SymbolLookup load() {
        var name = System.mapLibraryName("tin_shaders");
        var path = "/natives/" + name;
        try (var source = MetalShaders.class.getResourceAsStream(path)) {
            if (source == null) {
                throw new IllegalStateException("missing " + path);
            }
            var directory = Files.createTempDirectory("tin-");
            var library = directory.resolve(name);
            Files.copy(source, library, StandardCopyOption.REPLACE_EXISTING);
            library.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();
            return SymbolLookup.libraryLookup(library, ARENA);
        }
    }

    @SneakyThrows
    public String source(String path) {
        try (var stream = MetalShaders.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @SneakyThrows
    public Translation translate(ByteBuffer vertex, ByteBuffer fragment, Request request) {
        try (var arena = Arena.ofConfined()) {
            var vertexBytes = MemorySegment.ofAddress(MemoryUtil.memAddress(vertex)).reinterpret(vertex.remaining());
            var fragmentBytes = MemorySegment.ofAddress(MemoryUtil.memAddress(fragment))
                    .reinterpret(fragment.remaining());
            var json = arena.allocateFrom(GSON.toJson(request));
            var reply = (MemorySegment) TRANSLATE.invokeExact(vertexBytes, (long) vertex.remaining(), fragmentBytes,
                    (long) fragment.remaining(), json);
            try {
                var text = reply.reinterpret(Long.MAX_VALUE).getString(0);
                return GSON.fromJson(text, Translation.class);
            } finally {
                FREE.invokeExact(reply);
            }
        }
    }
}
