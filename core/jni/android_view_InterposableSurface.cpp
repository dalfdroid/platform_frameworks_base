#define LOG_TAG "Dalf"

#include <stdio.h>

#include <android_runtime/AndroidRuntime.h>
#include <gui/BufferItemConsumer.h>
#include <gui/Surface.h>
#include <gui/BufferQueue.h>
#include <gui/BufferQueueDefs.h>
#include <gui/IProducerListener.h>
#include "core_jni_helpers.h"

#include <cutils/atomic.h>
#include <utils/Log.h>
#include <utils/misc.h>

#include <android_runtime/android_view_Surface.h>

#include "jni.h"
#include <nativehelper/JNIHelp.h>
#include "ScopedLocalRef.h"

#ifdef DEBUG_DALF
#define LOG_DEBUG_DALF(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOG_DEBUG_DALF(...) ((void)0)
#endif

#define LOG_ERROR_DALF(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALIGN(x, mask) ( ((x) + (mask) - 1) & ~((mask) - 1) )

// ----------------------------------------------------------------------------
namespace android {

const char* const kInterposableSurfaceClassPathName = "android/view/InterposableSurface";

static struct {
    jfieldID    mNativeContext;
    jmethodID   onFrameAvailable;
} javaClassInfo;

// ----------------------------------------------------------------------------

class InterposableSurface : public ConsumerBase::FrameAvailableListener,
                            public BnProducerListener
{
public:
    InterposableSurface(jobject interposableSurfaceObj, int streamId,
            const sp<Surface>& targetSurface);

    sp<Surface>   getSourceSurface() { return mSourceSurface; }

    bool          initialize();
    bool          isInitialized() { return (mInitialized == 1); }

    void          disconnect();

private:

    // Part of ConsumerBase::onFrameAvailable
    virtual void onFrameAvailable(const BufferItem& item);

    // Implementation of IProducerListener, used to notify the producer that the
    // consumer has returned a buffer and it is ready for the producer to
    // dequeue.
    virtual void onBufferReleased();

    void freeDestBuffers();

    bool getDestBuffer(sp<GraphicBuffer>& sourceBuffer, BufferItem *dest);
    bool copyFrame(sp<GraphicBuffer>& sourceBuffer, uint8_t *destData);
    void sendToPlugin(sp<GraphicBuffer>& destBuffer, uint8_t *destData);
    void sendToDestination(BufferItem* item);

    enum { NUM_BUFFER_SLOTS = BufferQueueDefs::NUM_BUFFER_SLOTS };

    const int                   mStreamId;
    const sp<Surface>           mDestinationSurface;

    jobject                     mInterposableSurfaceObj;

    bool                        mInitialized = false;

    sp<IGraphicBufferProducer>  mSourceProducer;
    sp<IGraphicBufferConsumer>  mSourceConsumer;
    sp<Surface>                 mSourceSurface;

    sp<IGraphicBufferProducer>  mDestinationProducer;

    sp<BufferItemConsumer>      mBufferItemConsumer;
    sp<GraphicBuffer>           mSlots[NUM_BUFFER_SLOTS];
};

InterposableSurface::InterposableSurface(jobject interposableSurfaceObj, int streamId,
        const sp<Surface>& destinationSurface)
    :
      mStreamId(streamId),
      mDestinationSurface(destinationSurface)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    mInterposableSurfaceObj = env->NewGlobalRef(interposableSurfaceObj);
    mInitialized = false;
}

void InterposableSurface::disconnect() {
    if (mInitialized) {
        mDestinationSurface->disconnect(NATIVE_WINDOW_API_CAMERA);
        mBufferItemConsumer->setFrameAvailableListener(0);
        mBufferItemConsumer->abandon();
        mInitialized = false;
    }

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    env->DeleteGlobalRef(mInterposableSurfaceObj);
}

bool InterposableSurface::initialize() {

    if (mInitialized) {
        return true;
    }
    status_t res = mDestinationSurface->connect(NATIVE_WINDOW_API_CAMERA, this);
    if (res != OK) {
        LOG_ERROR_DALF("Could not connect to destination surface for stream %d!",
                       mStreamId);
        return false;
    }
    mDestinationProducer = mDestinationSurface->getIGraphicBufferProducer();

    BufferQueue::createBufferQueue(&mSourceProducer, &mSourceConsumer);
    sp<Surface> sourceSurface(new Surface(mSourceProducer, /** controlledByApp */ true));
    mSourceSurface = sourceSurface;
    mBufferItemConsumer = new BufferItemConsumer(mSourceConsumer, 1,
         GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN,
         /** controlledByApp */ true);

    wp<FrameAvailableListener> listener = this;
    mBufferItemConsumer->setFrameAvailableListener(listener);

    mInitialized = true;
    return true;
}

void InterposableSurface::freeDestBuffers()
{
    for (int i = 0; i < NUM_BUFFER_SLOTS; i++) {
        mSlots[i] = 0;
    }
}

