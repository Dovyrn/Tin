use crate::buffer::Buffer;
use crate::device::Device;
use crate::fence::Fence;
use crate::memory::Memory;
use crate::pass::Pass;
use crate::queries::Queries;
use crate::texture::Texture;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2::Message;
use objc2_metal::{
    MTLBlitCommandEncoder, MTLBuffer, MTLClearColor, MTLCommandBuffer, MTLCommandEncoder, MTLCommandQueue, MTLDevice,
    MTLLoadAction, MTLOrigin, MTLRenderPassDescriptor, MTLResourceOptions, MTLSize, MTLStoreAction,
};
use std::collections::VecDeque;
use std::ptr::NonNull;

const IN_FLIGHT: usize = 2;

pub struct Encoder {
    device: Retained<ProtocolObject<dyn MTLDevice>>,
    queue: Retained<ProtocolObject<dyn MTLCommandQueue>>,
    cmd: Option<Retained<ProtocolObject<dyn MTLCommandBuffer>>>,
    submitted: VecDeque<Retained<ProtocolObject<dyn MTLCommandBuffer>>>,
    pub memory: Memory,
    pass: Option<Pass>,
}

impl Encoder {
    pub fn new(device: &Device) -> Self {
        Self {
            device: device.device.clone(),
            queue: device.queue.clone(),
            cmd: None,
            submitted: VecDeque::new(),
            memory: Memory::new(device),
            pass: None,
        }
    }

    pub fn cmd(&mut self) -> &ProtocolObject<dyn MTLCommandBuffer> {
        assert!(self.pass.is_none(), "command buffer used inside a render pass");
        self.cmd.get_or_insert_with(|| self.queue.commandBuffer().expect("command buffer"))
    }

