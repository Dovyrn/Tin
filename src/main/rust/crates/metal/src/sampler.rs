use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLSamplerState;

#[derive(Clone, Copy)]
pub enum Address {
    Repeat,
    Clamp,
}

#[derive(Clone, Copy)]
pub enum Filter {
    Nearest,
    Linear,
}

pub struct Sampler {
    pub raw: Retained<ProtocolObject<dyn MTLSamplerState>>,
}

impl Sampler {
    pub fn new(
        device: &Device, u: Address, v: Address, min: Filter, mag: Filter, anisotropy: u32, lod: Option<f32>,
    ) -> Self {
        todo!()
    }
}