bool InterposableSurface::getDestBuffer(sp<GraphicBuffer>& sourceBuffer, BufferItem *destItem)
{
    int slot = 0;
    uint64_t bufferAge = 0;
    sp<Fence> fence;
    status_t res = mDestinationProducer->dequeueBuffer(&slot, &fence, sourceBuffer->getWidth(),
         sourceBuffer->getHeight(), sourceBuffer->getPixelFormat(), sourceBuffer->getUsage(),
         &bufferAge, nullptr);

    if (res < 0) {
        LOG_ERROR_DALF("%s: InterposableSurface could not get dequeue destination buffer."
                       " Failed with error %d", __func__, res);
        return false;
    }

    sp<GraphicBuffer>& destBuffer(mSlots[slot]);
    if (res & IGraphicBufferProducer::RELEASE_ALL_BUFFERS) {
        freeDestBuffers();
    }

    if ((res & IGraphicBufferProducer::BUFFER_NEEDS_REALLOCATION) || destBuffer == nullptr) {
        res = mDestinationProducer->requestBuffer(slot, &destBuffer);
        if (res != NO_ERROR) {
            LOG_ERROR_DALF("%s: InterposableSurface could not request destination buffer."
                           " Failed with error %d", __func__, res);
            mDestinationProducer->cancelBuffer(slot, fence);
            return false;
        }
        mSlots[slot] = destBuffer;
    }

    destItem->mSlot = slot;
    destItem->mFence = fence;
    destItem->mGraphicBuffer = destBuffer;

    return true;
}

bool InterposableSurface::copyFrame(sp<GraphicBuffer>& sourceBuffer, uint8_t *destData)
{
    uint8_t* srcData = NULL;
    status_t res = sourceBuffer->lock(GraphicBuffer::USAGE_SW_READ_OFTEN, (void**)&srcData);
    if (res != OK) {
        LOG_ERROR_DALF("%s: InterposableSurface could not lock source buffer."
                       " Failed with error %d", __func__, res);
        return false;
    }

    int stride = sourceBuffer->getStride();
    int height = sourceBuffer->getHeight();
    int alignedHeight = ALIGN(height/2, 32) * 2;

    if (sourceBuffer->getPixelFormat() == HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED) {
        memcpy(destData, srcData, stride * alignedHeight * 1.5);
    } else {
        memcpy(destData, srcData, stride * height);
    }

    sourceBuffer->unlock();
    return true;
}

void InterposableSurface::sendToPlugin(sp<GraphicBuffer>& destBuffer, uint8_t *destData)
{
    JNIEnv* env = AndroidRuntime::getJNIEnv();
    ScopedLocalRef<jobject> interposableSurfaceObj(env, jniGetReferent(env, mInterposableSurfaceObj));
    env->CallVoidMethod(interposableSurfaceObj.get(), javaClassInfo.onFrameAvailable,
        static_cast<jint>(destBuffer->getStride()), reinterpret_cast<jlong>(destData));
}

void InterposableSurface::sendToDestination(BufferItem *item)
{
    status_t res = OK;

    IGraphicBufferProducer::QueueBufferInput queueInput(
        item->mTimestamp, item->mIsAutoTimestamp,
        item->mDataSpace, item->mCrop,
        static_cast<int32_t>(item->mScalingMode),
        item->mTransform, item->mFence);
    IGraphicBufferProducer::QueueBufferOutput queueOutput;

    res = mDestinationProducer->queueBuffer(item->mSlot, queueInput, &queueOutput);
    if (res != OK) {
        LOG_ERROR_DALF("%s: InterposableSurface could not queue destination buffer."
                       " Failed with error %d", __func__, res);
        mDestinationProducer->cancelBuffer(item->mSlot, item->mFence);
    }
}

void InterposableSurface::onFrameAvailable(const BufferItem& item)
{
    if (!mInitialized) {
        LOG_ERROR_DALF("Unexpectedly receiving frames for stream %d", mStreamId);
        return;
    }

    BufferItem srcItem;
    status_t res = mBufferItemConsumer->acquireBuffer(&srcItem, 0, false);
    if (res != OK) {
        LOG_ERROR_DALF("%s: InterposableSurface could not acquire incoming buffer."
                       " Failed with error %d", __func__, res);
        return;
    }

    sp<GraphicBuffer>& sourceBuffer = srcItem.mGraphicBuffer;
    BufferItem destItem = srcItem;

    if (getDestBuffer(sourceBuffer, &destItem)) {
        sp<GraphicBuffer> destBuffer = destItem.mGraphicBuffer;
        uint8_t* destData = NULL;

        res = destBuffer->lock(
            GraphicBuffer::USAGE_SW_READ_OFTEN | GraphicBuffer::USAGE_SW_WRITE_OFTEN,
            (void**)&destData);

        if (res == OK) {
            if (copyFrame(sourceBuffer, destData)) {
                sendToPlugin(destBuffer, destData);
                destBuffer->unlock();

                sendToDestination(&destItem);
            } else {
                destBuffer->unlock();
                mDestinationProducer->cancelBuffer(destItem.mSlot, destItem.mFence);
            }
        } else {
            mDestinationProducer->cancelBuffer(destItem.mSlot, destItem.mFence);
        }
    }

    mBufferItemConsumer->releaseBuffer(srcItem);
}

