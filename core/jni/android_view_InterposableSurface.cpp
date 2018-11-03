#define LOG_TAG "Dalf"

#include <stdio.h>
#include <gui/BufferItemConsumer.h>
#include <gui/Surface.h>
#include <gui/BufferQueue.h>
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
    InterposableSurface(JNIEnv* env, jobject thiz, int streamId,
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


    const int                   mStreamId;
    const sp<Surface>           mDestinationSurface;

    JNIEnv*                     mEnv;
    jobject                     mThiz;

    bool                        mInitialized = false;

    sp<IGraphicBufferProducer>  mSourceProducer;
    sp<IGraphicBufferConsumer>  mSourceConsumer;
    sp<Surface>                 mSourceSurface;

    sp<IGraphicBufferProducer>  mDestinationProducer;

    sp<BufferItemConsumer>      mBufferItemConsumer;

    BufferItem*                 mBufferItem;
};

InterposableSurface::InterposableSurface(JNIEnv* env, jobject thiz, int streamId,
        const sp<Surface>& destinationSurface)
    :
      mStreamId(streamId),
      mDestinationSurface(destinationSurface),
      mEnv(env),
      mThiz(env->NewGlobalRef(thiz))
{
    mInitialized = false;
}

void InterposableSurface::disconnect() {
    if (mInitialized) {
        mDestinationSurface->disconnect(NATIVE_WINDOW_API_CAMERA);
        mBufferItemConsumer->setFrameAvailableListener(0);
        mBufferItemConsumer->abandon();
        delete mBufferItem;
        mInitialized = false;
    }
    mEnv->DeleteGlobalRef(mThiz);
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

    mBufferItem = new BufferItem;

    wp<FrameAvailableListener> listener = this;
    mBufferItemConsumer->setFrameAvailableListener(listener);

    mInitialized = true;
    return true;
}



void InterposableSurface::onFrameAvailable(const BufferItem& item)
{
    if (!mInitialized) {
        LOG_ERROR_DALF("Unexpectedly receiving frames for stream %d", mStreamId);
        return;
    }

    status_t res = mBufferItemConsumer->acquireBuffer(mBufferItem, 0, false);
    if (res != OK) {
        LOG_ERROR_DALF("InterposableSurface could not acquire buffer in onFrameAvailable."
                       " Failed with error %d", res);
        return;
    }

    uint8_t* imgData = NULL;
    sp<GraphicBuffer> buf = mBufferItem->mGraphicBuffer;
    res = buf->lock(GraphicBuffer::USAGE_SW_READ_OFTEN | GraphicBuffer::USAGE_SW_WRITE_OFTEN,
                (void**)&imgData);

    if (res != OK) {
        LOG_ERROR_DALF("InterposableSurface could not lock buffer in onFrameAvailable."
                       " Failed with error %d", res);
        return;
    }

    mEnv->CallVoidMethod(mThiz, javaClassInfo.onFrameAvailable,
        (jint)buf->getStride(), (jlong)imgData);

    res = buf->unlock();
    imgData = NULL;
    if (res != OK) {
        LOG_ERROR_DALF("InterposableSurface could not unlock buffer in onFrameAvailable."
                       " Failed with error %d", res);
        return;
    }

    IGraphicBufferProducer::QueueBufferInput queueInput(
        mBufferItem->mTimestamp, mBufferItem->mIsAutoTimestamp,
        mBufferItem->mDataSpace, mBufferItem->mCrop,
        static_cast<int32_t>(mBufferItem->mScalingMode),
        mBufferItem->mTransform, mBufferItem->mFence);
    IGraphicBufferProducer::QueueBufferOutput queueOutput;

    int slot = -1;
    res = mDestinationProducer->attachBuffer(&slot, mBufferItem->mGraphicBuffer);
    if (res != OK) {
        LOG_ERROR_DALF("InterposableSurface could not attach buffer to destination producer."
                       " Failed with error %d", res);
        return;
    }

    res = mDestinationProducer->queueBuffer(slot, queueInput, &queueOutput);
    if (res != OK) {
        LOG_ERROR_DALF("InterposableSurface could not queue buffer to destination producer."
                       " Failed with error %d", res);
    }

    mBufferItemConsumer->releaseBuffer(*mBufferItem);
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

static jboolean InterposableSurface_init(JNIEnv* env, jobject thiz, jint streamId, jobject destinationSurface)
{
    sp<Surface> cDestinationSurface = android_view_Surface_getSurface(env, destinationSurface);
    int cStreamId = (int) streamId;
    sp<InterposableSurface> ctx(new InterposableSurface(env, thiz, cStreamId, cDestinationSurface));
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
    {"nativeInit", "(ILandroid/view/Surface;)Z",
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
