use serde::{Deserialize, Serialize};
use spirv_cross2::compile::msl::{CompilerOptions, MetalPlatform, MslVersion};
use spirv_cross2::reflect::{ResourceType, TypeInner};
use spirv_cross2::spirv::{Decoration, Dim, ExecutionModel};
use spirv_cross2::{targets, Compiler, Module};
use std::ffi::{c_char, CStr, CString};
use std::slice;

const UNIFORM_BASE: u32 = 16;

#[derive(Deserialize)]
struct Request {
    inputs: Vec<String>,
    texels: Vec<Texel>,
}

#[derive(Deserialize, Clone)]
struct Texel {
    name: String,
    buffer: bool,
}

#[derive(Serialize, Default)]
struct Reply {
    error: Option<String>,
    vertex: String,
    fragment: String,
    vertex_entry: String,
    fragment_entry: String,
    uniforms: Vec<Binding>,
    textures: Vec<Binding>,
}

#[derive(Serialize, Clone)]
struct Binding {
    name: String,
    index: u32,
    texel: bool,
}

struct Stages {
    inputs: Vec<String>,
    texels: Vec<Texel>,
    outputs: Vec<String>,
    uniforms: Vec<Binding>,
    textures: Vec<Binding>,
}

impl Stages {
    fn stage(&mut self, spirv: &[u8], model: ExecutionModel) -> Result<(String, String), String> {
        let words: Vec<u32> = spirv.chunks_exact(4).map(|c| u32::from_le_bytes([c[0], c[1], c[2], c[3]])).collect();
        let mut compiler = Compiler::<targets::Msl>::new(Module::from_words(&words)).map_err(text)?;
        let resources = compiler.shader_resources().map_err(text)?;
        let inputs: Vec<_> = resources.resources_for_type(ResourceType::StageInput).map_err(text)?.collect();
        let outputs: Vec<_> = resources.resources_for_type(ResourceType::StageOutput).map_err(text)?.collect();
        let buffers: Vec<_> = resources.resources_for_type(ResourceType::UniformBuffer).map_err(text)?.collect();
        let images: Vec<_> = resources.resources_for_type(ResourceType::SampledImage).map_err(text)?.collect();
        let vertex = model == ExecutionModel::Vertex;
        let names: Vec<String> = if vertex { self.inputs.clone() } else { self.outputs.clone() };
        for input in &inputs {
            let Some(location) = names.iter().position(|n| *n == *input.name) else {
                return Err(format!("shader expects input variable which is not provided: {}", input.name));
            };
            compiler.set_decoration(input.id, Decoration::Location, Some(location as u32)).map_err(text)?;
        }
        if vertex {
            self.outputs = outputs.iter().map(|o| o.name.to_string()).collect();
            for (i, output) in outputs.iter().enumerate() {
                compiler.set_decoration(output.id, Decoration::Location, Some(i as u32)).map_err(text)?;
            }
        }
        for buffer in &buffers {
            let name = buffer.name.to_string();
            let i = match self.uniforms.iter().position(|u| u.name == name) {
                Some(i) => i,
                None => {
                    let index = UNIFORM_BASE + self.uniforms.len() as u32;
                    self.uniforms.push(Binding { name, index, texel: false });
                    self.uniforms.len() - 1
                }
            };
            compiler.set_decoration(buffer.id, Decoration::DescriptorSet, Some(0u32)).map_err(text)?;
            compiler.set_decoration(buffer.id, Decoration::Binding, Some(self.uniforms[i].index)).map_err(text)?;
        }
        for image in &images {
            let name = image.name.to_string();
            let dim = match compiler.type_description(image.base_type_id).map_err(text)?.inner {
                TypeInner::Image(image) => image.dimension,
                _ => return Err(format!("sampler {name} has no image type")),
            };
            let texel = self.texels.iter().any(|t| t.name == name && t.buffer);
            if texel != (dim == Dim::DimBuffer) {
                return Err(format!("sampler {name} dimension does not match its binding"));
            }
            let i = match self.textures.iter().position(|t| t.name == name) {
                Some(i) => i,
                None => {
                    let index = self.textures.len() as u32;
                    self.textures.push(Binding { name, index, texel });
                    self.textures.len() - 1
                }
            };
            compiler.set_decoration(image.id, Decoration::DescriptorSet, Some(0u32)).map_err(text)?;
            compiler.set_decoration(image.id, Decoration::Binding, Some(self.textures[i].index)).map_err(text)?;
        }
        let mut options = CompilerOptions::default();
        options.version = MslVersion::from((3, 0));
        options.platform = MetalPlatform::MacOS;
        options.enable_decoration_binding = true;
        options.texture_buffer_native = true;
        options.common.flip_vertex_y = true;
        let compiled = compiler.compile(&options).map_err(text)?;
        let entry = compiled.cleansed_entry_point_name("main", model).map_err(text)?.ok_or("no entry point")?;
        Ok((compiled.to_string(), entry.to_string()))
    }
}

fn text<E: std::fmt::Display>(e: E) -> String {
    e.to_string()
}

fn run(vertex: &[u8], fragment: &[u8], request: Request) -> Result<Reply, String> {
    let mut stages = Stages {
        inputs: request.inputs,
        texels: request.texels,
        outputs: Vec::new(),
        uniforms: Vec::new(),
        textures: Vec::new(),
    };
    let (vertex_source, vertex_entry) = stages.stage(vertex, ExecutionModel::Vertex)?;
    let (fragment_source, fragment_entry) = stages.stage(fragment, ExecutionModel::Fragment)?;
    Ok(Reply {
        error: None,
        vertex: vertex_source,
        fragment: fragment_source,
        vertex_entry,
        fragment_entry,
        uniforms: stages.uniforms,
        textures: stages.textures,
    })
}

#[no_mangle]
pub unsafe extern "C" fn tin_translate(
    vertex: *const u8, vertex_len: usize, fragment: *const u8, fragment_len: usize, request: *const c_char,
) -> *mut c_char {
    let vertex = unsafe { slice::from_raw_parts(vertex, vertex_len) };
    let fragment = unsafe { slice::from_raw_parts(fragment, fragment_len) };
    let json = unsafe { CStr::from_ptr(request) }.to_string_lossy().into_owned();
    let reply = match serde_json::from_str(&json).map_err(text).and_then(|r| run(vertex, fragment, r)) {
        Ok(reply) => reply,
        Err(error) => Reply { error: Some(error), ..Default::default() },
    };
    let out = serde_json::to_string(&reply).unwrap_or_else(|e| format!("{{\"error\":\"{e}\"}}"));
    CString::new(out).unwrap_or_default().into_raw()
}

#[no_mangle]
pub unsafe extern "C" fn tin_free(text: *mut c_char) {
    if !text.is_null() {
        drop(unsafe { CString::from_raw(text) });
    }
}
