use crate::device::Device;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::{
    MTLDevice, MTLSamplerAddressMode, MTLSamplerDescriptor, MTLSamplerMinMagFilter, MTLSamplerMipFilter,
    MTLSamplerState,
};

const NO_LOD: f32 = 1000.0;
const MIP_THRESHOLD: f32 = 0.25;

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

impl Address {
    fn raw(self) -> MTLSamplerAddressMode {
        match self {
            Address::Repeat => MTLSamplerAddressMode::Repeat,
            Address::Clamp => MTLSamplerAddressMode::ClampToEdge,
        }
    }
}

impl Filter {
    fn raw(self) -> MTLSamplerMinMagFilter {
        match self {
            Filter::Nearest => MTLSamplerMinMagFilter::Nearest,
            Filter::Linear => MTLSamplerMinMagFilter::Linear,
        }
    }
}

pub struct Sampler {
    pub raw: Retained<ProtocolObject<dyn MTLSamplerState>>,
}

impl Sampler {
    pub fn new(
        device: &Device, u: Address, v: Address, min: Filter, mag: Filter, anisotropy: u32, lod: Option<f32>,
    ) -> Self {
        let max = lod.unwrap_or(NO_LOD);
        let info = MTLSamplerDescriptor::new();
        info.setSAddressMode(u.raw());
        info.setTAddressMode(v.raw());
        info.setMinFilter(min.raw());
        info.setMagFilter(mag.raw());
        info.setMipFilter(if max > MIP_THRESHOLD {
            MTLSamplerMipFilter::Linear
        } else {
            MTLSamplerMipFilter::Nearest
        });
        info.setLodMaxClamp(max.max(MIP_THRESHOLD));
        info.setMaxAnisotropy(anisotropy.max(1) as usize);
        let raw = device.device.newSamplerStateWithDescriptor(&info).expect("sampler");
        Self { raw }
    }
}
