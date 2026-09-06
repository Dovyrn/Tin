#![allow(clippy::todo, clippy::too_many_arguments, unused_variables)]

use jni::errors::{Error, ThrowRuntimeExAndDefault};
use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jdouble, jfloat, jint, jlong};
use jni::{Env, EnvUnowned};
use metal::device::Device;
use metal::encoder::Encoder;
use metal::sampler::{Address, Filter};
use metal::surface::{Present, Surface};
use metal::texture::Texture;
use metal::view::View;
use objc2_app_kit::NSWindow;

const IMMEDIATE: jint = 0;
const FIFO: jint = 2;

fn device<'a>(handle: jlong) -> &'a Device {
    unsafe { &*(handle as *const Device) }
}

fn texture<'a>(handle: jlong) -> &'a Texture {
    unsafe { &*(handle as *const Texture) }
}

fn view<'a>(handle: jlong) -> &'a View {
    unsafe { &*(handle as *const View) }
}

fn boxed<T>(value: T) -> jlong {
    Box::into_raw(Box::new(value)) as jlong
}

fn text(env: &Env, s: &JString) -> Result<String, Error> {
    Ok(s.mutf8_chars(env)?.to_str().into_owned())
}

fn ints(env: &Env, a: &JIntArray) -> Result<Vec<i32>, Error> {
    let mut out = vec![0; a.len(env)?];
    a.get_region(env, 0, &mut out)?;
    Ok(out)
}

fn address(ordinal: jint) -> Address {
    match ordinal {
        0 => Address::Repeat,
        1 => Address::Clamp,
        _ => unreachable!("address mode {ordinal}"),
    }
}

fn filter(ordinal: jint) -> Filter {
    match ordinal {
        0 => Filter::Nearest,
        1 => Filter::Linear,
        _ => unreachable!("filter {ordinal}"),
    }
}

fn ordinal(mode: Present) -> jint {
    match mode {
        Present::Immediate => IMMEDIATE,
        Present::Fifo => FIFO,
    }
}

fn strings<'l>(env: &mut Env<'l>, items: &[String]) -> Result<JObjectArray<'l, JString<'l>>, Error> {
    let out = JObjectArray::<JString>::new(env, items.len(), JString::null())?;
    for (i, item) in items.iter().enumerate() {
        let value = env.new_string(item)?;
        out.set_element(env, i, value)?;
    }
    Ok(out)
}

fn encoder<'a>(handle: jlong) -> &'a mut Encoder {
    unsafe { &mut *(handle as *mut Encoder) }
}

fn surface<'a>(handle: jlong) -> &'a mut Surface {
    unsafe { &mut *(handle as *mut Surface) }
}

fn present(mode: jint) -> Present {
    match mode {
        IMMEDIATE => Present::Immediate,
        FIFO => Present::Fifo,
        _ => unreachable!("present mode {mode}"),
    }
}

macro_rules! entries {
    ($($name:ident($env:pat_param, $class:pat_param $(, $arg:ident: $ty:ty)* $(,)?) $(-> $ret:ty)? $body:block)*) => {
        $(
            #[allow(non_snake_case)]
            #[export_name = concat!("Java_dev_dov_tin_Native_", stringify!($name))]
            pub extern "system" fn $name<'l>($env: EnvUnowned<'l>, $class: JClass<'l> $(, $arg: $ty)*) $(-> $ret)? $body
        )*
        #[cfg(test)]
        const ENTRIES: &[(&str, usize)] = &[$((stringify!($name), 0 $(+ { let _ = stringify!($arg); 1 })*)),*];
    };
}