void InterposableSurface::onBufferReleased()
{
    /** Nothing to do here at the moment. */
}

// ----------------------------------------------------------------------------

extern "C" {

#define ANDROID_VIEW_INTERPOSABLESURFACE_CTX_JNI_ID "mNativeContext"
#define ANDROID_VIEW_INTERPOSABLESURFACE_ONFRAMEAVAILABLE "onFrameAvailable"

static void InterposableSurface_classInit(JNIEnv* env, jclass clazz)
{
    javaClassInfo.mNativeContext = env->GetFieldID(clazz,
            ANDROID_VIEW_INTERPOSABLESURFACE_CTX_JNI_ID, "J");

    if (javaClassInfo.mNativeContext == NULL) {
        LOG_ERROR_DALF("can't find android/view/InterposableSurface.%s",
                       ANDROID_VIEW_INTERPOSABLESURFACE_CTX_JNI_ID);
        return;
    }

    javaClassInfo.onFrameAvailable = env->GetMethodID(clazz,
            ANDROID_VIEW_INTERPOSABLESURFACE_ONFRAMEAVAILABLE, "(IJ)V");

    if (javaClassInfo.onFrameAvailable == NULL) {
        LOG_ERROR_DALF("can't find android/view/InterposableSurface.%s",
                       ANDROID_VIEW_INTERPOSABLESURFACE_ONFRAMEAVAILABLE);
        return;
    }
}

static InterposableSurface* InterposableSurface_getContext(JNIEnv* env, jobject thiz)
{
    InterposableSurface *ctx;
    ctx = reinterpret_cast<InterposableSurface *>
        (env->GetLongField(thiz, javaClassInfo.mNativeContext));
    return ctx;
}

static void InterposableSurface_setContext(JNIEnv* env, jobject thiz,
        sp<InterposableSurface> ctx)
{
    InterposableSurface* const p = InterposableSurface_getContext(env, thiz);

    if (p) {
        p->decStrong((void*)InterposableSurface_setContext);
    }

    if ( ctx != 0) {
        ctx->incStrong((void*)InterposableSurface_setContext);
        env->SetLongField(thiz, javaClassInfo.mNativeContext, reinterpret_cast<jlong>(ctx.get()));
    } else {
        env->SetLongField(thiz, javaClassInfo.mNativeContext, (jlong)0);
    }
}

static jboolean InterposableSurface_init(JNIEnv* env, jobject thiz, jobject weakThiz, jint streamId, jobject destinationSurface)
{
    sp<Surface> cDestinationSurface = android_view_Surface_getSurface(env, destinationSurface);
    int cStreamId = (int) streamId;
    sp<InterposableSurface> ctx(new InterposableSurface(weakThiz, cStreamId, cDestinationSurface));
    InterposableSurface_setContext(env, thiz, ctx);

    ctx->initialize();
    if (!ctx->isInitialized()) {
        InterposableSurface_setContext(env, thiz, NULL);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

static jobject InterposableSurface_getSourceSurface(JNIEnv* env, jobject thiz)
{
    InterposableSurface *ctx = InterposableSurface_getContext(env, thiz);
    if (ctx == NULL) {
        return NULL;
    }

    const sp<Surface>& sourceSurface = ctx->getSourceSurface();
    return android_view_Surface_createFromSurface(env, sourceSurface);
}

static void InterposableSurface_close(JNIEnv* env, jobject thiz)
{
    InterposableSurface *ctx = InterposableSurface_getContext(env, thiz);
    if (ctx == NULL) {
        return;
    }
    ctx->disconnect();
    InterposableSurface_setContext(env, thiz, 0);
}

} // extern "C"

static const JNINativeMethod gMethods[] = {
    {"nativeInit", "(Ljava/lang/ref/WeakReference;ILandroid/view/Surface;)Z",
             (void*)InterposableSurface_init},
    {"nativeGetSourceSurface", "()Landroid/view/Surface;",
             (void*)InterposableSurface_getSourceSurface},
    {"nativeClose", "()V",
             (void*)InterposableSurface_close},
};

int register_android_view_InterposableSurface(JNIEnv *env)
{
    ScopedLocalRef<jclass> klass(env, FindClassOrDie(env, kInterposableSurfaceClassPathName));
    InterposableSurface_classInit(env, klass.get());

    return AndroidRuntime::registerNativeMethods(env,
            "android/view/InterposableSurface", gMethods, NELEM(gMethods));
}

}; // namespace "android"
