#import <Cocoa/Cocoa.h>
#import <Metal/Metal.h>
#import <QuartzCore/CAMetalLayer.h>
#include <jni.h>
#include <stdatomic.h>
#include <stdint.h>

@interface VCBuffer : NSObject {
@public atomic_uint readers;
}
@property(nonatomic, strong) id<MTLBuffer> storage;
@end
@implementation VCBuffer
@end

@interface VCRenderer : NSObject
@property(nonatomic, strong) id<MTLDevice> device;
@property(nonatomic, strong) id<MTLCommandQueue> queue;
@property(nonatomic, strong) id<MTLRenderPipelineState> worldPipeline;
@property(nonatomic, strong) id<MTLRenderPipelineState> hudPipeline;
@property(nonatomic, strong) id<MTLDepthStencilState> depthState;
@property(nonatomic, strong) id<MTLDepthStencilState> hudDepthState;
@property(nonatomic, strong) CAMetalLayer* layer;
@property(nonatomic, strong) NSView* view;
@property(nonatomic, strong) CALayer* previousLayer;
@property(nonatomic) BOOL previouslyWantedLayer;
@property(nonatomic, strong) NSMutableArray<id<MTLTexture>>* textures;
@property(nonatomic, strong) id<MTLTexture> depth;
@property(nonatomic, strong) id<MTLTexture> offscreen;
@property(nonatomic, strong) id<MTLCommandBuffer> lastCommand;
@property(nonatomic, strong) dispatch_semaphore_t frames;
@end
@implementation VCRenderer
@end

static VCRenderer* renderer(jlong handle) { return (__bridge VCRenderer*)(void*)(uintptr_t)handle; }
static VCBuffer* buffer(jlong handle) { return (__bridge VCBuffer*)(void*)(uintptr_t)handle; }
static void fail(JNIEnv* env, NSString* message) {
    if (!(*env)->ExceptionCheck(env)) {
        jclass type = (*env)->FindClass(env, "java/lang/IllegalStateException");
        (*env)->ThrowNew(env, type, message.UTF8String);
    }
}
static id<MTLTexture> texture(VCRenderer* r, MTLPixelFormat format, NSUInteger w, NSUInteger h,
                              MTLStorageMode mode, MTLTextureUsage usage) {
    MTLTextureDescriptor* desc = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:format
                                         width:w height:h mipmapped:NO];
    desc.storageMode = mode;
    desc.usage = usage;
    return [r.device newTextureWithDescriptor:desc];
}

JNIEXPORT jlong JNICALL Java_dev_voxelcraft_client_render_MetalNative_create
  (JNIEnv* env, jclass cls, jlong window, jstring source, jboolean vsync) {
    @autoreleasepool {
        VCRenderer* r = [VCRenderer new];
        r.device = MTLCreateSystemDefaultDevice();
        if (!r.device || !r.device.hasUnifiedMemory) {
            fail(env, @"Metal shared-buffer backend requires a unified-memory Metal device");
            return 0;
        }
        const char* utf = (*env)->GetStringUTFChars(env, source, NULL);
        if (!utf) return 0;
        NSString* shader = [NSString stringWithUTF8String:utf];
        (*env)->ReleaseStringUTFChars(env, source, utf);
        NSError* error = nil;
        id<MTLLibrary> library = [r.device newLibraryWithSource:shader options:nil error:&error];
        if (!library) { fail(env, error.localizedDescription ?: @"Metal shader compilation failed"); return 0; }
        for (NSUInteger pass = 0; pass < 2; pass++) {
            MTLRenderPipelineDescriptor* desc = [MTLRenderPipelineDescriptor new];
            desc.vertexFunction = [library newFunctionWithName:pass ? @"hudVertex" : @"worldVertex"];
            desc.fragmentFunction = [library newFunctionWithName:pass ? @"hudFragment" : @"worldFragment"];
            desc.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
            desc.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
            if (pass) {
                desc.colorAttachments[0].blendingEnabled = YES;
                desc.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
                desc.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
                desc.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorOne;
                desc.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
            }
            id<MTLRenderPipelineState> pipeline = [r.device newRenderPipelineStateWithDescriptor:desc error:&error];
            if (!pipeline) { fail(env, error.localizedDescription ?: @"Metal pipeline creation failed"); return 0; }
            if (pass) r.hudPipeline = pipeline; else r.worldPipeline = pipeline;
        }
        MTLDepthStencilDescriptor* depth = [MTLDepthStencilDescriptor new];
        depth.depthCompareFunction = MTLCompareFunctionLess;
        depth.depthWriteEnabled = YES;
        r.depthState = [r.device newDepthStencilStateWithDescriptor:depth];
        depth.depthCompareFunction = MTLCompareFunctionAlways;
        depth.depthWriteEnabled = NO;
        r.hudDepthState = [r.device newDepthStencilStateWithDescriptor:depth];
        r.queue = [r.device newCommandQueue];
        if (!r.queue || !r.depthState || !r.hudDepthState) { fail(env, @"Metal resource allocation failed"); return 0; }
        r.frames = dispatch_semaphore_create(3);
        r.textures = [NSMutableArray array];
        for (int slot = 0; slot < 4; slot++) {
            id<MTLTexture> t = texture(r, MTLPixelFormatRGBA8Unorm, slot == 1 ? 4096 : 1,
                                       slot == 1 ? 6 : 1, MTLStorageModeShared, MTLTextureUsageShaderRead);
            if (!t) { fail(env, @"Metal texture allocation failed"); return 0; }
            NSMutableData* data = [NSMutableData dataWithLength:t.width * t.height * 4];
            if (slot != 1) memset(data.mutableBytes, 255, data.length);
            [t replaceRegion:MTLRegionMake2D(0, 0, t.width, t.height) mipmapLevel:0
                   withBytes:data.bytes bytesPerRow:t.width * 4];
            [r.textures addObject:t];
        }
        if (window) {
            NSWindow* nsWindow = (__bridge NSWindow*)(void*)(uintptr_t)window;
            r.view = nsWindow.contentView;
            r.previouslyWantedLayer = r.view.wantsLayer;
            r.previousLayer = r.view.layer;
            r.layer = [CAMetalLayer layer];
            r.layer.device = r.device;
            r.layer.pixelFormat = MTLPixelFormatBGRA8Unorm;
            r.layer.framebufferOnly = YES;
            r.layer.displaySyncEnabled = vsync;
            r.layer.contentsScale = nsWindow.backingScaleFactor;
            r.view.wantsLayer = YES;
            r.view.layer = r.layer;
        }
        return (jlong)(uintptr_t)CFBridgingRetain(r);
    }
}

