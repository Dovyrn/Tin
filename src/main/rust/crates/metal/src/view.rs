use crate::texture::Texture;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLTexture;

pub struct View {
    pub raw: Retained<ProtocolObject<dyn MTLTexture>>,
}

impl View {
    pub fn new(texture: &Texture, base: u32, mips: u32) -> Self {
        todo!()
    }
}
