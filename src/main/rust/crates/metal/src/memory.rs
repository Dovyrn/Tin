use crate::device::Device;

pub struct Memory {}

impl Memory {
    pub fn new(device: &Device) -> Self {
        Self {}
    }

    pub fn end_submit(&mut self) {}
}