entries! {
    nDeviceCreate(_env, _class,
        window: jlong, log_level: jint, sync_logs: jboolean, labels: jboolean, validation: jboolean,
    ) -> jlong {
        boxed(Device::new(validation))
    }

    nDeviceSurface(_env, _class, handle: jlong, window: jlong) -> jlong {
        let window = unsafe { &*(window as *const NSWindow) };
        let surface = Surface::new(device(handle), window);
        Box::into_raw(Box::new(surface)) as jlong
    }

    nDeviceEncoder(_env, _class, handle: jlong) -> jlong {
        Box::into_raw(Box::new(Encoder::new(device(handle).queue.clone()))) as jlong
    }

    nDeviceSampler(_env, _class,
        handle: jlong, u: jint, v: jint, min: jint, mag: jint, anisotropy: jint, has_lod: jboolean, max_lod: jdouble,
    ) -> jlong {
        let lod = has_lod.then_some(max_lod as f32);
        boxed(device(handle).sampler(address(u), address(v), filter(min), filter(mag), anisotropy as u32, lod))
    }

    nDeviceTexture(mut env, _class,
        handle: jlong, label: JString<'l>, usage: jint, format: jint, width: jint, height: jint, layers: jint, mips: jint,
    ) -> jlong {
        env.with_env(|env| {
            let label = text(env, &label)?;
            let texture = device(handle).texture(
                &label,
                usage as u32,
                format as u32,
                width as u32,
                height as u32,
                layers as u32,
                mips as u32,
            );
            Ok::<_, Error>(boxed(texture))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nDeviceView(_env, _class, handle: jlong, texture_handle: jlong, base: jint, mips: jint) -> jlong {
        boxed(device(handle).view(texture(texture_handle), base as u32, mips as u32))
    }

    nDeviceBuffer(mut env, _class, handle: jlong, label: JString<'l>, usage: jint, size: jlong) -> jlong {
        env.with_env(|env| {
            let label = text(env, &label)?;
            Ok::<_, Error>(boxed(device(handle).buffer(&label, usage as u32, size as u64)))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nDeviceBufferData(mut env, _class,
        handle: jlong, label: JString<'l>, usage: jint, address: jlong, length: jint,
    ) -> jlong {
        env.with_env(|env| {
            let label = text(env, &label)?;
            let data = unsafe { std::slice::from_raw_parts(address as *const u8, length as usize) };
            Ok::<_, Error>(boxed(device(handle).buffer_with(&label, usage as u32, data)))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nDeviceMessages(mut env, _class, handle: jlong) -> JObjectArray<'l, JString<'l>> {
        env.with_env(|env| strings(env, &device(handle).messages())).resolve::<ThrowRuntimeExAndDefault>()
    }

    nDeviceDebugging(_env, _class, handle: jlong) -> jboolean {
        device(handle).debug
    }

    nDevicePipeline(mut env, _class,
        handle: jlong, location: JString<'l>, vertex: JString<'l>, fragment: JString<'l>, defines: JString<'l>, state: JIntArray<'l>,
    ) -> jlong {
        env.with_env(|env| {
            let location = text(env, &location)?;
            let vertex = text(env, &vertex)?;
            let fragment = text(env, &fragment)?;
            let defines = text(env, &defines)?;
            let state = ints(env, &state)?;
            let pipeline = device(handle).pipeline(&location, &vertex, &fragment, &defines, &state);
            Ok::<_, Error>(boxed(pipeline))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nDeviceClearPipelines(_env, _class, handle: jlong) {
        device(handle).clear_pipelines();
    }

    nDeviceClose(_env, _class, handle: jlong) {
        drop(unsafe { Box::from_raw(handle as *mut Device) });
    }

    nDeviceQueries(_env, _class, handle: jlong, size: jint) -> jlong {
        boxed(device(handle).queries(size as u32))
    }

    nDeviceTimestamp(_env, _class, handle: jlong) -> jlong {
        device(handle).now() as jlong
    }

    nDeviceInfoNumbers(mut env, _class, handle: jlong) -> JLongArray<'l> {
        env.with_env(|env| {
            let numbers = device(handle).numbers();
            let out = env.new_long_array(numbers.len())?;
            out.set_region(env, 0, &numbers)?;
            Ok::<_, Error>(out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nDeviceInfoStrings(mut env, _class, handle: jlong) -> JObjectArray<'l, JString<'l>> {
        env.with_env(|env| strings(env, &device(handle).strings())).resolve::<ThrowRuntimeExAndDefault>()
    }

    nPipelineValid(_env, _class, pipeline: jlong) -> jboolean {
        todo!()
    }

    nEncoderSubmit(_env, _class, handle: jlong) {
        encoder(handle).submit();
    }

    nEncoderMemory(_env, _class, encoder: jlong) -> jlong {
        todo!()
    }

    nEncoderPass(_env, _class,
        encoder: jlong, label: JString<'l>, views: JLongArray<'l>, clears: JFloatArray<'l>, area: JIntArray<'l>,
    ) -> jlong {
        todo!()
    }

    nEncoderSubmitPass(_env, _class, encoder: jlong) {
        todo!()
    }

    nEncoderClearColor(_env, _class, encoder: jlong, texture: jlong, r: jfloat, g: jfloat, b: jfloat, a: jfloat) {
        todo!()
    }

    nEncoderClearColorDepth(_env, _class,
        encoder: jlong, color: jlong, r: jfloat, g: jfloat, b: jfloat, a: jfloat, depth: jlong, value: jdouble,
    ) {
        todo!()
    }

    nEncoderClearColorDepthRegion(_env, _class,
        encoder: jlong, color: jlong, r: jfloat, g: jfloat, b: jfloat, a: jfloat, depth: jlong, value: jdouble, x: jint, y: jint, width: jint, height: jint,
    ) {
        todo!()
    }

    nEncoderClearDepth(_env, _class, encoder: jlong, texture: jlong, value: jdouble) {
        todo!()
    }

    nEncoderWriteBuffer(_env, _class,
        encoder: jlong, buffer: jlong, offset: jlong, length: jlong, address: jlong, size: jint,
    ) {
        todo!()
    }

    nEncoderCopyBuffer(_env, _class,
        encoder: jlong, source: jlong, source_offset: jlong, source_length: jlong, target: jlong, target_offset: jlong, target_length: jlong,
    ) {
        todo!()
    }

    nEncoderWriteTexture(_env, _class,
        encoder: jlong, texture: jlong, address: jlong, size: jint, mip: jint, layer: jint, x: jint, y: jint, width: jint, height: jint,
    ) {
        todo!()
    }

    nEncoderCopyBufferTexture(_env, _class,
        encoder: jlong, buffer: jlong, offset: jlong, length: jlong, source_x: jint, source_y: jint, source_width: jint, source_height: jint, texture: jlong, x: jint, y: jint, width: jint, height: jint, mip: jint, layer: jint,
    ) {
        todo!()
    }

    nEncoderCopyTextureBuffer(_env, _class,
        encoder: jlong, texture: jlong, buffer: jlong, offset: jlong, callback: JObject<'l>, mip: jint,
    ) {
        todo!()
    }

    nEncoderCopyTextureBufferRegion(_env, _class,
        encoder: jlong, texture: jlong, buffer: jlong, offset: jlong, callback: JObject<'l>, mip: jint, x: jint, y: jint, width: jint, height: jint,
    ) {
        todo!()
    }

    nEncoderCopyTexture(_env, _class,
        encoder: jlong, source: jlong, target: jlong, mip: jint, x: jint, y: jint, source_x: jint, source_y: jint, width: jint, height: jint,
    ) {
        todo!()
    }

    nEncoderFence(_env, _class, encoder: jlong) -> jlong {
        todo!()
    }

    nEncoderTimestamp(_env, _class, encoder: jlong, queries: jlong, index: jint) {
        todo!()
    }

    nFenceAwait(_env, _class, fence: jlong, timeout_ms: jlong) -> jboolean {
        todo!()
    }

    nFenceClose(_env, _class, fence: jlong) {
        todo!()
    }

    nPassPush(_env, _class, pass: jlong, label: JString<'l>) {
        todo!()
    }

    nPassPop(_env, _class, pass: jlong) {
        todo!()
    }

    nPassPipeline(_env, _class, pass: jlong, pipeline: jlong) {
        todo!()
    }

    nPassTexture(_env, _class, pass: jlong, name: JString<'l>, view: jlong, sampler: jlong) {
        todo!()
    }

    nPassUniform(_env, _class, pass: jlong, name: JString<'l>, buffer: jlong, offset: jlong, length: jlong) {
        todo!()
    }

    nPassScissor(_env, _class, pass: jlong, x: jint, y: jint, width: jint, height: jint) {
        todo!()
    }

    nPassNoScissor(_env, _class, pass: jlong) {
        todo!()
    }

    nPassVertex(_env, _class, pass: jlong, slot: jint, buffer: jlong, offset: jlong, length: jlong) {
        todo!()
    }

    nPassIndex(_env, _class, pass: jlong, buffer: jlong, kind: jint) {
        todo!()
    }

    nPassDrawIndexed(_env, _class,
        pass: jlong, index_count: jint, instance_count: jint, first_index: jint, vertex_offset: jint, first_instance: jint,
    ) {
        todo!()
    }

    nPassMultiDrawIndexed(_env, _class,
        pass: jlong, params: jlong, instance_count: jint, first_instance: jint, draw_count: jint,
    ) {
        todo!()
    }

    nPassMultiDrawIndexedSeparate(_env, _class,
        pass: jlong, first_index_offsets: jlong, index_counts: jlong, vertex_offsets: jlong, draw_count: jint,
    ) {
        todo!()
    }

    nPassDrawIndexedIndirect(_env, _class,
        pass: jlong, buffer: jlong, offset: jlong, length: jlong, draw_count: jint,
    ) {
        todo!()
    }

    nPassDrawOne(_env, _class,
        pass: jlong, slot: jint, vertex: jlong, index: jlong, kind: jint, first_index: jint, index_count: jint, base_vertex: jint,
    ) {
        todo!()
    }

    nPassDraw(_env, _class,
        pass: jlong, vertex_count: jint, instance_count: jint, first_vertex: jint, first_instance: jint,
    ) {
        todo!()
    }

    nPassMultiDraw(_env, _class,
        pass: jlong, params: jlong, instance_count: jint, first_instance: jint, draw_count: jint,
    ) {
        todo!()
    }

    nPassMultiDrawSeparate(_env, _class,
        pass: jlong, first_vertices: jlong, vertex_counts: jlong, draw_count: jint,
    ) {
        todo!()
    }

    nPassDrawIndirect(_env, _class, pass: jlong, buffer: jlong, offset: jlong, length: jlong, draw_count: jint) {
        todo!()
    }

    nPassTimestamp(_env, _class, pass: jlong, queries: jlong, index: jint) {
        todo!()
    }

    nSurfaceConfigure(_env, _class, handle: jlong, width: jint, height: jint, mode: jint) {
        surface(handle).configure(width as u32, height as u32, present(mode));
    }

    nSurfaceSuboptimal(_env, _class, handle: jlong) -> jboolean {
        surface(handle).suboptimal()
    }

    nSurfaceAcquire(_env, _class, handle: jlong) {
        assert!(surface(handle).acquire(), "no drawable");
    }

    nSurfaceBlit(_env, _class, handle: jlong, encoder_handle: jlong, view_handle: jlong) {
        surface(handle).blit(encoder(encoder_handle), view(view_handle));
    }

    nSurfacePresent(_env, _class, handle: jlong, device_handle: jlong) {
        surface(handle).present(device(device_handle));
    }

    nSurfaceClose(_env, _class, handle: jlong) {
        drop(unsafe { Box::from_raw(handle as *mut Surface) });
    }

    nSurfaceModes(mut env, _class, handle: jlong) -> JIntArray<'l> {
        env.with_env(|env| {
            let modes: Vec<jint> = surface(handle).modes().iter().map(|&m| ordinal(m)).collect();
            let out = env.new_int_array(modes.len())?;
            out.set_region(env, 0, &modes)?;
            Ok::<_, Error>(out)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryCpu(_env, _class,
        memory: jlong, size: jlong, alignment: jlong, minimum: jlong, element: jlong,
    ) -> jlong {
        todo!()
    }

    nMemoryStaging(_env, _class,
        memory: jlong, size: jlong, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        todo!()
    }

    nMemoryGpu(_env, _class,
        memory: jlong, size: jlong, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        todo!()
    }

    nMemoryGpuMapped(_env, _class,
        memory: jlong, size: jlong, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        todo!()
    }

    nMemoryUploadStaging(_env, _class,
        memory: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        todo!()
    }

    nMemoryUploadGpu(_env, _class,
        memory: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        todo!()
    }

    nMemoryMultiStaging(_env, _class,
        memory: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint,
    ) -> JLongArray<'l> {
        todo!()
    }

    nMemoryMultiGpu(_env, _class,
        memory: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint,
    ) -> JLongArray<'l> {
        todo!()
    }

    nBufferClosed(_env, _class, buffer: jlong) -> jboolean {
        todo!()
    }

    nBufferClose(_env, _class, buffer: jlong) {
        todo!()
    }

    nBufferMap(_env, _class,
        buffer: jlong, offset: jlong, length: jlong, read: jboolean, write: jboolean,
    ) -> jlong {
        todo!()
    }

    nBufferUnmap(_env, _class, buffer: jlong) {
        todo!()
    }

    nTextureClosed(_env, _class, texture: jlong) -> jboolean {
        todo!()
    }

    nTextureClose(_env, _class, texture: jlong) {
        todo!()
    }

    nViewClosed(_env, _class, view: jlong) -> jboolean {
        todo!()
    }

    nViewClose(_env, _class, view: jlong) {
        todo!()
    }

    nSamplerClose(_env, _class, sampler: jlong) {
        todo!()
    }

    nQueriesValues(_env, _class, queries: jlong, index: jint, count: jint) -> JLongArray<'l> {
        todo!()
    }

    nQueriesClose(_env, _class, queries: jlong) {
        todo!()
    }

}

#[cfg(test)]
mod tests {
    use super::ENTRIES;

    #[test]
    fn java() {
        let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../java/dev/dov/tin/bridge/Native.java");
        let source = std::fs::read_to_string(path).expect("Native.java");
        let mut java: Vec<(String, usize)> = source
            .split(';')
            .filter_map(|s| {
                let (_, decl) = s.rsplit_once(" native ")?;
                let name = decl.split_whitespace().nth(1)?;
                let open = name.find('(')?;
                let start = decl.find('(')?;
                let close = decl.rfind(')')?;
                let args = decl[start + 1..close].split(',').filter(|a| !a.trim().is_empty()).count();
                Some((name[..open].to_string(), args))
            })
            .collect();
        java.sort();
        let mut rust: Vec<(String, usize)> = ENTRIES.iter().map(|&(n, a)| (n.to_string(), a)).collect();
        rust.sort();
        assert_eq!(rust, java);
    }
}
