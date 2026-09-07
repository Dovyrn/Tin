use crate::device::Device;
use crate::format::{format, vertex, Format};
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_foundation::NSString;
use objc2_metal::{
    MTLBlendFactor, MTLBlendOperation, MTLColorWriteMask, MTLCompareFunction, MTLCompileOptions, MTLCullMode,
    MTLDepthStencilDescriptor, MTLDepthStencilState, MTLDevice, MTLFunction, MTLLanguageVersion, MTLLibrary,
    MTLPixelFormat, MTLPrimitiveTopologyClass, MTLPrimitiveType, MTLRenderPipelineDescriptor, MTLRenderPipelineState,
    MTLVertexDescriptor, MTLVertexStepFunction,
};
use spirv_cross2::compile::msl::{CompilerOptions, MetalPlatform, MslVersion};
use spirv_cross2::reflect::{ResourceType, TypeInner};
use spirv_cross2::spirv::{Decoration, Dim, ExecutionModel};
use spirv_cross2::{targets, Compiler, Module, SpirvCrossError};

const UNIFORM_BASE: u32 = 16;

pub struct Binding {
    pub name: String,
    pub index: u32,
    pub texel: Option<Format>,
}

pub struct Pipeline {
    pub raw: Option<Retained<ProtocolObject<dyn MTLRenderPipelineState>>>,
    pub no_depth: Option<Retained<ProtocolObject<dyn MTLRenderPipelineState>>>,
    pub depth: Retained<ProtocolObject<dyn MTLDepthStencilState>>,
    pub topology: MTLPrimitiveType,
    pub cull: MTLCullMode,
    pub bias: (f32, f32),
    pub uniforms: Vec<Binding>,
    pub textures: Vec<Binding>,
}

struct Shaders<'a> {
    inputs: &'a [String],
    texels: &'a [(String, u32)],
    outputs: Vec<String>,
    uniforms: Vec<Binding>,
    textures: Vec<Binding>,
}

impl Shaders<'_> {
    fn stage(
        &mut self, device: &Device, spirv: &[u8], model: ExecutionModel,
    ) -> Result<Retained<ProtocolObject<dyn MTLFunction>>, String> {
        let words: Vec<u32> = spirv.chunks_exact(4).map(|c| u32::from_le_bytes([c[0], c[1], c[2], c[3]])).collect();
        let mut compiler = Compiler::<targets::Msl>::new(Module::from_words(&words)).map_err(cross)?;
        let resources = compiler.shader_resources().map_err(cross)?;
        let inputs: Vec<_> = resources.resources_for_type(ResourceType::StageInput).map_err(cross)?.collect();
        let outputs: Vec<_> = resources.resources_for_type(ResourceType::StageOutput).map_err(cross)?.collect();
        let buffers: Vec<_> = resources.resources_for_type(ResourceType::UniformBuffer).map_err(cross)?.collect();
        let images: Vec<_> = resources.resources_for_type(ResourceType::SampledImage).map_err(cross)?.collect();
        let vertex = model == ExecutionModel::Vertex;
        let names: Vec<String> = if vertex {
            self.inputs.to_vec()
        } else {
            self.outputs.clone()
        };
        for input in &inputs {
            let Some(location) = names.iter().position(|n| *n == *input.name) else {
                return Err(format!("shader expects input variable which is not provided: {}", input.name));
            };
            compiler.set_decoration(input.id, Decoration::Location, Some(location as u32)).map_err(cross)?;
        }
        if vertex {
            self.outputs = outputs.iter().map(|o| o.name.to_string()).collect();
            for (i, output) in outputs.iter().enumerate() {
                compiler.set_decoration(output.id, Decoration::Location, Some(i as u32)).map_err(cross)?;
            }
        }
        for buffer in &buffers {
            let name = buffer.name.to_string();
            let i = match self.uniforms.iter().position(|u| u.name == name) {
                Some(i) => i,
                None => {
                    self.uniforms.push(Binding { index: UNIFORM_BASE + self.uniforms.len() as u32, name, texel: None });
                    self.uniforms.len() - 1
                }
            };
            compiler.set_decoration(buffer.id, Decoration::DescriptorSet, Some(0u32)).map_err(cross)?;
            compiler.set_decoration(buffer.id, Decoration::Binding, Some(self.uniforms[i].index)).map_err(cross)?;
        }
        for image in &images {
            let name = image.name.to_string();
            let dim = match compiler.type_description(image.base_type_id).map_err(cross)?.inner {
                TypeInner::Image(image) => image.dimension,
                _ => unreachable!("sampled image without image type"),
            };
            let texel = self.texels.iter().find(|(n, _)| *n == name).map(|(_, f)| format(*f));
            if texel.is_some() != (dim == Dim::DimBuffer) {
                return Err(format!("sampler {name} dimension does not match its binding"));
            }
            let i = match self.textures.iter().position(|t| t.name == name) {
                Some(i) => i,
                None => {
                    self.textures.push(Binding { index: self.textures.len() as u32, name, texel });
                    self.textures.len() - 1
                }
            };
            compiler.set_decoration(image.id, Decoration::DescriptorSet, Some(0u32)).map_err(cross)?;
            compiler.set_decoration(image.id, Decoration::Binding, Some(self.textures[i].index)).map_err(cross)?;
        }
        let mut options = CompilerOptions::default();
        options.version = MslVersion::from((3, 0));
        options.platform = MetalPlatform::MacOS;
        options.enable_decoration_binding = true;
        options.texture_buffer_native = true;
        options.common.flip_vertex_y = true;
        let compiled = compiler.compile(&options).map_err(cross)?;
        let entry = compiled.cleansed_entry_point_name("main", model).map_err(cross)?.expect("entry point");
        let entry = entry.to_string();
        let source = NSString::from_str(compiled.as_ref());
        let options = MTLCompileOptions::new();
        options.setLanguageVersion(MTLLanguageVersion::Version3_0);
        let library = device
            .device
            .newLibraryWithSource_options_error(&source, Some(&options))
            .map_err(|e| e.localizedDescription().to_string())?;
        library.newFunctionWithName(&NSString::from_str(&entry)).ok_or_else(|| format!("no function {entry}"))
    }
}

