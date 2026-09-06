use objc2_metal::MTLPixelFormat;

pub struct Format {
    pub raw: MTLPixelFormat,
    pub pixel: u32,
}

const fn f(raw: MTLPixelFormat, pixel: u32) -> Format {
    Format { raw, pixel }
}

pub fn format(ordinal: u32) -> Format {
    match ordinal {
        0 => f(MTLPixelFormat::R8Unorm, 1),
        1 => f(MTLPixelFormat::R8Snorm, 1),
        2 => f(MTLPixelFormat::RG8Unorm, 2),
        3 => f(MTLPixelFormat::RG8Snorm, 2),
        6 => f(MTLPixelFormat::RGBA8Unorm, 4),
        7 => f(MTLPixelFormat::RGBA8Snorm, 4),
        8 => f(MTLPixelFormat::R16Unorm, 2),
        9 => f(MTLPixelFormat::R16Snorm, 2),
        10 => f(MTLPixelFormat::RG16Unorm, 4),
        11 => f(MTLPixelFormat::RG16Snorm, 4),
        14 => f(MTLPixelFormat::RGBA16Unorm, 8),
        15 => f(MTLPixelFormat::RGBA16Snorm, 8),
        16 => f(MTLPixelFormat::R8Uint, 1),
        17 => f(MTLPixelFormat::R8Sint, 1),
        18 => f(MTLPixelFormat::RG8Uint, 2),
        19 => f(MTLPixelFormat::RG8Sint, 2),
        22 => f(MTLPixelFormat::RGBA8Uint, 4),
        23 => f(MTLPixelFormat::RGBA8Sint, 4),
        24 => f(MTLPixelFormat::R16Uint, 2),
        25 => f(MTLPixelFormat::R16Sint, 2),
        26 => f(MTLPixelFormat::RG16Uint, 4),
        27 => f(MTLPixelFormat::RG16Sint, 4),
        30 => f(MTLPixelFormat::RGBA16Uint, 8),
        31 => f(MTLPixelFormat::RGBA16Sint, 8),
        32 => f(MTLPixelFormat::R32Uint, 4),
        33 => f(MTLPixelFormat::R32Sint, 4),
        34 => f(MTLPixelFormat::RG32Uint, 8),
        35 => f(MTLPixelFormat::RG32Sint, 8),
        38 => f(MTLPixelFormat::RGBA32Uint, 16),
        39 => f(MTLPixelFormat::RGBA32Sint, 16),
        40 => f(MTLPixelFormat::R16Float, 2),
        41 => f(MTLPixelFormat::RG16Float, 4),
        43 => f(MTLPixelFormat::RGBA16Float, 8),
        44 => f(MTLPixelFormat::R32Float, 4),
        45 => f(MTLPixelFormat::RG32Float, 8),
        47 => f(MTLPixelFormat::RGBA32Float, 16),
        48 => f(MTLPixelFormat::RGB10A2Unorm, 4),
        49 => f(MTLPixelFormat::RGB10A2Uint, 4),
        50 => f(MTLPixelFormat::RG11B10Float, 4),
        51 => f(MTLPixelFormat::Depth32Float, 4),
        52 => f(MTLPixelFormat::Depth32Float_Stencil8, 8),
        53 => f(MTLPixelFormat::Depth24Unorm_Stencil8, 4),
        54 => f(MTLPixelFormat::Depth16Unorm, 2),
        55 => f(MTLPixelFormat::Stencil8, 1),
        _ => unreachable!("format {ordinal} has no metal form"),
    }
}
