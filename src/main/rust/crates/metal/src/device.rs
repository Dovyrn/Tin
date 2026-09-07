use crate::buffer::Buffer;
use crate::pipeline::Pipeline;
use crate::queries::Queries;
use crate::sampler::{Address, Filter, Sampler};
use crate::texture::Texture;
use crate::view::View;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::{MTLCommandQueue, MTLCreateSystemDefaultDevice, MTLDevice};
use std::cell::RefCell;
use std::ptr::NonNull;

const Z_ZERO_TO_ONE: i64 = 1;
const TIMESTAMP_PERIOD: f32 = 1.0;
const MAX_ANISOTROPY: i64 = 16;
const UNIFORM_ALIGN: i64 = 256;
const MAX_TEXTURE: i64 = 16384;
const MAX_ATTACHMENTS: i64 = 8;
const INTEGRATED: i64 = 1;

pub struct Device {
    pub device: Retained<ProtocolObject<dyn MTLDevice>>,
    pub queue: Retained<ProtocolObject<dyn MTLCommandQueue>>,
    pub debug: bool,
    pub messages: RefCell<Vec<String>>,
}

impl Device {
    pub fn new(debug: bool) -> Self {
        let device = MTLCreateSystemDefaultDevice().expect("no metal device");
        let queue = device.newCommandQueue().expect("command queue");
        Self { device, queue, debug, messages: RefCell::new(Vec::new()) }
    }

    pub fn sampler(
        &self, u: Address, v: Address, min: Filter, mag: Filter, anisotropy: u32, lod: Option<f32>,
    ) -> Sampler {
        Sampler::new(self, u, v, min, mag, anisotropy, lod)
    }

    pub fn texture(
        &self, label: &str, usage: u32, format: u32, width: u32, height: u32, layers: u32, mips: u32,
    ) -> Texture {
        Texture::new(self, label, usage, format, width, height, layers, mips)
    }

    pub fn view(&self, texture: &Texture, base: u32, mips: u32) -> View {
        View::new(texture, base, mips)
    }

    pub fn buffer(&self, label: &str, usage: u32, size: u64) -> Buffer {
        Buffer::new(&self.device, label, usage, size)
    }

    pub fn buffer_with(&self, label: &str, usage: u32, data: &[u8]) -> Buffer {
        Buffer::with(&self.device, label, usage, data)
    }

    pub fn messages(&self) -> Vec<String> {
        self.messages.take()
    }

    pub fn pipeline(
        &self, location: &str, vertex: &[u8], fragment: &[u8], inputs: &[String], texels: &[(String, u32)],
        state: &[i32],
    ) -> Pipeline {
        Pipeline::new(self, location, vertex, fragment, inputs, texels, state)
    }

    pub fn clear_pipelines(&self) {}

    pub fn queries(&self, size: u32) -> Queries {
        Queries::new(self, size)
    }

    pub fn now(&self) -> u64 {
        let mut cpu = 0u64;
        let mut gpu = 0u64;
        unsafe {
            self.device.sampleTimestamps_gpuTimestamp(NonNull::from(&mut cpu), NonNull::from(&mut gpu));
        }
        gpu
    }

    pub fn numbers(&self) -> [i64; 18] {
        [
            Z_ZERO_TO_ONE,
            TIMESTAMP_PERIOD.to_bits() as i64,
            MAX_ANISOTROPY,
            UNIFORM_ALIGN,
            MAX_TEXTURE,
            self.device.maxBufferLength() as i64,
            0,
            MAX_ATTACHMENTS,
            1,
            0,
            0,
            1,
            1,
            1,
            1,
            0,
            0,
            INTEGRATED,
        ]
    }

    pub fn strings(&self) -> Vec<String> {
        vec![self.device.name().to_string(), "Apple".to_string(), "Metal 3".to_string()]
    }
}
