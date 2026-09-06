use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLTexture;

pub struct Texture {
    pub raw: Retained<ProtocolObject<dyn MTLTexture>>,
    pub pixel: u32,
    pub width: u32,
    pub height: u32,
}

impl Texture {
    pub fn new(
        device: &Device, label: &str, usage: u32, format: u32, width: u32, height: u32, layers: u32, mips: u32,
    ) -> Self {
        todo!()
    }
}
