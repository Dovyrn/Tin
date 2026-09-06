use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_foundation::NSString;
use objc2_metal::{MTLBuffer, MTLDevice, MTLResource, MTLResourceOptions};
use std::ptr::NonNull;

pub struct Buffer {
    pub raw: Retained<ProtocolObject<dyn MTLBuffer>>,
    pub size: u64,
    pub usage: u32,
}

impl Buffer {
    pub fn new(device: &Device, label: &str, usage: u32, size: u64) -> Self {
        let raw = device
            .device
            .newBufferWithLength_options(size.max(1) as usize, MTLResourceOptions::StorageModeShared)
            .expect("buffer");
        raw.setLabel(Some(&NSString::from_str(label)));
        Self { raw, size, usage }
    }

    pub fn with(device: &Device, label: &str, usage: u32, data: &[u8]) -> Self {
        let bytes = NonNull::from(data).cast();
        let raw = unsafe {
            device.device.newBufferWithBytes_length_options(bytes, data.len(), MTLResourceOptions::StorageModeShared)
        }
        .expect("buffer");
        raw.setLabel(Some(&NSString::from_str(label)));
        Self { raw, size: data.len() as u64, usage }
    }

    pub fn map(&self, offset: u64) -> *mut u8 {
        assert!(offset <= self.size, "map past buffer");
        unsafe { self.raw.contents().as_ptr().cast::<u8>().add(offset as usize) }
    }
}
