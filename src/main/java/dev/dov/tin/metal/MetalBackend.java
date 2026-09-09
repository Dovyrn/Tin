package dev.dov.tin.metal;

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
        return new GpuDevice(new MetalDevice(device, window, shaders, debug), loader);
    }
}
