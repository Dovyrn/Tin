package dev.dov.tin.metal;

import dev.dov.tin.bridge.Native;
import com.mojang.blaze3d.GLFWErrorCapture;
import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import java.util.Locale;
import org.lwjgl.glfw.GLFW;

public class MTLBackend implements GpuBackend {
    @Override
    public String getName() {
        return "Metal";
    }

    @Override
    public void setWindowHints() {
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
    }

    @Override
    public void handleWindowCreationErrors(GLFWErrorCapture.Error error) throws BackendCreationException {
        if (error != null) {
            throw new BackendCreationException(String.format(Locale.ROOT, "GLFW_ERROR: 0x%X", error.error()),
                    BackendCreationException.Reason.GLFW_ERROR);
        }
        throw new BackendCreationException("Failed to create window for Metal",
                BackendCreationException.Reason.GLFW_ERROR);
    }

    @Override
    public GpuDevice createDevice(long window, ShaderSource shaders, GpuDebugOptions debug, Runnable loader) {
        long handle = Native.nDeviceCreate(window, debug.logLevel(), debug.synchronousLogs(), debug.useLabels(),
                debug.useValidationLayers());
        return new GpuDevice(new MTLDevice(handle, shaders), loader);
    }
}
