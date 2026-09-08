package dev.dov.tin.metal;

import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
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
    public @NotNull GpuDevice createDevice(long window, @NotNull ShaderSource shaders, @NotNull GpuDebugOptions debug, @NotNull Runnable loader) {
        return new GpuDevice(new MetalDevice(window, shaders, debug), loader);
    }
}
