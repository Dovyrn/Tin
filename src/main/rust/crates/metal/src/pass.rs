use crate::buffer::Buffer;
use crate::pipeline::Pipeline;
use crate::queries::Queries;
use crate::sampler::Sampler;
use crate::view::View;
use hashbrown::HashMap;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_foundation::NSString;
use objc2_metal::{
    MTLClearColor, MTLCommandBuffer, MTLCommandEncoder, MTLIndexType, MTLLoadAction, MTLRenderCommandEncoder,
    MTLRenderPassDescriptor, MTLScissorRect, MTLStoreAction, MTLTexture, MTLViewport, MTLWinding,
};

const INDIRECT_STRIDE: usize = 20;
const INDIRECT_PLAIN_STRIDE: usize = 16;

pub struct Color<'a> {
    pub view: Option<&'a View>,
    pub clear: Option<[f32; 4]>,
}

pub struct Depth<'a> {
    pub view: &'a View,
    pub clear: Option<f64>,
}

#[derive(Clone, Copy)]
pub enum Index {
    Short,
    Int,
}

impl Index {
    fn raw(self) -> MTLIndexType {
        match self {
            Index::Short => MTLIndexType::UInt16,
            Index::Int => MTLIndexType::UInt32,
        }
    }

    fn bytes(self) -> usize {
        match self {
            Index::Short => 2,
            Index::Int => 4,
        }
    }
}

struct Uniform {
    buffer: *const Buffer,
    offset: u64,
}

pub struct Pass {
    raw: Retained<ProtocolObject<dyn MTLRenderCommandEncoder>>,
    width: u32,
    height: u32,
    area: [i32; 4],
    pipeline: Option<*const Pipeline>,
    uniforms: HashMap<String, Uniform>,
    textures: HashMap<String, (*const View, *const Sampler)>,
    index: Option<(*const Buffer, Index)>,
    dirty: bool,
}

impl Pass {
    pub fn new(
        cmd: &ProtocolObject<dyn MTLCommandBuffer>, label: &str, colors: &[Color], depth: Option<Depth>, area: [i32; 4],
    ) -> Self {
        let info = MTLRenderPassDescriptor::renderPassDescriptor();
        let mut size = (0, 0);
        for (i, color) in colors.iter().enumerate() {
            let Some(view) = color.view else {
                continue;
            };
            size = (view.raw.width() as u32, view.raw.height() as u32);
            let attachment = unsafe { info.colorAttachments().objectAtIndexedSubscript(i) };
            attachment.setTexture(Some(&view.raw));
            attachment.setStoreAction(MTLStoreAction::Store);
            match color.clear {
                Some(c) => {
                    attachment.setLoadAction(MTLLoadAction::Clear);
                    attachment.setClearColor(MTLClearColor {
                        red: c[0] as f64,
                        green: c[1] as f64,
                        blue: c[2] as f64,
                        alpha: c[3] as f64,
                    });
                }
                None => attachment.setLoadAction(MTLLoadAction::Load),
            }
        }
        if let Some(depth) = &depth {
            size = (depth.view.raw.width() as u32, depth.view.raw.height() as u32);
            let attachment = info.depthAttachment();
            attachment.setTexture(Some(&depth.view.raw));
            attachment.setStoreAction(MTLStoreAction::Store);
            match depth.clear {
                Some(value) => {
                    attachment.setLoadAction(MTLLoadAction::Clear);
                    attachment.setClearDepth(value);
                }
                None => attachment.setLoadAction(MTLLoadAction::Load),
            }
        }
        let raw = cmd.renderCommandEncoderWithDescriptor(&info).expect("render encoder");
        raw.setLabel(Some(&NSString::from_str(label)));
        raw.setViewport(MTLViewport {
            originX: 0.0,
            originY: 0.0,
            width: size.0 as f64,
            height: size.1 as f64,
            znear: 0.0,
            zfar: 1.0,
        });
        raw.setFrontFacingWinding(MTLWinding::CounterClockwise);
        let pass = Self {
            raw,
            width: size.0,
            height: size.1,
            area,
            pipeline: None,
            uniforms: HashMap::new(),
            textures: HashMap::new(),
            index: None,
            dirty: true,
        };
        pass.no_scissor();
        pass
    }

