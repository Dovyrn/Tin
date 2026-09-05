#![allow(clippy::todo, clippy::too_many_arguments, unused_variables)]

use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jdouble, jfloat, jint, jlong};
use jni::EnvUnowned;

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
        todo!()
    }

    nDeviceSurface(_env, _class, device: jlong, window: jlong) -> jlong {
        todo!()
    }

    nDeviceEncoder(_env, _class, device: jlong) -> jlong {
        todo!()
    }

    nDeviceSampler(_env, _class,
        device: jlong, u: jint, v: jint, min: jint, mag: jint, anisotropy: jint, has_lod: jboolean, max_lod: jdouble,
    ) -> jlong {
        todo!()
    }

    nDeviceTexture(_env, _class,
        device: jlong, label: JString<'l>, usage: jint, format: jint, width: jint, height: jint, depth_or_layers: jint, mips: jint,
    ) -> jlong {
        todo!()
    }

    nDeviceView(_env, _class, device: jlong, texture: jlong, base_mip: jint, mips: jint) -> jlong {
        todo!()
    }

    nDeviceBuffer(_env, _class, device: jlong, label: JString<'l>, usage: jint, size: jlong) -> jlong {
        todo!()
    }

    nDeviceBufferData(_env, _class,
        device: jlong, label: JString<'l>, usage: jint, address: jlong, length: jint,
    ) -> jlong {
        todo!()
    }

    nDeviceMessages(_env, _class, device: jlong) -> JObjectArray<'l, JString<'l>> {
        todo!()
    }

    nDeviceDebugging(_env, _class, device: jlong) -> jboolean {
        todo!()
    }

    nDevicePipeline(_env, _class,
        device: jlong, location: JString<'l>, vertex: JString<'l>, fragment: JString<'l>, defines: JString<'l>, state: JIntArray<'l>,
    ) -> jlong {
        todo!()
    }

    nDeviceClearPipelines(_env, _class, device: jlong) {
        todo!()
    }

    nDeviceClose(_env, _class, device: jlong) {
        todo!()
    }

    nDeviceQueries(_env, _class, device: jlong, size: jint) -> jlong {
        todo!()
    }

    nDeviceTimestamp(_env, _class, device: jlong) -> jlong {
        todo!()
    }

    nDeviceInfoNumbers(_env, _class, device: jlong) -> JLongArray<'l> {
        todo!()
    }

    nDeviceInfoStrings(_env, _class, device: jlong) -> JObjectArray<'l, JString<'l>> {
        todo!()
    }

    nPipelineValid(_env, _class, pipeline: jlong) -> jboolean {
        todo!()
    }

    nEncoderSubmit(_env, _class, encoder: jlong) {
        todo!()
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

    nSurfaceConfigure(_env, _class, surface: jlong, width: jint, height: jint, mode: jint) {
        todo!()
    }

    nSurfaceSuboptimal(_env, _class, surface: jlong) -> jboolean {
        todo!()
    }

    nSurfaceAcquire(_env, _class, surface: jlong) {
        todo!()
    }

    nSurfaceBlit(_env, _class, surface: jlong, encoder: jlong, view: jlong) {
        todo!()
    }

    nSurfacePresent(_env, _class, surface: jlong) {
        todo!()
    }

    nSurfaceClose(_env, _class, surface: jlong) {
        todo!()
    }

    nSurfaceModes(_env, _class, surface: jlong) -> JIntArray<'l> {
        todo!()
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