JNIEXPORT jstring JNICALL Java_dev_voxelcraft_client_render_MetalNative_deviceName
  (JNIEnv* env, jclass cls, jlong ctx) {
    return (*env)->NewStringUTF(env, renderer(ctx).device.name.UTF8String);
}

JNIEXPORT jlong JNICALL Java_dev_voxelcraft_client_render_MetalNative_allocate
  (JNIEnv* env, jclass cls, jlong ctx, jint bytes) {
    @autoreleasepool {
        if (bytes <= 0) { fail(env, @"Invalid shared buffer size"); return 0; }
        VCBuffer* b = [VCBuffer new];
        atomic_init(&b->readers, 0);
        b.storage = [renderer(ctx).device newBufferWithLength:(NSUInteger)bytes options:MTLResourceStorageModeShared];
        if (!b.storage) { fail(env, @"Unable to allocate shared Metal buffer"); return 0; }
        return (jlong)(uintptr_t)CFBridgingRetain(b);
    }
}

JNIEXPORT jobject JNICALL Java_dev_voxelcraft_client_render_MetalNative_contents
  (JNIEnv* env, jclass cls, jlong handle) {
    VCBuffer* b = buffer(handle);
    return (*env)->NewDirectByteBuffer(env, b.storage.contents, (jlong)b.storage.length);
}
JNIEXPORT jboolean JNICALL Java_dev_voxelcraft_client_render_MetalNative_idle
  (JNIEnv* env, jclass cls, jlong handle) {
    return atomic_load(&buffer(handle)->readers) == 0;
}
JNIEXPORT void JNICALL Java_dev_voxelcraft_client_render_MetalNative_releaseBuffer
  (JNIEnv* env, jclass cls, jlong handle) {
    if (handle) CFRelease((CFTypeRef)(uintptr_t)handle);
}

JNIEXPORT void JNICALL Java_dev_voxelcraft_client_render_MetalNative_setTexture
  (JNIEnv* env, jclass cls, jlong ctx, jint slot, jint width, jint height, jobject data) {
    @autoreleasepool {
        void* bytes = (*env)->GetDirectBufferAddress(env, data);
        if (slot < 0 || slot >= 4 || width <= 0 || height <= 0 || !bytes ||
            (*env)->GetDirectBufferCapacity(env, data) < (jlong)width * height * 4) {
            fail(env, @"Invalid Metal texture data"); return;
        }
        VCRenderer* r = renderer(ctx);
        id<MTLTexture> t = texture(r, MTLPixelFormatRGBA8Unorm, width, height, MTLStorageModeShared, MTLTextureUsageShaderRead);
        if (!t) { fail(env, @"Unable to allocate Metal texture"); return; }
        [t replaceRegion:MTLRegionMake2D(0, 0, width, height) mipmapLevel:0 withBytes:bytes bytesPerRow:(NSUInteger)width * 4];
        r.textures[slot] = t;
    }
}