fn cross(e: SpirvCrossError) -> String {
    e.to_string()
}

impl Pipeline {
    pub fn new(
        device: &Device, location: &str, vertex: &[u8], fragment: &[u8], inputs: &[String], texels: &[(String, u32)],
        state: &[i32],
    ) -> Self {
        let has_depth = state[0] != 0;
        let depth = MTLDepthStencilDescriptor::new();
        depth.setDepthCompareFunction(if has_depth {
            compare(state[1])
        } else {
            MTLCompareFunction::Always
        });
        depth.setDepthWriteEnabled(has_depth && state[2] != 0);
        let depth = device.device.newDepthStencilStateWithDescriptor(&depth).expect("depth state");
        let bias = (f32::from_bits(state[3] as u32), f32::from_bits(state[4] as u32));
        let cull = if state[6] != 0 {
            MTLCullMode::Back
        } else {
            MTLCullMode::None
        };
        let (topology, class) = topology(state[7]);
        let mut shaders = Shaders { inputs, texels, outputs: Vec::new(), uniforms: Vec::new(), textures: Vec::new() };
        let functions = shaders
            .stage(device, vertex, ExecutionModel::Vertex)
            .and_then(|v| Ok((v, shaders.stage(device, fragment, ExecutionModel::Fragment)?)));
        let mut pipeline = Self {
            raw: None,
            no_depth: None,
            depth,
            topology,
            cull,
            bias,
            uniforms: shaders.uniforms,
            textures: shaders.textures,
        };
        let (vertex, fragment) = match functions {
            Ok(f) => f,
            Err(e) => {
                device.messages.borrow_mut().push(format!("Couldn't compile pipeline {location}: {e}"));
                return pipeline;
            }
        };
        let info = MTLRenderPipelineDescriptor::new();
        info.setLabel(Some(&NSString::from_str(location)));
        info.setVertexFunction(Some(&vertex));
        info.setFragmentFunction(Some(&fragment));
        unsafe {
            info.setInputPrimitiveTopology(class);
        }
        let mut i = 9;
        let targets = state[8] as usize;
        for t in 0..targets {
            let s = &state[i..i + 9];
            let attachment = unsafe { info.colorAttachments().objectAtIndexedSubscript(t) };
            attachment.setPixelFormat(format(s[0] as u32).raw);
            attachment.setWriteMask(mask(s[1]));
            if s[2] != 0 {
                attachment.setBlendingEnabled(true);
                attachment.setSourceRGBBlendFactor(factor(s[3]));
                attachment.setDestinationRGBBlendFactor(factor(s[4]));
                attachment.setRgbBlendOperation(op(s[5]));
                attachment.setSourceAlphaBlendFactor(factor(s[6]));
                attachment.setDestinationAlphaBlendFactor(factor(s[7]));
                attachment.setAlphaBlendOperation(op(s[8]));
            }
            i += 9;
        }
        let layout = MTLVertexDescriptor::vertexDescriptor();
        let bindings = state[i] as usize;
        i += 1;
        let mut location_index = 0;
        for b in 0..bindings {
            if state[i] == 0 {
                i += 1;
                continue;
            }
            let (rate, stride, elements) = (state[i + 1], state[i + 2], state[i + 3] as usize);
            i += 4;
            let slot = unsafe { layout.layouts().objectAtIndexedSubscript(b) };
            unsafe {
                slot.setStride(stride as usize);
            }
            if rate > 0 {
                slot.setStepFunction(MTLVertexStepFunction::PerInstance);
                unsafe {
                    slot.setStepRate(rate as usize);
                }
            } else {
                slot.setStepFunction(MTLVertexStepFunction::PerVertex);
            }
            for _ in 0..elements {
                let attribute = unsafe { layout.attributes().objectAtIndexedSubscript(location_index) };
                attribute.setFormat(self::vertex(state[i + 1] as u32));
                unsafe {
                    attribute.setOffset(state[i] as usize);
                    attribute.setBufferIndex(b);
                }
                location_index += 1;
                i += 2;
            }
        }
        info.setVertexDescriptor(Some(&layout));
        info.setDepthAttachmentPixelFormat(MTLPixelFormat::Depth32Float);
        let raw = device.device.newRenderPipelineStateWithDescriptor_error(&info);
        let no_depth = if has_depth {
            Ok(None)
        } else {
            info.setDepthAttachmentPixelFormat(MTLPixelFormat::Invalid);
            device.device.newRenderPipelineStateWithDescriptor_error(&info).map(Some)
        };
        match (raw, no_depth) {
            (Ok(raw), Ok(no_depth)) => {
                pipeline.raw = Some(raw);
                pipeline.no_depth = no_depth;
            }
            (Err(e), _) | (_, Err(e)) => {
                let e = e.localizedDescription();
                device.messages.borrow_mut().push(format!("Couldn't compile pipeline {location}: {e}"));
            }
        }
        pipeline
    }

