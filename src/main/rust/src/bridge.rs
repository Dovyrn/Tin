#![allow(clippy::todo, clippy::too_many_arguments, unused_variables)]

use jni::errors::{Error, ThrowRuntimeExAndDefault};
use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jdouble, jfloat, jint, jlong};
use jni::{jni_sig, jni_str, Env, EnvUnowned, JavaVM};
use metal::buffer::Buffer;
use metal::device::Device;
use metal::encoder::Encoder;
use metal::memory::{Memory, Slice};
use metal::pass::{Color, Depth, Index, Pass};
use metal::pipeline::Pipeline;
use metal::queries::Queries;

use metal::sampler::{Address, Filter, Sampler};
use metal::surface::{Present, Surface};
use metal::texture::Texture;
use metal::view::View;
use objc2_app_kit::NSWindow;

const IMMEDIATE: jint = 0;
const FIFO: jint = 2;

fn device<'a>(handle: jlong) -> &'a Device {
    unsafe { &*(handle as *const Device) }
}

fn buffer<'a>(handle: jlong) -> &'a Buffer {
    unsafe { &*(handle as *const Buffer) }
}

fn queries<'a>(handle: jlong) -> &'a Queries {
    unsafe { &*(handle as *const Queries) }
}

fn bytes<'a>(address: jlong, length: jint) -> &'a [u8] {
    unsafe { std::slice::from_raw_parts(address as *const u8, length as usize) }
}

fn longs(env: &Env, a: &JLongArray) -> Result<Vec<i64>, Error> {
    let mut out = vec![0; a.len(env)?];
    a.get_region(env, 0, &mut out)?;
    Ok(out)
}

fn floats(env: &Env, a: &JFloatArray) -> Result<Vec<f32>, Error> {
    let mut out = vec![0.0; a.len(env)?];
    a.get_region(env, 0, &mut out)?;
    Ok(out)
}

fn runnable(env: &Env, callback: JObject) -> Result<Box<dyn FnOnce() + Send>, Error> {
    let vm: JavaVM = env.get_java_vm()?;
    let callback = env.new_global_ref(callback)?;
    Ok(Box::new(move || {
        vm.attach_current_thread(|env| {
            env.call_method(callback.as_obj(), jni_str!("run"), jni_sig!(() -> void), &[])?;
            Ok::<_, Error>(())
        })
        .expect("callback");
    }))
}

fn memory<'a>(handle: jlong) -> &'a mut Memory {
    unsafe { &mut *(handle as *mut Memory) }
}

fn pass<'a>(handle: jlong) -> &'a mut Pass {
    unsafe { &mut *(handle as *mut Pass) }
}

fn pipeline<'a>(handle: jlong) -> &'a Pipeline {
    unsafe { &*(handle as *const Pipeline) }
}

fn sampler<'a>(handle: jlong) -> &'a Sampler {
    unsafe { &*(handle as *const Sampler) }
}

fn index(ordinal: jint) -> Index {
    match ordinal {
        0 => Index::Short,
        1 => Index::Int,
        _ => unreachable!("index type {ordinal}"),
    }
}

fn parts<'a>(env: &Env, addresses: &JLongArray, sizes: &JIntArray) -> Result<Vec<&'a [u8]>, Error> {
    let addresses = longs(env, addresses)?;
    let sizes = ints(env, sizes)?;
    Ok(addresses.iter().zip(&sizes).map(|(&a, &n)| bytes(a, n)).collect())
}

