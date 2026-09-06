use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::{MTLCullMode, MTLDepthStencilState, MTLPrimitiveType, MTLRenderPipelineState};

pub struct Binding {
    pub name: String,
    pub index: u32,
}

pub struct Pipeline {
    pub raw: Retained<ProtocolObject<dyn MTLRenderPipelineState>>,
    pub depth: Retained<ProtocolObject<dyn MTLDepthStencilState>>,
    pub topology: MTLPrimitiveType,
    pub cull: MTLCullMode,
    pub bias: (f32, f32),
    pub uniforms: Vec<Binding>,
    pub textures: Vec<Binding>,
}

impl Pipeline {
    pub fn new(device: &Device, location: &str, vertex: &str, fragment: &str, defines: &str, state: &[i32]) -> Self {
        todo!()
    }
}