    pub fn end(self) {
        self.raw.endEncoding();
    }

    pub fn push(&self, label: &str) {
        self.raw.pushDebugGroup(&NSString::from_str(label));
    }

    pub fn pop(&self) {
        self.raw.popDebugGroup();
    }

    pub fn set_pipeline(&mut self, pipeline: &Pipeline) {
        self.raw.setRenderPipelineState(&pipeline.raw);
        self.raw.setDepthStencilState(Some(&pipeline.depth));
        self.raw.setCullMode(pipeline.cull);
        self.raw.setDepthBias_slopeScale_clamp(pipeline.bias.1, pipeline.bias.0, 0.0);
        self.pipeline = Some(pipeline);
        self.dirty = true;
    }

    pub fn set_texture(&mut self, name: &str, view: Option<&View>, sampler: Option<&Sampler>) {
        match (view, sampler) {
            (Some(view), Some(sampler)) => {
                self.textures.insert(name.to_string(), (view, sampler));
            }
            (None, None) => {
                self.textures.remove(name);
            }
            _ => unreachable!("texture and sampler must be bound together"),
        }
        self.dirty = true;
    }

    pub fn set_uniform(&mut self, name: &str, buffer: &Buffer, offset: u64) {
        self.uniforms.insert(name.to_string(), Uniform { buffer, offset });
        self.dirty = true;
    }

    pub fn scissor(&self, x: i32, y: i32, width: i32, height: i32) {
        let x = x.max(0) as usize;
        let y = y.max(0) as usize;
        let width = (width.max(0) as usize).min(self.width as usize - x.min(self.width as usize));
        let height = (height.max(0) as usize).min(self.height as usize - y.min(self.height as usize));
        self.raw.setScissorRect(MTLScissorRect { x, y, width, height });
    }

    pub fn no_scissor(&self) {
        if self.area[2] > 0 && self.area[3] > 0 {
            self.scissor(self.area[0], self.area[1], self.area[2], self.area[3]);
        } else {
            self.scissor(0, 0, self.width as i32, self.height as i32);
        }
    }

    pub fn set_vertex(&self, slot: u32, buffer: Option<&Buffer>, offset: u64) {
        unsafe {
            self.raw.setVertexBuffer_offset_atIndex(buffer.map(|b| &*b.raw), offset as usize, slot as usize);
        }
    }

    pub fn set_index(&mut self, buffer: &Buffer, index: Index) {
        self.index = Some((buffer, index));
    }

    fn pipeline(&self) -> &Pipeline {
        unsafe { &*self.pipeline.expect("no pipeline set") }
    }

    fn bind(&mut self) {
        if !self.dirty {
            return;
        }
        let pipeline = self.pipeline();
        for uniform in &pipeline.uniforms {
            let value = self.uniforms.get(&uniform.name).unwrap_or_else(|| panic!("missing uniform {}", uniform.name));
            let buffer = unsafe { &*value.buffer };
            let index = uniform.index as usize;
            unsafe {
                self.raw.setVertexBuffer_offset_atIndex(Some(&buffer.raw), value.offset as usize, index);
                self.raw.setFragmentBuffer_offset_atIndex(Some(&buffer.raw), value.offset as usize, index);
            }
        }
        for texture in &pipeline.textures {
            let (view, sampler) =
                self.textures.get(&texture.name).unwrap_or_else(|| panic!("missing sampler {}", texture.name));
            let (view, sampler) = unsafe { (&**view, &**sampler) };
            let index = texture.index as usize;
            unsafe {
                self.raw.setVertexTexture_atIndex(Some(&view.raw), index);
                self.raw.setVertexSamplerState_atIndex(Some(&sampler.raw), index);
                self.raw.setFragmentTexture_atIndex(Some(&view.raw), index);
                self.raw.setFragmentSamplerState_atIndex(Some(&sampler.raw), index);
            }
        }
        self.dirty = false;
    }

