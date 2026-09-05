use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::{MTLCommandQueue, MTLCreateSystemDefaultDevice, MTLDevice};

pub struct Device {
    pub device: Retained<ProtocolObject<dyn MTLDevice>>,
    pub queue: Retained<ProtocolObject<dyn MTLCommandQueue>>,
}

impl Device {
    pub fn new() -> Self {
        let device = MTLCreateSystemDefaultDevice().expect("no metal device");
        let queue = device.newCommandQueue().expect("command queue");
        Self { device, queue }
    }

    pub fn name(&self) -> String {
        self.device.name().to_string()
    }
}

impl Default for Device {
    fn default() -> Self {
        Self::new()
    }
}
