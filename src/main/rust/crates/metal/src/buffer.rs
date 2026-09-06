use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLBuffer;

pub struct Buffer {
    pub raw: Retained<ProtocolObject<dyn MTLBuffer>>,
    pub size: u64,
    pub usage: u32,
}

impl Buffer {
    pub fn new(device: &Device, label: &str, usage: u32, size: u64) -> Self {
        todo!()
    }

    pub fn with(device: &Device, label: &str, usage: u32, data: &[u8]) -> Self {
        todo!()
    }
}