JNIEXPORT void JNICALL Java_dev_voxelcraft_client_render_MetalNative_render
  (JNIEnv* env, jclass cls, jlong ctx, jint width, jint height, jfloatArray values,
   jlongArray vertexHandles, jlongArray indexHandles, jintArray indexCounts,
   jlong hudHandle, jint hudWidth, jint hudHeight) {
    @autoreleasepool {
        VCRenderer* r = renderer(ctx);
        jsize count = (*env)->GetArrayLength(env, indexCounts);
        if (width <= 0 || height <= 0 || (*env)->GetArrayLength(env, values) != 20 ||
            (*env)->GetArrayLength(env, vertexHandles) != count || (*env)->GetArrayLength(env, indexHandles) != count) {
            fail(env, @"Invalid Metal frame arguments"); return;
        }
        if (r.lastCommand.status == MTLCommandBufferStatusError) {
            fail(env, r.lastCommand.error.localizedDescription ?: @"Metal GPU command failed"); return;
        }
        float uniforms[20];
        (*env)->GetFloatArrayRegion(env, values, 0, 20, uniforms);
        if ((*env)->ExceptionCheck(env)) return;
        // Construct strong references before submitting; completion owns them until every read finishes.
        NSMutableArray<VCBuffer*>* vertices = [NSMutableArray arrayWithCapacity:count];
        NSMutableArray<VCBuffer*>* indices = [NSMutableArray arrayWithCapacity:count];
        NSMutableSet<VCBuffer*>* used = [NSMutableSet set];
        NSMutableData* countsData = [NSMutableData dataWithLength:(NSUInteger)count * sizeof(jint)];
        jint* counts = countsData.mutableBytes;
        (*env)->GetIntArrayRegion(env, indexCounts, 0, count, counts);
        for (jsize i = 0; i < count; i++) {
            jlong vh, ih;
            (*env)->GetLongArrayRegion(env, vertexHandles, i, 1, &vh);
            (*env)->GetLongArrayRegion(env, indexHandles, i, 1, &ih);
            if ((*env)->ExceptionCheck(env)) return;
            VCBuffer* vb = buffer(vh); VCBuffer* ib = buffer(ih);
            if (!vb || !ib || counts[i] < 0 || (NSUInteger)counts[i] * sizeof(uint32_t) > ib.storage.length) {
                fail(env, @"Invalid Metal mesh buffer"); return;
            }
            [vertices addObject:vb]; [indices addObject:ib];
            [used addObject:vb]; [used addObject:ib];
        }
        VCBuffer* hud = hudHandle ? buffer(hudHandle) : nil;
        if (hud) {
            if (hudWidth <= 0 || hudHeight <= 0 || (uint64_t)hudWidth * hudHeight * 4 > hud.storage.length) {
                fail(env, @"Invalid Metal HUD buffer"); return;
            }
            [used addObject:hud];
        }
        dispatch_semaphore_wait(r.frames, DISPATCH_TIME_FOREVER);
        BOOL submitted = NO;
        BOOL readersRegistered = NO;
        @try {
            id<CAMetalDrawable> drawable = nil;
            id<MTLTexture> target;
            if (r.layer) {
                r.layer.contentsScale = r.view.window.backingScaleFactor;
                r.layer.drawableSize = CGSizeMake(width, height);
                drawable = [r.layer nextDrawable];
                if (!drawable) return; // Minimized or temporarily unavailable.
                target = drawable.texture;
            } else {
                if (!r.offscreen || r.offscreen.width != (NSUInteger)width || r.offscreen.height != (NSUInteger)height)
                    r.offscreen = texture(r, MTLPixelFormatBGRA8Unorm, width, height, MTLStorageModeShared, MTLTextureUsageRenderTarget);
                target = r.offscreen;
            }
            if (!r.depth || r.depth.width != (NSUInteger)width || r.depth.height != (NSUInteger)height)
                r.depth = texture(r, MTLPixelFormatDepth32Float, width, height, MTLStorageModePrivate, MTLTextureUsageRenderTarget);
            if (!target || !r.depth) { fail(env, @"Metal render target allocation failed"); return; }
            MTLRenderPassDescriptor* pass = [MTLRenderPassDescriptor renderPassDescriptor];
            pass.colorAttachments[0].texture = target;
            pass.colorAttachments[0].loadAction = MTLLoadActionClear;
            pass.colorAttachments[0].storeAction = MTLStoreActionStore;
            pass.colorAttachments[0].clearColor = MTLClearColorMake(0.31 * uniforms[19], 0.53 * uniforms[19], 0.78 * uniforms[19], 1.0);
            pass.depthAttachment.texture = r.depth;
            pass.depthAttachment.loadAction = MTLLoadActionClear;
            pass.depthAttachment.storeAction = MTLStoreActionDontCare;
            pass.depthAttachment.clearDepth = 1.0;
            id<MTLCommandBuffer> command = [r.queue commandBuffer];
            id<MTLRenderCommandEncoder> encoder = [command renderCommandEncoderWithDescriptor:pass];
            if (!command || !encoder) { fail(env, @"Unable to create Metal command encoder"); return; }
            [encoder setRenderPipelineState:r.worldPipeline];
            [encoder setDepthStencilState:r.depthState];
            [encoder setCullMode:MTLCullModeBack];
            [encoder setFrontFacingWinding:MTLWindingClockwise];
            [encoder setVertexBytes:uniforms length:sizeof(uniforms) atIndex:1];
            [encoder setFragmentBytes:uniforms length:sizeof(uniforms) atIndex:1];
            for (NSUInteger slot = 0; slot < 4; slot++) [encoder setFragmentTexture:r.textures[slot] atIndex:slot];
            for (jsize i = 0; i < count; i++) {
                [encoder setVertexBuffer:vertices[i].storage offset:0 atIndex:0];
                [encoder drawIndexedPrimitives:MTLPrimitiveTypeTriangle indexCount:counts[i]
                                    indexType:MTLIndexTypeUInt32 indexBuffer:indices[i].storage indexBufferOffset:0];
            }
            if (hud) {
                uint32_t dimensions[2] = {(uint32_t)hudWidth, (uint32_t)hudHeight};
                [encoder setRenderPipelineState:r.hudPipeline];
                [encoder setDepthStencilState:r.hudDepthState];
                [encoder setCullMode:MTLCullModeNone];
                [encoder setFragmentBuffer:hud.storage offset:0 atIndex:0];
                [encoder setFragmentBytes:dimensions length:sizeof(dimensions) atIndex:1];
                [encoder drawPrimitives:MTLPrimitiveTypeTriangle vertexStart:0 vertexCount:3];
            }
            [encoder endEncoding];
            if (drawable) [command presentDrawable:drawable];
            for (VCBuffer* b in used) atomic_fetch_add(&b->readers, 1);
            readersRegistered = YES;
            dispatch_semaphore_t semaphore = r.frames;
            [command addCompletedHandler:^(id<MTLCommandBuffer> finished) {
                for (VCBuffer* b in used) atomic_fetch_sub(&b->readers, 1);
                dispatch_semaphore_signal(semaphore);
            }];
            r.lastCommand = command;
            [command commit];
            submitted = YES;
        } @catch (NSException* error) {
            fail(env, error.reason ?: @"Metal frame encoding failed");
        } @finally {
            if (!submitted) {
                if (readersRegistered) for (VCBuffer* b in used) atomic_fetch_sub(&b->readers, 1);
                dispatch_semaphore_signal(r.frames);
            }
        }
    }
}

