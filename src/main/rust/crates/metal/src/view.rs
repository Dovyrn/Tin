use crate::texture::Texture;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_foundation::NSRange;
use objc2_metal::MTLTexture;

pub struct View {
    pub raw: Retained<ProtocolObject<dyn MTLTexture>>,
}

impl View {
    pub fn new(texture: &Texture, base: u32, mips: u32) -> Self {
        let raw = &texture.raw;
        let levels = NSRange { location: base as usize, length: mips as usize };
        let slices = NSRange { location: 0, length: raw.arrayLength() };
        let view = unsafe {
            raw.newTextureViewWithPixelFormat_textureType_levels_slices(
                raw.pixelFormat(),
                raw.textureType(),
                levels,
                slices,
            )
        }
        .expect("texture view");
        Self { raw: view }
    }
}
