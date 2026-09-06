use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLRenderPipelineState;

pub struct Pipeline {
    pub raw: Retained<ProtocolObject<dyn MTLRenderPipelineState>>,
}

impl Pipeline {
    pub fn new(device: &Device, location: &str, vertex: &str, fragment: &str, defines: &str, state: &[i32]) -> Self {
        todo!()
    }
}
