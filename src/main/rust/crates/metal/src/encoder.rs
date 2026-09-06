use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::{MTLCommandBuffer, MTLCommandQueue};
use std::collections::VecDeque;

const IN_FLIGHT: usize = 2;

pub struct Encoder {
    queue: Retained<ProtocolObject<dyn MTLCommandQueue>>,
    cmd: Option<Retained<ProtocolObject<dyn MTLCommandBuffer>>>,
    submitted: VecDeque<Retained<ProtocolObject<dyn MTLCommandBuffer>>>,
}

impl Encoder {
    pub fn new(queue: Retained<ProtocolObject<dyn MTLCommandQueue>>) -> Self {
        Self { queue, cmd: None, submitted: VecDeque::new() }
    }

    pub fn cmd(&mut self) -> &ProtocolObject<dyn MTLCommandBuffer> {
        self.cmd.get_or_insert_with(|| self.queue.commandBuffer().expect("command buffer"))
    }

    pub fn submit(&mut self) {
        if let Some(cmd) = self.cmd.take() {
            cmd.commit();
            self.submitted.push_back(cmd);
        }
        while self.submitted.len() > IN_FLIGHT {
            self.submitted.pop_front().unwrap().waitUntilCompleted();
        }
    }

    pub fn wait(&mut self) {
        self.submit();
        for cmd in self.submitted.drain(..) {
            cmd.waitUntilCompleted();
        }
    }
}
