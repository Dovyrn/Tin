use crate::device::Device;
use crate::encoder::Encoder;
use crate::view::View;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_app_kit::NSWindow;
use objc2_core_foundation::CGSize;
use objc2_metal::{
    MTLBlitCommandEncoder, MTLClearColor, MTLCommandBuffer, MTLCommandEncoder, MTLCommandQueue, MTLLoadAction,
    MTLPixelFormat, MTLRenderPassDescriptor, MTLStoreAction,
};
use objc2_quartz_core::{CAMetalDrawable, CAMetalLayer};

#[derive(Clone, Copy, PartialEq, Eq)]
pub enum Present {
    Immediate,
    Fifo,
}

pub struct Surface {
    layer: Retained<CAMetalLayer>,
    drawable: Option<Retained<ProtocolObject<dyn CAMetalDrawable>>>,
    width: u32,
    height: u32,
}

impl Surface {
    pub fn new(device: &Device, window: &NSWindow) -> Self {
        let layer = CAMetalLayer::new();
        layer.setDevice(Some(&device.device));
        layer.setPixelFormat(MTLPixelFormat::BGRA8Unorm);
        layer.setFramebufferOnly(false);
        let view = window.contentView().expect("window content view");
        layer.setContentsScale(window.backingScaleFactor());
        view.setWantsLayer(true);
        view.setLayer(Some(&layer));
        Self { layer, drawable: None, width: 0, height: 0 }
    }

    pub fn configure(&mut self, width: u32, height: u32, present: Present) {
        self.width = width;
        self.height = height;
        self.layer.setDrawableSize(CGSize::new(width as f64, height as f64));
        self.layer.setDisplaySyncEnabled(present == Present::Fifo);
    }

    pub fn acquire(&mut self) -> bool {
        self.drawable = self.layer.nextDrawable();
        self.drawable.is_some()
    }

    pub fn blit(&mut self, encoder: &mut Encoder, source: &View) {
        let Some(drawable) = self.drawable.take() else {
            return;
        };
        let cmd = encoder.cmd();
        let blit = cmd.blitCommandEncoder().expect("blit encoder");
        unsafe {
            blit.copyFromTexture_toTexture(&source.raw, &drawable.texture());
        }
        blit.endEncoding();
        cmd.presentDrawable(ProtocolObject::from_ref(&*drawable));
    }

    pub fn present(&mut self, device: &Device) {
        let Some(drawable) = self.drawable.take() else {
            return;
        };
        let cmd = device.queue.commandBuffer().expect("command buffer");
        let pass = MTLRenderPassDescriptor::renderPassDescriptor();
        let color = unsafe { pass.colorAttachments().objectAtIndexedSubscript(0) };
        color.setTexture(Some(&drawable.texture()));
        color.setLoadAction(MTLLoadAction::Clear);
        color.setStoreAction(MTLStoreAction::Store);
        color.setClearColor(MTLClearColor { red: 0.0, green: 0.0, blue: 0.0, alpha: 1.0 });
        let encoder = cmd.renderCommandEncoderWithDescriptor(&pass).expect("render encoder");
        encoder.endEncoding();
        cmd.presentDrawable(ProtocolObject::from_ref(&*drawable));
        cmd.commit();
    }

    pub fn suboptimal(&self) -> bool {
        false
    }

    pub fn modes(&self) -> [Present; 2] {
        [Present::Immediate, Present::Fifo]
    }
}
