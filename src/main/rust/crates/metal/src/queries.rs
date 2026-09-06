use crate::device::Device;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLCommandBuffer;

pub struct Queries {
    pub size: u32,
}

impl Queries {
    pub fn new(device: &Device, size: u32) -> Self {
        todo!()
    }

    pub fn write(&self, cmd: &ProtocolObject<dyn MTLCommandBuffer>, index: u32) {
        todo!()
    }
}
