# Tin

Metal backend for Minecraft on macOS. This allows code to reach the GPU directly through
the native graphics API, rather than going through MoltenVK, allowing better performance
and GPU features that aren't otherwise available.

# Goal

Ensuring good compatibility across mod that use blaze3D for rendering.
And delivering a working backend for Metal devs.

# Results

Measured on a M4 Macbook Air on 2940x1790. Up to 2.9x faster on 8 render distance
compared to vanilla vulkan backend.

# Requirements

- macOS with a GPU that supports Metal 3 (Apple Silicon, or a 2019 or newer Intel Mac)
- Java 25
- Fabric loader version 0.18.6 or newer