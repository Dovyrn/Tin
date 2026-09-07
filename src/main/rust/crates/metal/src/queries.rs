use crate::device::Device;
use crate::fence::Fence;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_foundation::NSRange;
use objc2_metal::{
    MTLCommandBuffer, MTLCommandEncoder, MTLCommonCounterSetTimestamp, MTLComputePassDescriptor,
    MTLCounterResultTimestamp, MTLCounterSampleBuffer, MTLCounterSampleBufferDescriptor, MTLCounterSet, MTLDevice,
    MTLStorageMode,
};
use std::sync::{Arc, Mutex};

pub struct Queries {
    pub size: u32,
    raw: Retained<ProtocolObject<dyn MTLCounterSampleBuffer>>,
    ready: Arc<Mutex<Vec<bool>>>,
}

impl Queries {
    pub fn new(device: &Device, size: u32) -> Self {
        let sets = device.device.counterSets().expect("counter sets");
        let timestamp = unsafe { MTLCommonCounterSetTimestamp };
        let set = sets.iter().find(|set| set.name().isEqualToString(timestamp)).expect("timestamp counters");
        let info = MTLCounterSampleBufferDescriptor::new();
        info.setCounterSet(Some(&*set));
        info.setStorageMode(MTLStorageMode::Shared);
        unsafe {
            info.setSampleCount(size as usize);
        }
        let raw = device.device.newCounterSampleBufferWithDescriptor_error(&info).expect("counter sample buffer");
        Self { size, raw, ready: Arc::new(Mutex::new(vec![false; size as usize])) }
    }

    pub fn write(&self, cmd: &ProtocolObject<dyn MTLCommandBuffer>, index: u32) {
        assert!(index < self.size, "query {index} out of range");
        let info = MTLComputePassDescriptor::computePassDescriptor();
        let attachment = unsafe { info.sampleBufferAttachments().objectAtIndexedSubscript(0) };
        attachment.setSampleBuffer(Some(&self.raw));
        unsafe {
            attachment.setStartOfEncoderSampleIndex(index as usize);
            attachment.setEndOfEncoderSampleIndex(index as usize);
        }
        cmd.computeCommandEncoderWithDescriptor(&info).expect("compute encoder").endEncoding();
        self.ready.lock().unwrap()[index as usize] = false;
        let ready = self.ready.clone();
        Fence::on_complete(cmd, Box::new(move || ready.lock().unwrap()[index as usize] = true));
    }

    pub fn values(&self, first: u32, count: u32) -> Vec<Option<u64>> {
        let ready = self.ready.lock().unwrap();
        let range = NSRange { location: first as usize, length: count as usize };
        let data = unsafe { self.raw.resolveCounterRange(range) };
        let stamps: Vec<u64> = data.map_or_else(Vec::new, |data| {
            let bytes = data.to_vec();
            bytes
                .chunks_exact(size_of::<MTLCounterResultTimestamp>())
                .map(|c| u64::from_ne_bytes(c[..8].try_into().unwrap()))
                .collect()
        });
        (0..count as usize)
            .map(|i| {
                let index = first as usize + i;
                (ready[index] && stamps.get(i).is_some_and(|&s| s != u64::MAX)).then(|| stamps[i])
            })
            .collect()
    }
}