fn slices<'l>(env: &mut Env<'l>, items: &[Slice]) -> Result<JLongArray<'l>, Error> {
    let mut out = Vec::with_capacity(items.len() * 4);
    for s in items {
        out.extend([s.buffer as jlong, s.offset as jlong, s.size as jlong, s.address as jlong]);
    }
    let array = env.new_long_array(out.len())?;
    array.set_region(env, 0, &out)?;
    Ok(array)
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
        boxed(Encoder::new(device(handle)))
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

    nEncoderMemory(_env, _class, handle: jlong) -> jlong {
        &mut encoder(handle).memory as *mut _ as jlong
    }

    nEncoderPass(mut env, _class,
        handle: jlong, label: JString<'l>, views: JLongArray<'l>, clears: JFloatArray<'l>, area: JIntArray<'l>,
    ) -> jlong {
        env.with_env(|env| {
            let label = text(env, &label)?;
            let views = longs(env, &views)?;
            let clears = floats(env, &clears)?;
            let area = ints(env, &area)?;
            let area = [area[0], area[1], area[2], area[3]];
            let n = views.len() - 1;
            let colors: Vec<Color> = (0..n)
                .map(|i| Color {
                    view: (views[i] != 0).then(|| view(views[i])),
                    clear: (clears[i * 5] != 0.0).then(|| [clears[i * 5 + 1], clears[i * 5 + 2], clears[i * 5 + 3], clears[i * 5 + 4]]),
                })
                .collect();
            let depth = (views[n] != 0).then(|| Depth {
                view: view(views[n]),
                clear: (clears[n * 5] != 0.0).then_some(clears[n * 5 + 1] as f64),
            });
            let pass = encoder(handle).begin_pass(&label, &colors, depth, area);
            Ok::<_, Error>(pass as *mut _ as jlong)
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nEncoderSubmitPass(_env, _class, handle: jlong) {
        encoder(handle).end_pass();
    }

    nEncoderClearColor(_env, _class, handle: jlong, color: jlong, r: jfloat, g: jfloat, b: jfloat, a: jfloat) {
        encoder(handle).clear_color(texture(color), [r, g, b, a]);
    }

    nEncoderClearColorDepth(_env, _class,
        handle: jlong, color: jlong, r: jfloat, g: jfloat, b: jfloat, a: jfloat, depth: jlong, value: jdouble,
    ) {
        encoder(handle).clear_both(texture(color), [r, g, b, a], texture(depth), value);
    }

    nEncoderClearColorDepthRegion(_env, _class,
        handle: jlong, color: jlong, r: jfloat, g: jfloat, b: jfloat, a: jfloat, depth: jlong, value: jdouble, x: jint, y: jint, width: jint, height: jint,
    ) {
        encoder(handle).clear_region(texture(color), [r, g, b, a], texture(depth), value, x, y, width, height);
    }

    nEncoderClearDepth(_env, _class, handle: jlong, depth: jlong, value: jdouble) {
        encoder(handle).clear_depth(texture(depth), value);
    }

    nEncoderWriteBuffer(_env, _class,
        handle: jlong, target: jlong, offset: jlong, length: jlong, address: jlong, size: jint,
    ) {
        assert!(size as jlong <= length, "write past buffer slice");
        encoder(handle).write_buffer(buffer(target), offset as u64, bytes(address, size));
    }

    nEncoderCopyBuffer(_env, _class,
        handle: jlong, source: jlong, source_offset: jlong, source_length: jlong, target: jlong, target_offset: jlong, target_length: jlong,
    ) {
        assert!(source_length <= target_length, "copy past buffer slice");
        encoder(handle).copy_buffer(buffer(source), source_offset as u64, buffer(target), target_offset as u64, source_length as u64);
    }

    nEncoderWriteTexture(_env, _class,
        handle: jlong, target: jlong, address: jlong, size: jint, mip: jint, layer: jint, x: jint, y: jint, width: jint, height: jint,
    ) {
        let data = bytes(address, size);
        encoder(handle).write_texture(texture(target), data, mip as u32, layer as u32, x as u32, y as u32, width as u32, height as u32);
    }

    nEncoderCopyBufferTexture(_env, _class,
        handle: jlong, source: jlong, offset: jlong, length: jlong, source_x: jint, source_y: jint, source_width: jint, source_height: jint, target: jlong, x: jint, y: jint, width: jint, height: jint, mip: jint, layer: jint,
    ) {
        encoder(handle).copy_buffer_texture(
            buffer(source),
            offset as u64,
            source_x as u32,
            source_y as u32,
            source_width as u32,
            texture(target),
            x as u32,
            y as u32,
            width as u32,
            height as u32,
            mip as u32,
            layer as u32,
        );
    }

    nEncoderCopyTextureBuffer(mut env, _class,
        handle: jlong, source: jlong, target: jlong, offset: jlong, callback: JObject<'l>, mip: jint,
    ) {
        env.with_env(|env| {
            let done = runnable(env, callback)?;
            let source = texture(source);
            let (width, height) = (source.width >> mip, source.height >> mip);
            encoder(handle).copy_texture_buffer(source, buffer(target), offset as u64, mip as u32, 0, 0, width, height, done);
            Ok::<_, Error>(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nEncoderCopyTextureBufferRegion(mut env, _class,
        handle: jlong, source: jlong, target: jlong, offset: jlong, callback: JObject<'l>, mip: jint, x: jint, y: jint, width: jint, height: jint,
    ) {
        env.with_env(|env| {
            let done = runnable(env, callback)?;
            encoder(handle).copy_texture_buffer(
                texture(source),
                buffer(target),
                offset as u64,
                mip as u32,
                x as u32,
                y as u32,
                width as u32,
                height as u32,
                done,
            );
            Ok::<_, Error>(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nEncoderCopyTexture(_env, _class,
        handle: jlong, source: jlong, target: jlong, mip: jint, x: jint, y: jint, source_x: jint, source_y: jint, width: jint, height: jint,
    ) {
        encoder(handle).copy_texture(
            texture(source),
            texture(target),
            mip as u32,
            x as u32,
            y as u32,
            source_x as u32,
            source_y as u32,
            width as u32,
            height as u32,
        );
    }

    nEncoderFence(_env, _class, handle: jlong) -> jlong {
        boxed(encoder(handle).fence())
    }

    nEncoderTimestamp(_env, _class, handle: jlong, pool: jlong, index: jint) {
        encoder(handle).timestamp(queries(pool), index as u32);
    }

    nFenceAwait(_env, _class, fence: jlong, timeout_ms: jlong) -> jboolean {
        todo!()
    }

    nFenceClose(_env, _class, fence: jlong) {
        todo!()
    }

    nPassPush(mut env, _class, handle: jlong, label: JString<'l>) {
        env.with_env(|env| {
            pass(handle).push(&text(env, &label)?);
            Ok::<_, Error>(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nPassPop(_env, _class, handle: jlong) {
        pass(handle).pop();
    }

    nPassPipeline(_env, _class, handle: jlong, pipeline_handle: jlong) {
        pass(handle).set_pipeline(pipeline(pipeline_handle));
    }

    nPassTexture(mut env, _class, handle: jlong, name: JString<'l>, view_handle: jlong, sampler_handle: jlong) {
        env.with_env(|env| {
            let name = text(env, &name)?;
            let view = (view_handle != 0).then(|| view(view_handle));
            let sampler = (sampler_handle != 0).then(|| sampler(sampler_handle));
            pass(handle).set_texture(&name, view, sampler);
            Ok::<_, Error>(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nPassUniform(mut env, _class, handle: jlong, name: JString<'l>, buffer_handle: jlong, offset: jlong, length: jlong) {
        env.with_env(|env| {
            let name = text(env, &name)?;
            pass(handle).set_uniform(&name, buffer(buffer_handle), offset as u64);
            Ok::<_, Error>(())
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nPassScissor(_env, _class, handle: jlong, x: jint, y: jint, width: jint, height: jint) {
        pass(handle).scissor(x, y, width, height);
    }

    nPassNoScissor(_env, _class, handle: jlong) {
        pass(handle).no_scissor();
    }

    nPassVertex(_env, _class, handle: jlong, slot: jint, buffer_handle: jlong, offset: jlong, length: jlong) {
        let buffer = (buffer_handle != 0).then(|| buffer(buffer_handle));
        pass(handle).set_vertex(slot as u32, buffer, offset as u64);
    }

    nPassIndex(_env, _class, handle: jlong, buffer_handle: jlong, kind: jint) {
        pass(handle).set_index(buffer(buffer_handle), index(kind));
    }

    nPassDrawIndexed(_env, _class,
        handle: jlong, index_count: jint, instance_count: jint, first_index: jint, vertex_offset: jint, first_instance: jint,
    ) {
        pass(handle).draw_indexed(index_count as u32, instance_count as u32, first_index as u32, vertex_offset, first_instance as u32);
    }

    nPassMultiDrawIndexed(_env, _class,
        handle: jlong, params: jlong, instance_count: jint, first_instance: jint, draw_count: jint,
    ) {
        let params = unsafe { std::slice::from_raw_parts(params as *const i32, draw_count as usize * 3) };
        pass(handle).multi_draw_indexed(params, instance_count as u32, first_instance as u32);
    }

    nPassMultiDrawIndexedSeparate(_env, _class,
        handle: jlong, first_index_offsets: jlong, index_counts: jlong, vertex_offsets: jlong, draw_count: jint,
    ) {
        let n = draw_count as usize;
        let offsets = unsafe { std::slice::from_raw_parts(first_index_offsets as *const u64, n) };
        let counts = unsafe { std::slice::from_raw_parts(index_counts as *const i32, n) };
        let bases = unsafe { std::slice::from_raw_parts(vertex_offsets as *const i32, n) };
        pass(handle).multi_draw_indexed_separate(offsets, counts, bases);
    }

    nPassDrawIndexedIndirect(_env, _class,
        handle: jlong, buffer_handle: jlong, offset: jlong, length: jlong, draw_count: jint,
    ) {
        pass(handle).draw_indexed_indirect(buffer(buffer_handle), offset as u64, draw_count as u32);
    }

    nPassDrawOne(_env, _class,
        handle: jlong, slot: jint, vertex: jlong, index_handle: jlong, kind: jint, first_index: jint, index_count: jint, base_vertex: jint,
    ) {
        pass(handle).draw_one(
            slot as u32,
            buffer(vertex),
            buffer(index_handle),
            index(kind),
            first_index as u32,
            index_count as u32,
            base_vertex,
        );
    }

    nPassDraw(_env, _class,
        handle: jlong, vertex_count: jint, instance_count: jint, first_vertex: jint, first_instance: jint,
    ) {
        pass(handle).draw(vertex_count as u32, instance_count as u32, first_vertex as u32, first_instance as u32);
    }

    nPassMultiDraw(_env, _class,
        handle: jlong, params: jlong, instance_count: jint, first_instance: jint, draw_count: jint,
    ) {
        let params = unsafe { std::slice::from_raw_parts(params as *const i32, draw_count as usize * 2) };
        pass(handle).multi_draw(params, instance_count as u32, first_instance as u32);
    }

    nPassMultiDrawSeparate(_env, _class,
        handle: jlong, first_vertices: jlong, vertex_counts: jlong, draw_count: jint,
    ) {
        let n = draw_count as usize;
        let firsts = unsafe { std::slice::from_raw_parts(first_vertices as *const i32, n) };
        let counts = unsafe { std::slice::from_raw_parts(vertex_counts as *const i32, n) };
        pass(handle).multi_draw_separate(firsts, counts);
    }

    nPassDrawIndirect(_env, _class, handle: jlong, buffer_handle: jlong, offset: jlong, length: jlong, draw_count: jint) {
        pass(handle).draw_indirect(buffer(buffer_handle), offset as u64, draw_count as u32);
    }

    nPassTimestamp(_env, _class, handle: jlong, pool: jlong, index: jint) {
        pass(handle).timestamp(queries(pool), index as u32);
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
        handle: jlong, size: jlong, alignment: jlong, minimum: jlong, element: jlong,
    ) -> jlong {
        memory(handle).cpu(size as u64, alignment as u64, minimum as u64, element as u64) as jlong
    }

    nMemoryStaging(mut env, _class,
        handle: jlong, size: jlong, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        let slice = memory(handle).gpu(size as u64, alignment as u64, minimum as u64, element as u64);
        env.with_env(|env| slices(env, &[slice])).resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryGpu(mut env, _class,
        handle: jlong, size: jlong, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        let slice = memory(handle).gpu(size as u64, alignment as u64, minimum as u64, element as u64);
        env.with_env(|env| slices(env, &[slice])).resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryGpuMapped(mut env, _class,
        handle: jlong, size: jlong, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        let slice = memory(handle).gpu(size as u64, alignment as u64, minimum as u64, element as u64);
        env.with_env(|env| slices(env, &[slice])).resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryUploadStaging(mut env, _class,
        handle: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        env.with_env(|env| {
            let parts = parts(env, &addresses, &sizes)?;
            let slice = memory(handle).upload(&parts, alignment as u64, minimum as u64, element as u64);
            slices(env, &[slice])
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryUploadGpu(mut env, _class,
        handle: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint, minimum: jlong, element: jlong,
    ) -> JLongArray<'l> {
        env.with_env(|env| {
            let parts = parts(env, &addresses, &sizes)?;
            let slice = memory(handle).upload(&parts, alignment as u64, minimum as u64, element as u64);
            slices(env, &[slice])
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryMultiStaging(mut env, _class,
        handle: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint,
    ) -> JLongArray<'l> {
        env.with_env(|env| {
            let parts = parts(env, &addresses, &sizes)?;
            slices(env, &memory(handle).multi_upload(&parts, alignment as u64))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nMemoryMultiGpu(mut env, _class,
        handle: jlong, addresses: JLongArray<'l>, sizes: JIntArray<'l>, alignment: jlong, usage: jint,
    ) -> JLongArray<'l> {
        env.with_env(|env| {
            let parts = parts(env, &addresses, &sizes)?;
            slices(env, &memory(handle).multi_upload(&parts, alignment as u64))
        })
        .resolve::<ThrowRuntimeExAndDefault>()
    }

    nBufferClose(_env, _class, handle: jlong) {
        drop(unsafe { Box::from_raw(handle as *mut Buffer) });
    }

    nBufferMap(_env, _class,
        handle: jlong, offset: jlong, length: jlong, read: jboolean, write: jboolean,
    ) -> jlong {
        buffer(handle).map(offset as u64) as jlong
    }

    nBufferUnmap(_env, _class, handle: jlong) {}

    nTextureClose(_env, _class, handle: jlong) {
        drop(unsafe { Box::from_raw(handle as *mut Texture) });
    }

    nViewClose(_env, _class, handle: jlong) {
        drop(unsafe { Box::from_raw(handle as *mut View) });
    }

    nSamplerClose(_env, _class, handle: jlong) {
        drop(unsafe { Box::from_raw(handle as *mut Sampler) });
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