    fn index(&self) -> (&Buffer, Index) {
        let (buffer, index) = self.index.expect("no index buffer");
        (unsafe { &*buffer }, index)
    }

    pub fn draw(&mut self, vertices: u32, instances: u32, first: u32, first_instance: u32) {
        self.bind();
        let topology = self.pipeline().topology;
        unsafe {
            self.raw.drawPrimitives_vertexStart_vertexCount_instanceCount_baseInstance(
                topology,
                first as usize,
                vertices as usize,
                instances as usize,
                first_instance as usize,
            );
        }
    }

    pub fn draw_indexed(&mut self, indices: u32, instances: u32, first: u32, vertex_offset: i32, first_instance: u32) {
        self.bind();
        let topology = self.pipeline().topology;
        let (buffer, index) = self.index();
        unsafe {
            self.raw.drawIndexedPrimitives_indexCount_indexType_indexBuffer_indexBufferOffset_instanceCount_baseVertex_baseInstance(
                topology,
                indices as usize,
                index.raw(),
                &buffer.raw,
                first as usize * index.bytes(),
                instances as usize,
                vertex_offset as isize,
                first_instance as usize,
            );
        }
    }

    pub fn multi_draw(&mut self, params: &[i32], instances: u32, first_instance: u32) {
        for draw in params.chunks_exact(2) {
            self.draw(draw[1] as u32, instances, draw[0] as u32, first_instance);
        }
    }

    pub fn multi_draw_separate(&mut self, firsts: &[i32], counts: &[i32]) {
        for (first, count) in firsts.iter().zip(counts) {
            self.draw(*count as u32, 1, *first as u32, 0);
        }
    }

    pub fn multi_draw_indexed(&mut self, params: &[i32], instances: u32, first_instance: u32) {
        for draw in params.chunks_exact(3) {
            self.draw_indexed(draw[1] as u32, instances, draw[0] as u32, draw[2], first_instance);
        }
    }

    pub fn multi_draw_indexed_separate(&mut self, offsets: &[u64], counts: &[i32], vertex_offsets: &[i32]) {
        let bytes = self.index().1.bytes() as u64;
        for i in 0..counts.len() {
            self.draw_indexed(counts[i] as u32, 1, (offsets[i] / bytes) as u32, vertex_offsets[i], 0);
        }
    }

    pub fn draw_indirect(&mut self, commands: &Buffer, offset: u64, count: u32) {
        self.bind();
        let topology = self.pipeline().topology;
        for i in 0..count as usize {
            unsafe {
                self.raw.drawPrimitives_indirectBuffer_indirectBufferOffset(
                    topology,
                    &commands.raw,
                    offset as usize + i * INDIRECT_PLAIN_STRIDE,
                );
            }
        }
    }

    pub fn draw_indexed_indirect(&mut self, commands: &Buffer, offset: u64, count: u32) {
        self.bind();
        let topology = self.pipeline().topology;
        let (buffer, index) = self.index();
        for i in 0..count as usize {
            unsafe {
                self.raw
                    .drawIndexedPrimitives_indexType_indexBuffer_indexBufferOffset_indirectBuffer_indirectBufferOffset(
                        topology,
                        index.raw(),
                        &buffer.raw,
                        0,
                        &commands.raw,
                        offset as usize + i * INDIRECT_STRIDE,
                    );
            }
        }
    }

    pub fn draw_one(
        &mut self, slot: u32, vertex: &Buffer, index: &Buffer, kind: Index, first: u32, count: u32, base: i32,
    ) {
        self.set_index(index, kind);
        self.set_vertex(slot, Some(vertex), 0);
        self.draw_indexed(count, 1, first, base, 0);
    }

    pub fn timestamp(&self, queries: &Queries, index: u32) {
        queries.write_in_pass(&self.raw, index);
    }
}