JNIEXPORT void JNICALL Java_dev_voxelcraft_client_render_MetalNative_waitIdle
  (JNIEnv* env, jclass cls, jlong ctx) {
    VCRenderer* r = renderer(ctx);
    [r.lastCommand waitUntilCompleted];
    if (r.lastCommand.status == MTLCommandBufferStatusError)
        fail(env, r.lastCommand.error.localizedDescription ?: @"Metal command failed");
}
JNIEXPORT void JNICALL Java_dev_voxelcraft_client_render_MetalNative_readPixels
  (JNIEnv* env, jclass cls, jlong ctx, jobject destination) {
    VCRenderer* r = renderer(ctx);
    Java_dev_voxelcraft_client_render_MetalNative_waitIdle(env, cls, ctx);
    if ((*env)->ExceptionCheck(env)) return;
    void* pixels = (*env)->GetDirectBufferAddress(env, destination);
    if (!r.offscreen || !pixels || (*env)->GetDirectBufferCapacity(env, destination) < (jlong)(r.offscreen.width * r.offscreen.height * 4)) {
        fail(env, @"Readback requires an offscreen frame and a correctly sized direct buffer"); return;
    }
    [r.offscreen getBytes:pixels bytesPerRow:r.offscreen.width * 4
               fromRegion:MTLRegionMake2D(0, 0, r.offscreen.width, r.offscreen.height) mipmapLevel:0];
}
JNIEXPORT void JNICALL Java_dev_voxelcraft_client_render_MetalNative_destroy
  (JNIEnv* env, jclass cls, jlong ctx) {
    @autoreleasepool {
        VCRenderer* r = CFBridgingRelease((void*)(uintptr_t)ctx);
        [r.lastCommand waitUntilCompleted];
        if (r.view) {
            r.view.layer = r.previousLayer;
            r.view.wantsLayer = r.previouslyWantedLayer;
        }
    }
}