    pub fn valid(&self) -> bool {
        self.raw.is_some()
    }
}

fn compare(ordinal: i32) -> MTLCompareFunction {
    match ordinal {
        0 => MTLCompareFunction::Always,
        1 => MTLCompareFunction::Less,
        2 => MTLCompareFunction::LessEqual,
        3 => MTLCompareFunction::Equal,
        4 => MTLCompareFunction::NotEqual,
        5 => MTLCompareFunction::GreaterEqual,
        6 => MTLCompareFunction::Greater,
        7 => MTLCompareFunction::Never,
        _ => unreachable!("compare op {ordinal}"),
    }
}

fn topology(ordinal: i32) -> (MTLPrimitiveType, MTLPrimitiveTopologyClass) {
    match ordinal {
        0 | 1 => (MTLPrimitiveType::Line, MTLPrimitiveTopologyClass::Line),
        2 => (MTLPrimitiveType::LineStrip, MTLPrimitiveTopologyClass::Line),
        3 => (MTLPrimitiveType::Point, MTLPrimitiveTopologyClass::Point),
        4 | 6 | 7 => (MTLPrimitiveType::Triangle, MTLPrimitiveTopologyClass::Triangle),
        5 => (MTLPrimitiveType::TriangleStrip, MTLPrimitiveTopologyClass::Triangle),
        _ => unreachable!("topology {ordinal}"),
    }
}

fn mask(bits: i32) -> MTLColorWriteMask {
    let mut out = MTLColorWriteMask::None;
    if bits & 1 != 0 {
        out |= MTLColorWriteMask::Red;
    }
    if bits & 2 != 0 {
        out |= MTLColorWriteMask::Green;
    }
    if bits & 4 != 0 {
        out |= MTLColorWriteMask::Blue;
    }
    if bits & 8 != 0 {
        out |= MTLColorWriteMask::Alpha;
    }
    out
}

fn factor(ordinal: i32) -> MTLBlendFactor {
    match ordinal {
        0 => MTLBlendFactor::BlendAlpha,
        1 => MTLBlendFactor::BlendColor,
        2 => MTLBlendFactor::DestinationAlpha,
        3 => MTLBlendFactor::DestinationColor,
        4 => MTLBlendFactor::One,
        5 => MTLBlendFactor::OneMinusBlendAlpha,
        6 => MTLBlendFactor::OneMinusBlendColor,
        7 => MTLBlendFactor::OneMinusDestinationAlpha,
        8 => MTLBlendFactor::OneMinusDestinationColor,
        9 => MTLBlendFactor::OneMinusSourceAlpha,
        10 => MTLBlendFactor::OneMinusSourceColor,
        11 => MTLBlendFactor::SourceAlpha,
        12 => MTLBlendFactor::SourceAlphaSaturated,
        13 => MTLBlendFactor::SourceColor,
        14 => MTLBlendFactor::Zero,
        _ => unreachable!("blend factor {ordinal}"),
    }
}

fn op(ordinal: i32) -> MTLBlendOperation {
    match ordinal {
        0 => MTLBlendOperation::Add,
        1 => MTLBlendOperation::Subtract,
        2 => MTLBlendOperation::ReverseSubtract,
        3 => MTLBlendOperation::Min,
        4 => MTLBlendOperation::Max,
        _ => unreachable!("blend op {ordinal}"),
    }
}