    pub fn submit(&mut self) {
        self.memory.end_submit();
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

    pub fn begin_pass(&mut self, label: &str, views: &[i64], clears: &[f32], area: [i32; 4]) -> &mut Pass {
        let cmd = self.cmd().retain();
        self.pass = Some(Pass::new(cmd, label, views, clears, area));
        self.pass.as_mut().unwrap()
    }

    pub fn end_pass(&mut self) {
        self.pass.take().expect("no render pass").end();
    }

    pub fn clear_color(&mut self, texture: &Texture, color: [f32; 4]) {
        self.clear(Some((texture, color)), None);
    }

    pub fn clear_depth(&mut self, depth: &Texture, value: f64) {
        self.clear(None, Some((depth, value)));
    }

    pub fn clear_both(&mut self, texture: &Texture, color: [f32; 4], depth: &Texture, value: f64) {
        self.clear(Some((texture, color)), Some((depth, value)));
    }

    pub fn clear_region(
        &mut self, texture: &Texture, color: [f32; 4], depth: &Texture, value: f64, x: i32, y: i32, width: i32,
        height: i32,
    ) {
        let full = x == 0 && y == 0 && width as u32 == texture.width && height as u32 == texture.height;
        if full {
            self.clear_both(texture, color, depth, value);
        } else {
            todo!()
        }
    }

    fn clear(&mut self, color: Option<(&Texture, [f32; 4])>, depth: Option<(&Texture, f64)>) {
        let pass = MTLRenderPassDescriptor::renderPassDescriptor();
        if let Some((texture, c)) = color {
            let attachment = unsafe { pass.colorAttachments().objectAtIndexedSubscript(0) };
            attachment.setTexture(Some(&texture.raw));
            attachment.setLoadAction(MTLLoadAction::Clear);
            attachment.setStoreAction(MTLStoreAction::Store);
            attachment.setClearColor(MTLClearColor {
                red: c[0] as f64,
                green: c[1] as f64,
                blue: c[2] as f64,
                alpha: c[3] as f64,
            });
        }
        if let Some((texture, value)) = depth {
            let attachment = pass.depthAttachment();
            attachment.setTexture(Some(&texture.raw));
            attachment.setLoadAction(MTLLoadAction::Clear);
            attachment.setStoreAction(MTLStoreAction::Store);
            attachment.setClearDepth(value);
        }
        let encoder = self.cmd().renderCommandEncoderWithDescriptor(&pass).expect("render encoder");
        encoder.endEncoding();
    }

    fn staging(&self, data: &[u8]) -> Retained<ProtocolObject<dyn MTLBuffer>> {
        let bytes = NonNull::from(data).cast();
        unsafe {
            self.device.newBufferWithBytes_length_options(bytes, data.len(), MTLResourceOptions::StorageModeShared)
        }
        .expect("staging buffer")
    }

    pub fn write_buffer(&mut self, target: &Buffer, offset: u64, data: &[u8]) {
        let staging = self.staging(data);
        let blit = self.cmd().blitCommandEncoder().expect("blit encoder");
        unsafe {
            blit.copyFromBuffer_sourceOffset_toBuffer_destinationOffset_size(
                &staging,
                0,
                &target.raw,
                offset as usize,
                data.len(),
            );
        }
        blit.endEncoding();
    }

    pub fn copy_buffer(&mut self, source: &Buffer, source_offset: u64, target: &Buffer, target_offset: u64, size: u64) {
        let blit = self.cmd().blitCommandEncoder().expect("blit encoder");
        unsafe {
            blit.copyFromBuffer_sourceOffset_toBuffer_destinationOffset_size(
                &source.raw,
                source_offset as usize,
                &target.raw,
                target_offset as usize,
                size as usize,
            );
        }
        blit.endEncoding();
    }

    pub fn write_texture(
        &mut self, texture: &Texture, data: &[u8], mip: u32, layer: u32, x: u32, y: u32, width: u32, height: u32,
    ) {
        let staging = self.staging(data);
        let row = width as usize * texture.pixel as usize;
        self.copy_to_texture(&staging, 0, row, texture, mip, layer, x, y, width, height);
    }

    pub fn copy_buffer_texture(
        &mut self, buffer: &Buffer, offset: u64, source_x: u32, source_y: u32, source_width: u32, texture: &Texture,
        x: u32, y: u32, width: u32, height: u32, mip: u32, layer: u32,
    ) {
        let pixel = texture.pixel as usize;
        let row = source_width as usize * pixel;
        let start = offset as usize + source_y as usize * row + source_x as usize * pixel;
        let raw = buffer.raw.clone();
        self.copy_to_texture(&raw, start, row, texture, mip, layer, x, y, width, height);
    }

    fn copy_to_texture(
        &mut self, source: &ProtocolObject<dyn MTLBuffer>, offset: usize, row: usize, texture: &Texture, mip: u32,
        layer: u32, x: u32, y: u32, width: u32, height: u32,
    ) {
        let blit = self.cmd().blitCommandEncoder().expect("blit encoder");
        unsafe {
            blit.copyFromBuffer_sourceOffset_sourceBytesPerRow_sourceBytesPerImage_sourceSize_toTexture_destinationSlice_destinationLevel_destinationOrigin(
                source,
                offset,
                row,
                row * height as usize,
                MTLSize { width: width as usize, height: height as usize, depth: 1 },
                &texture.raw,
                layer as usize,
                mip as usize,
                MTLOrigin { x: x as usize, y: y as usize, z: 0 },
            );
        }
        blit.endEncoding();
    }

    pub fn copy_texture_buffer(
        &mut self, texture: &Texture, buffer: &Buffer, offset: u64, mip: u32, x: u32, y: u32, width: u32, height: u32,
        done: Box<dyn FnOnce() + Send>,
    ) {
        let row = width as usize * texture.pixel as usize;
        let blit = self.cmd().blitCommandEncoder().expect("blit encoder");
        unsafe {
            blit.copyFromTexture_sourceSlice_sourceLevel_sourceOrigin_sourceSize_toBuffer_destinationOffset_destinationBytesPerRow_destinationBytesPerImage(
                &texture.raw,
                0,
                mip as usize,
                MTLOrigin { x: x as usize, y: y as usize, z: 0 },
                MTLSize { width: width as usize, height: height as usize, depth: 1 },
                &buffer.raw,
                offset as usize,
                row,
                row * height as usize,
            );
        }
        blit.endEncoding();
        Fence::on_complete(self.cmd(), done);
    }

    pub fn copy_texture(
        &mut self, source: &Texture, target: &Texture, mip: u32, x: u32, y: u32, source_x: u32, source_y: u32,
        width: u32, height: u32,
    ) {
        let blit = self.cmd().blitCommandEncoder().expect("blit encoder");
        unsafe {
            blit.copyFromTexture_sourceSlice_sourceLevel_sourceOrigin_sourceSize_toTexture_destinationSlice_destinationLevel_destinationOrigin(
                &source.raw,
                0,
                mip as usize,
                MTLOrigin { x: source_x as usize, y: source_y as usize, z: 0 },
                MTLSize { width: width as usize, height: height as usize, depth: 1 },
                &target.raw,
                0,
                mip as usize,
                MTLOrigin { x: x as usize, y: y as usize, z: 0 },
            );
        }
        blit.endEncoding();
    }

    pub fn fence(&mut self) -> Fence {
        Fence::new(self.cmd())
    }

    pub fn timestamp(&mut self, queries: &Queries, index: u32) {
        queries.write(self.cmd(), index);
    }
}
