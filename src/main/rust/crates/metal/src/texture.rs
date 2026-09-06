use crate::device::Device;
use crate::format::format;
use objc2::rc::Retained;
use objc2::runtime::ProtocolObject;
use objc2_foundation::NSString;
use objc2_metal::{MTLDevice, MTLResource, MTLStorageMode, MTLTexture, MTLTextureDescriptor, MTLTextureUsage};

const BINDING: u32 = 4;
const ATTACHMENT: u32 = 8;
const CUBE: u32 = 16;

pub struct Texture {
    pub raw: Retained<ProtocolObject<dyn MTLTexture>>,
    pub pixel: u32,
    pub width: u32,
    pub height: u32,
}

impl Texture {
    pub fn new(
        device: &Device, label: &str, usage: u32, format: u32, width: u32, height: u32, layers: u32, mips: u32,
    ) -> Self {
        let format = self::format(format);
        let cube = usage & CUBE != 0;
        assert!(layers == if cube { 6 } else { 1 }, "unsupported texture layout");
        let info = unsafe {
            if cube {
                MTLTextureDescriptor::textureCubeDescriptorWithPixelFormat_size_mipmapped(
                    format.raw,
                    width as usize,
                    mips > 1,
                )
            } else {
                MTLTextureDescriptor::texture2DDescriptorWithPixelFormat_width_height_mipmapped(
                    format.raw,
                    width as usize,
                    height as usize,
                    mips > 1,
                )
            }
        };
        unsafe {
            info.setMipmapLevelCount(mips as usize);
        }
        info.setStorageMode(MTLStorageMode::Private);
        let mut flags = MTLTextureUsage::empty();
        if usage & BINDING != 0 {
            flags |= MTLTextureUsage::ShaderRead;
        }
        if usage & ATTACHMENT != 0 {
            flags |= MTLTextureUsage::RenderTarget;
        }
        info.setUsage(flags);
        let raw = device.device.newTextureWithDescriptor(&info).expect("texture");
        raw.setLabel(Some(&NSString::from_str(label)));
        Self { raw, pixel: format.pixel, width, height }
    }
}
