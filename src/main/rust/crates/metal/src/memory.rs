use crate::buffer::Buffer;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_metal::MTLDevice;
use std::collections::VecDeque;

const BLOCK: u64 = 512 * 1024;
const IN_FLIGHT: usize = 2;
const CPU_ALIGN: usize = 16;

pub struct Slice {
    pub buffer: *const Buffer,
    pub offset: u64,
    pub size: u64,
    pub address: *mut u8,
}

#[allow(clippy::vec_box)]
struct Ring {
    free: Vec<Box<Buffer>>,
    used: Vec<Box<Buffer>>,
    retired: VecDeque<Vec<Box<Buffer>>>,
    offset: u64,
}

pub struct Memory {
    device: Retained<ProtocolObject<dyn MTLDevice>>,
    cpu: Vec<Vec<u8>>,
    cpu_retired: VecDeque<Vec<Vec<u8>>>,
    cpu_offset: usize,
    gpu: Ring,
}

fn round(value: u64, align: u64) -> u64 {
    value.div_ceil(align) * align
}

impl Ring {
    fn block(&mut self, device: &ProtocolObject<dyn MTLDevice>, size: u64) -> &Buffer {
        let block = match self.free.iter().position(|b| b.size >= size) {
            Some(i) => self.free.swap_remove(i),
            None => Box::new(Buffer::new(device, "transient block", 0, size.max(BLOCK))),
        };
        self.used.push(block);
        self.offset = 0;
        self.used.last().unwrap()
    }

    fn allocate(
        &mut self, device: &ProtocolObject<dyn MTLDevice>, size: u64, align: u64, minimum: u64, element: u64,
    ) -> Slice {
        if size > BLOCK {
            self.block(device, size);
            return self.take(size);
        }
        let start = round(self.offset, align);
        let room = self.used.last().map_or(0, |b| b.size.saturating_sub(start));
        if room >= size {
            self.offset = start;
            return self.take(size);
        }
        if room >= minimum {
            self.offset = start;
            return self.take(room / element * element);
        }
        self.block(device, BLOCK);
        self.take(size)
    }

    fn take(&mut self, size: u64) -> Slice {
        let block = self.used.last().unwrap();
        let offset = self.offset;
        self.offset = offset + size;
        Slice { buffer: &**block, offset, size, address: block.map(offset) }
    }

    fn rotate(&mut self) {
        self.retired.push_back(std::mem::take(&mut self.used));
        self.offset = BLOCK;
        while self.retired.len() > IN_FLIGHT {
            self.free.extend(self.retired.pop_front().unwrap());
        }
    }
}

impl Memory {
    pub fn new(device: Retained<ProtocolObject<dyn MTLDevice>>) -> Self {
        let gpu = Ring { free: Vec::new(), used: Vec::new(), retired: VecDeque::new(), offset: BLOCK };
        Self { device, cpu: Vec::new(), cpu_retired: VecDeque::new(), cpu_offset: BLOCK as usize, gpu }
    }

    pub fn cpu(&mut self, size: u64, align: u64, minimum: u64, element: u64) -> *mut u8 {
        let size = size as usize;
        let start = round(self.cpu_offset as u64, align.max(1)) as usize;
        let room = self.cpu.last().map_or(0, |b| b.len().saturating_sub(start));
        if room < size {
            self.cpu.push(vec![0; size.max(BLOCK as usize)]);
            self.cpu_offset = 0;
        } else {
            self.cpu_offset = start;
        }
        let block = self.cpu.last_mut().unwrap();
        let out = unsafe { block.as_mut_ptr().add(self.cpu_offset) };
        self.cpu_offset += size.max(CPU_ALIGN);
        out
    }

    pub fn gpu(&mut self, size: u64, align: u64, minimum: u64, element: u64) -> Slice {
        self.gpu.allocate(&self.device, size, align.max(1), minimum, element.max(1))
    }

    pub fn upload(&mut self, parts: &[&[u8]], align: u64, minimum: u64, element: u64) -> Slice {
        let align = align.max(1);
        let total = parts.iter().fold(0, |at, part| round(at + part.len() as u64, align));
        let slice = self.gpu(total, align, minimum, element);
        let mut at = 0;
        for part in parts {
            if at >= slice.size {
                break;
            }
            let len = part.len().min((slice.size - at) as usize);
            unsafe {
                std::ptr::copy_nonoverlapping(part.as_ptr(), slice.address.add(at as usize), len);
            }
            at = round(at + part.len() as u64, align);
        }
        slice
    }

    pub fn multi_upload(&mut self, parts: &[&[u8]], align: u64) -> Vec<Slice> {
        parts.iter().map(|part| self.upload(&[part], align, part.len() as u64, 1)).collect()
    }

    pub fn end_submit(&mut self) {
        self.cpu_retired.push_back(std::mem::take(&mut self.cpu));
        self.cpu_offset = BLOCK as usize;
        while self.cpu_retired.len() > IN_FLIGHT {
            self.cpu_retired.pop_front();
        }
        self.gpu.rotate();
    }
}
