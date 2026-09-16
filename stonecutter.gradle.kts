plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

stonecutter active "26.2"

stonecutter parameters {
    replacements {
        string(current.parsed >= "26.3") {
        replace("com.mojang.blaze3d.vulkan.glsl.ShaderCompileException", "com.mojang.renderpearl.util.ShaderCompileException")
        replace("com.mojang.blaze3d.systems.BackendCreationException", "com.mojang.renderpearl.api.device.BackendCreationException")
        replace("com.mojang.blaze3d.pipeline.CompiledRenderPipeline", "com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline")
        replace("com.mojang.blaze3d.systems.CommandEncoderBackend", "com.mojang.renderpearl.backend.api.CommandEncoderBackend")
        replace("com.mojang.blaze3d.systems.RenderPassDescriptor", "com.mojang.renderpearl.api.commands.RenderPassDescriptor")
        replace("com.mojang.blaze3d.systems.HintsAndWorkarounds", "com.mojang.renderpearl.api.device.HintsAndWorkarounds")
        replace("com.mojang.blaze3d.systems.RenderPassBackend", "com.mojang.renderpearl.backend.api.RenderPassBackend")
        replace("com.mojang.blaze3d.systems.GpuSurfaceBackend", "com.mojang.renderpearl.backend.api.GpuSurfaceBackend")
        replace("com.mojang.blaze3d.vulkan.glsl.GlslCompiler", "com.mojang.renderpearl.frontend.shaders.GlslCompiler")
        replace("com.mojang.blaze3d.systems.SurfaceException", "com.mojang.renderpearl.api.device.SurfaceException")
        replace("com.mojang.blaze3d.systems.GpuDeviceBackend", "com.mojang.renderpearl.backend.api.GpuDeviceBackend")
        replace("com.mojang.blaze3d.pipeline.BindGroupLayout", "com.mojang.renderpearl.api.pipeline.BindGroupLayout")
        replace("com.mojang.blaze3d.textures.GpuTextureView", "com.mojang.renderpearl.api.textures.GpuTextureView")
        replace("com.mojang.blaze3d.systems.TransientMemory", "com.mojang.renderpearl.api.buffers.TransientMemory")
        replace("com.mojang.blaze3d.shaders.GpuDebugOptions", "com.mojang.renderpearl.api.device.GpuDebugOptions")
        replace("com.mojang.blaze3d.pipeline.RenderPipeline", "com.mojang.renderpearl.api.pipeline.RenderPipeline")
        replace("com.mojang.blaze3d.GpuOutOfMemoryException", "com.mojang.renderpearl.api.device.GpuOutOfMemoryException")
        replace("com.mojang.blaze3d.systems.DeviceFeatures", "com.mojang.renderpearl.api.device.DeviceFeatures")
        replace("com.mojang.blaze3d.buffers.GpuBufferSlice", "com.mojang.renderpearl.api.buffers.GpuBufferSlice")
        replace("com.mojang.blaze3d.textures.AddressMode", "com.mojang.renderpearl.api.textures.AddressMode")
        replace("com.mojang.blaze3d.systems.GpuQueryPool", "com.mojang.renderpearl.api.commands.GpuQueryPool")
        replace("com.mojang.blaze3d.systems.DeviceLimits", "com.mojang.renderpearl.api.device.DeviceLimits")
        replace("com.mojang.blaze3d.shaders.ShaderSource", "com.mojang.renderpearl.api.pipeline.ShaderSource")
        replace("com.mojang.blaze3d.platform.PolygonMode", "com.mojang.renderpearl.api.pipeline.PolygonMode")
        replace("com.mojang.blaze3d.platform.BlendFactor", "com.mojang.renderpearl.api.pipeline.BlendFactor")
        replace("com.mojang.blaze3d.textures.GpuTexture", "com.mojang.renderpearl.api.textures.GpuTexture")
        replace("com.mojang.blaze3d.textures.GpuSampler", "com.mojang.renderpearl.api.textures.GpuSampler")
        replace("com.mojang.blaze3d.textures.FilterMode", "com.mojang.renderpearl.api.textures.FilterMode")
        replace("com.mojang.blaze3d.shaders.UniformType", "com.mojang.renderpearl.api.pipeline.UniformType")
        replace("com.mojang.blaze3d.systems.RenderPass", "com.mojang.renderpearl.api.commands.RenderPass")
        replace("com.mojang.blaze3d.systems.GpuSurface", "com.mojang.renderpearl.api.device.GpuSurface")
        replace("com.mojang.blaze3d.systems.GpuBackend", "com.mojang.renderpearl.api.device.GpuBackend")
        replace("com.mojang.blaze3d.systems.DeviceType", "com.mojang.renderpearl.api.device.DeviceType")
        replace("com.mojang.blaze3d.systems.DeviceInfo", "com.mojang.renderpearl.api.device.DeviceInfo")
        replace("com.mojang.blaze3d.shaders.ShaderType", "com.mojang.renderpearl.api.pipeline.ShaderType")
        replace("com.mojang.blaze3d.platform.CompareOp", "com.mojang.renderpearl.api.pipeline.CompareOp")
        replace("com.mojang.blaze3d.systems.GpuDevice", "com.mojang.renderpearl.api.device.GpuDevice")
        replace("com.mojang.blaze3d.buffers.GpuBuffer", "com.mojang.renderpearl.api.buffers.GpuBuffer")
        replace("com.mojang.blaze3d.PrimitiveTopology", "com.mojang.renderpearl.api.pipeline.PrimitiveTopology")
        replace("com.mojang.blaze3d.platform.BlendOp", "com.mojang.renderpearl.api.pipeline.BlendOp")
        replace("com.mojang.blaze3d.buffers.GpuFence", "com.mojang.renderpearl.api.commands.GpuFence")
        replace("com.mojang.blaze3d.IndexType", "com.mojang.renderpearl.api.pipeline.IndexType")
        replace("com.mojang.blaze3d.GpuFormat", "com.mojang.renderpearl.api.GpuFormat")
        }
    }
}

val nativeSrcDir = file("src/main/rust")
val nativeOutDir = layout.buildDirectory.dir("generated/native")

tasks.register<Exec>("buildNative") {
    workingDir = nativeSrcDir
    commandLine("cargo", "build", "--release")
    inputs.dir(File(nativeSrcDir, "src"))
    inputs.files(File(nativeSrcDir, "Cargo.toml"))
    outputs.file(File(nativeSrcDir, "target/release/libtin_shaders.dylib"))
}

tasks.register<Sync>("stageNative") {
    dependsOn("buildNative")
    into(nativeOutDir.map { File(it.asFile, "natives") })
    from(File(nativeSrcDir, "target/release/libtin_shaders.dylib"))
}
