package dev.dov.tin.bridge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import lombok.experimental.UtilityClass;
import org.lwjgl.system.Platform;

@UtilityClass
class NativeLibrary {
    void load() {
        var name = System.mapLibraryName("tin_native");
        var path = "/natives/macos/" + arch() + "/" + name;
        try (var source = NativeLibrary.class.getResourceAsStream(path)) {
            if (source == null) {
                throw new IllegalStateException("Missing native library " + path);
            }
            var directory = Files.createTempDirectory("tin-native-");
            var library = directory.resolve(name);
            Files.copy(source, library, StandardCopyOption.REPLACE_EXISTING);
            directory.toFile().deleteOnExit();
            library.toFile().deleteOnExit();
            System.load(library.toString());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load native library", e);
        }
    }

    private String arch() {
        return Platform.getArchitecture() == Platform.Architecture.ARM64 ? "aarch64" : "x86_64";
    }
}
