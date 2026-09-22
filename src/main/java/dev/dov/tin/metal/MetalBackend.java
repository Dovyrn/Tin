package dev.dov.tin.metal;

//? if >= 26.3 {
/*import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import dev.dov.metalj.device.MTLDevice;
import dev.dov.metalj.device.Metal;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.sdl.SDLVideo;

public class MetalBackend implements GpuBackend {
    @Override
    public @NotNull String getName() {
        return "Metal";
    }

    @Override
    public void loadLibrary() throws BackendCreationException {
        var device = Metal.MTLCreateSystemDefaultDevice();
        if (device.isNull() || !device.supportsFamily(MTLDevice.MTLGPUFamilyMetal3)) {
            throw new BackendCreationException("Metal 3 is required", BackendCreationException.Reason.OTHER);
        }
        device.release();
    }

    @Override
    public void unloadLibrary() {
    }

    @Override
    public long createWindow(String title, int width, int height, long flags) {
        return SDLVideo.SDL_CreateWindow(title, width, height, SDLVideo.SDL_WINDOW_METAL | flags);
    }

    @Override
    public @NotNull GpuDevice createDevice(@NotNull GpuDebugOptions debug) throws BackendCreationException {
        var device = Metal.MTLCreateSystemDefaultDevice();
        if (device.isNull() || !device.supportsFamily(MTLDevice.MTLGPUFamilyMetal3)) {
            throw new BackendCreationException("Metal 3 is required", BackendCreationException.Reason.OTHER);
        }
        return new FrontendGpuDevice(new MetalDevice(device, debug));
    }
}
*///?} else {
import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import dev.dov.metalj.device.MTLDevice;
import dev.dov.metalj.device.Metal;
import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

public class MetalBackend implements GpuBackend {
    @Override
    public @NotNull String getName() {
        return "Metal";
    }

    @Override
    public void setWindowHints() {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
    }

    @Override
    public void handleWindowCreationErrors(GLFWErrorCapture.@NotNull Error error) throws BackendCreationException {
        throw new BackendCreationException(String.format(Locale.ROOT, "GLFW_ERROR: 0x%X", error.error()),
                BackendCreationException.Reason.GLFW_ERROR);
    }

    @Override
    public @NotNull GpuDevice createDevice(long window, @NotNull ShaderSource shaders,
            @NotNull GpuDebugOptions debug, @NotNull Runnable loader) throws BackendCreationException {
        var device = Metal.MTLCreateSystemDefaultDevice();
        if (device.isNull() || !device.supportsFamily(MTLDevice.MTLGPUFamilyMetal3)) {
            throw new BackendCreationException("Metal 3 is required", BackendCreationException.Reason.OTHER);
        }
        return new GpuDevice(new MetalDevice(device, shaders, debug), loader);
    }
}
//?}
