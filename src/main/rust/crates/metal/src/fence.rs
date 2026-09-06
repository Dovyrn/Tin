use objc2::runtime::ProtocolObject;
use objc2_metal::MTLCommandBuffer;

pub struct Fence {}

impl Fence {
    pub fn new(cmd: &ProtocolObject<dyn MTLCommandBuffer>) -> Self {
        todo!()
    }

    pub fn on_complete(cmd: &ProtocolObject<dyn MTLCommandBuffer>, done: Box<dyn FnOnce() + Send>) {
        todo!()
    }

    pub fn wait(&self, timeout_ns: u64) -> bool {
        todo!()
    }
}
