use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLCommandBuffer;

pub struct Pass {}

impl Pass {
    pub fn new(
        cmd: Retained<ProtocolObject<dyn MTLCommandBuffer>>, label: &str, views: &[i64], clears: &[f32], area: [i32; 4],
    ) -> Self {
        todo!()
    }

    pub fn end(self) {
        todo!()
    }
}
