/*
 * UVCCamera
 * library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 * File name: UVCPreview.cpp
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
 * Files in the jni/libjpeg, jni/libusb, jin/libuvc, jni/rapidjson folder may have a different license, see the respective files.
*/

#include <cstdlib>
#include <linux/time.h>
#include <unistd.h>

#if 1	// set 1 if you don't need debug log
	#ifndef LOG_NDEBUG
		#define	LOG_NDEBUG		// w/o LOGV/LOGD/MARK
	#endif
	#undef USE_LOGALL
#else
	#define USE_LOGALL
	#undef LOG_NDEBUG
//	#undef NDEBUG
#endif

#include "utilbase.h"
#include "UVCPreview.h"
#include "libuvc_internal.h"

#define	LOCAL_DEBUG 0
#define MAX_FRAME 1 // previewFrames buffer size. Less value - less camera latency, but can drop the frames
#define FRAME_POOL_SZ (MAX_FRAME + 2)

static int previewFormatPixelBytes = 4;

UVCPreview::UVCPreview(uvc_device_handle_t *devh)
:	mPreviewWindow(NULL),
	mCaptureWindow(NULL),
	mDeviceHandle(devh),
	requestWidth(DEFAULT_PREVIEW_WIDTH),
	requestHeight(DEFAULT_PREVIEW_HEIGHT),
	requestMinFps(DEFAULT_PREVIEW_FPS_MIN),
	requestMaxFps(DEFAULT_PREVIEW_FPS_MAX),
	defaultCameraFps(DEFAULT_PREVIEW_FPS_MAX),
	requestMode(DEFAULT_PREVIEW_MODE),
	frameWidth(DEFAULT_PREVIEW_WIDTH),
	frameHeight(DEFAULT_PREVIEW_HEIGHT),
	frameRotationAngle(DEFAULT_FRAME_ROTATION_ANGLE),
	frameHorizontalMirror(0),
	frameVerticalMirror(0),
	rotateImage(NULL),
	frameBytes(DEFAULT_PREVIEW_WIDTH * DEFAULT_PREVIEW_HEIGHT * 2),	// YUYV
	previewFormat(AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM),
	mIsRunning(false),
	mIsCapturing(false),
	captureQueu(NULL),
	// Frame callback Java object
	mFrameCallbackObj(NULL),
    // Pixel format conversion method
	mFrameCallbackFunc(NULL),
    currentFps(0),
    framesCounter(0),
	callbackPixelBytes(2) {

	ENTER();
	pthread_cond_init(&preview_sync, NULL);
	pthread_mutex_init(&preview_mutex, NULL);
//
	pthread_cond_init(&capture_sync, NULL);
	pthread_mutex_init(&capture_mutex, NULL);
//	
	pthread_mutex_init(&pool_mutex, NULL);

	EXIT();
}

UVCPreview::~UVCPreview() {

	ENTER();
	if(rotateImage){
	    SAFE_DELETE(rotateImage);
    }
	if (mPreviewWindow) ANativeWindow_release(mPreviewWindow);
	mPreviewWindow = NULL;
	if (mCaptureWindow) ANativeWindow_release(mCaptureWindow);
	mCaptureWindow = NULL;
	clearPreviewFrame();
	clearCaptureFrame();
	clear_pool();
	pthread_mutex_destroy(&preview_mutex);
	pthread_cond_destroy(&preview_sync);
	pthread_mutex_destroy(&capture_mutex);
	pthread_cond_destroy(&capture_sync);
	pthread_mutex_destroy(&pool_mutex);
	EXIT();
}

/**
 * get uvc_frame_t from frame pool
 * if pool is empty, create new frame
 * this function does not confirm the frame size
 * and you may need to confirm the size
 */
uvc_frame_t *UVCPreview::get_frame(size_t data_bytes) {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&pool_mutex);
	{
		if (!mFramePool.isEmpty()) {
			frame = mFramePool.last();
			if(frame->data_bytes < data_bytes){
			    mFramePool.put(frame);
			    frame = NULL;
			}
		}
	}
	pthread_mutex_unlock(&pool_mutex);
	if UNLIKELY(!frame) {
		//LOGW("allocate new frame");
		// Open up frame data memory
		frame = uvc_allocate_frame(data_bytes);
	}
	return frame;
}

/**
 * Put the frame back into the frame pool
 */
void UVCPreview::recycle_frame(uvc_frame_t *frame) {
	pthread_mutex_lock(&pool_mutex);
	// If the current frame pool is smaller than the maximum size, it is put back into the frame pool, otherwise it is destroyed.
	if (LIKELY(mFramePool.size() < FRAME_POOL_SZ)) {
		mFramePool.put(frame);
		frame = NULL;
	}
	pthread_mutex_unlock(&pool_mutex);
	if (UNLIKELY(frame)) {
	    // Release frame data memory
		uvc_free_frame(frame);
	}
}

/**
 * Initialize frame pool
 */
void UVCPreview::init_pool(size_t data_bytes) {
	ENTER();

	clear_pool();
	pthread_mutex_lock(&pool_mutex);
	{
		for (int i = 0; i < FRAME_POOL_SZ; i++) {
			mFramePool.put(uvc_allocate_frame(data_bytes));
		}
	}
	pthread_mutex_unlock(&pool_mutex);

	EXIT();
}

/**
 * clear frame pool
 */
void UVCPreview::clear_pool() {
	ENTER();

	pthread_mutex_lock(&pool_mutex);
	{
		const int n = mFramePool.size();
		for (int i = 0; i < n; i++) {
		    // Release frame data memory
			uvc_free_frame(mFramePool[i]);
		}
		mFramePool.clear();
	}
	pthread_mutex_unlock(&pool_mutex);
	EXIT();
}

// Set preview parameters
int UVCPreview::setPreviewSize(int width, int height, int cameraAngle, int min_fps, int max_fps, int mode) {
	ENTER();
	
	int result = 0;
	if ((requestWidth != width) || (requestHeight != height) || (requestMode != mode)) {
		requestWidth = width;
		requestHeight = height;
		requestMinFps = min_fps;
		requestMaxFps = max_fps;
		requestMode = mode;

		uvc_stream_ctrl_t ctrl;
		result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, &ctrl,
			!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
			requestWidth, requestHeight, requestMinFps, requestMaxFps);
	}

	// Calculate the angle at which the image frame needs to be rotated based on the camera angle
	frameRotationAngle = (360 - cameraAngle) % 360;
	LOGW("frameRotationAngle:%d",frameRotationAngle);
	if( (frameHorizontalMirror || frameVerticalMirror || frameRotationAngle) && !rotateImage) {
		rotateImage = new RotateImage();
	}
	
	RETURN(result, int);
}

// Set preview display
int UVCPreview::setPreviewDisplay(ANativeWindow *preview_window) {
	ENTER();
	pthread_mutex_lock(&preview_mutex);
	{
		if (mPreviewWindow != preview_window) {
			if (mPreviewWindow)
				ANativeWindow_release(mPreviewWindow);
			mPreviewWindow = preview_window;
			if (LIKELY(mPreviewWindow)) {
                previewFormat = ANativeWindow_getFormat(mPreviewWindow);
                LOGI("previewFormat: %d", previewFormat);
                switch (previewFormat){
                    case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
                    case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM:
                        previewFormatPixelBytes = 4;
                        break;
                    case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM:
                        previewFormatPixelBytes = 2;
                        break;
                    case 34:
                        previewFormat = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
                        previewFormatPixelBytes = 4;
                        break;
                    default:
                        previewFormat = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
                        previewFormatPixelBytes = 4;
                }
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	RETURN(0, int);
}

// Set frame callback
int UVCPreview::setFrameCallback(JNIEnv *env, jobject frame_callback_obj, int pixel_format) {
	
	ENTER();
	pthread_mutex_lock(&capture_mutex);
	{
		if (isRunning() && isCapturing()) {
			mIsCapturing = false;
			if (mFrameCallbackObj) {
				pthread_cond_signal(&capture_sync);
				pthread_cond_wait(&capture_sync, &capture_mutex);	// wait finishing capturing
			}
		}
		if (!env->IsSameObject(mFrameCallbackObj, frame_callback_obj))	{
			iframecallback_fields.onFrame = NULL;
			if (mFrameCallbackObj) {
				env->DeleteGlobalRef(mFrameCallbackObj);
			}
			mFrameCallbackObj = frame_callback_obj;
			if (frame_callback_obj) {
				// get method IDs of Java object for callback
				jclass clazz = env->GetObjectClass(frame_callback_obj);
				if (LIKELY(clazz)) {
					iframecallback_fields.onFrame = env->GetMethodID(clazz,
						"onFrame",	"(Ljava/nio/ByteBuffer;)V");
				} else {
					LOGW("failed to get object class");
				}
				env->ExceptionClear();
				if (!iframecallback_fields.onFrame) {
					LOGE("Can't find IFrameCallback#onFrame");
					env->DeleteGlobalRef(frame_callback_obj);
					mFrameCallbackObj = frame_callback_obj = NULL;
				}
			}
		}
		if (frame_callback_obj) {
			mPixelFormat = pixel_format;
			callbackPixelFormatChanged();
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	RETURN(0, int);
}

// Pixel format conversion
void UVCPreview::callbackPixelFormatChanged() {
	mFrameCallbackFunc = NULL;
	const size_t sz = requestWidth * requestHeight;
	// Frame callback pixel format
	switch (mPixelFormat) {
	  case PIXEL_FORMAT_RAW:
		LOGI("PIXEL_FORMAT_RAW:");
		callbackPixelBytes = sz * 2;
		break;
	  case PIXEL_FORMAT_YUV:
		LOGI("PIXEL_FORMAT_YUV:");
		callbackPixelBytes = sz * 2;
		break;
	  case PIXEL_FORMAT_RGBX:
		LOGI("PIXEL_FORMAT_RGBX:");
		mFrameCallbackFunc = uvc_any2rgbx;
		callbackPixelBytes = sz * 4;
		break;
	}
}

// clear display
void UVCPreview::clearDisplay() {
	ENTER();

	ANativeWindow_Buffer buffer;
	pthread_mutex_lock(&capture_mutex);
	{
		if (LIKELY(mCaptureWindow)) {
			if (LIKELY(ANativeWindow_lock(mCaptureWindow, &buffer, NULL) == 0)) {
				uint8_t *dest = (uint8_t *)buffer.bits;
				const size_t bytes = buffer.width * previewFormatPixelBytes;
				const int stride = buffer.stride * previewFormatPixelBytes;
				for (int i = 0; i < buffer.height; i++) {
					memset(dest, 0, bytes);
					dest += stride;
				}
				ANativeWindow_unlockAndPost(mCaptureWindow);
			}
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	pthread_mutex_lock(&preview_mutex);
	{
		if (LIKELY(mPreviewWindow)) {
			if (LIKELY(ANativeWindow_lock(mPreviewWindow, &buffer, NULL) == 0)) {
				uint8_t *dest = (uint8_t *)buffer.bits;
				const size_t bytes = buffer.width * previewFormatPixelBytes;
				const int stride = buffer.stride * previewFormatPixelBytes;
				for (int i = 0; i < buffer.height; i++) {
					memset(dest, 0, bytes);
					dest += stride;
				}
				ANativeWindow_unlockAndPost(mPreviewWindow);
			}
		}
	}
	pthread_mutex_unlock(&preview_mutex);

	EXIT();
}

// Start preview
int UVCPreview::startPreview() {
	ENTER();

	int result = EXIT_FAILURE;
	if (!isRunning()) {
        mPreviewConvertFunc = nullptr;
		pthread_mutex_lock(&preview_mutex);
		{
			if (LIKELY(mPreviewWindow)) {
			    // Create a thread to execute preview_thread_func
				result = pthread_create(&preview_thread, NULL, preview_thread_func, (void *)this);
			}
		}
		pthread_mutex_unlock(&preview_mutex);
		if (UNLIKELY(result != EXIT_SUCCESS)) {
			LOGW("UVCCamera::window does not exist/already running/could not create thread etc.");
			mIsRunning = false;
			pthread_mutex_lock(&preview_mutex);
			{
				pthread_cond_signal(&preview_sync);
			}
			pthread_mutex_unlock(&preview_mutex);
		}
	}
	RETURN(result, int);
}

// Stop preview
int UVCPreview::stopPreview() {
	ENTER();
	bool b = isRunning();
	if (LIKELY(b)) {
		mIsRunning = false;
        mPreviewConvertFunc = nullptr;
		pthread_cond_signal(&preview_sync);
		pthread_cond_signal(&capture_sync);
		if (mIsCapturing && pthread_join(capture_thread, NULL) != EXIT_SUCCESS) {
			LOGW("UVCPreview::terminate capture thread: pthread_join failed");
		}
		if (pthread_join(preview_thread, NULL) != EXIT_SUCCESS) {
			LOGW("UVCPreview::terminate preview thread: pthread_join failed");
		}
		clearDisplay();
	}
	clearPreviewFrame();
	clearCaptureFrame();
	pthread_mutex_lock(&preview_mutex);
	if (mPreviewWindow) {
		ANativeWindow_release(mPreviewWindow);
		mPreviewWindow = NULL;
	}
	pthread_mutex_unlock(&preview_mutex);
	pthread_mutex_lock(&capture_mutex);
	if (mCaptureWindow) {
		ANativeWindow_release(mCaptureWindow);
		mCaptureWindow = NULL;
	}
	pthread_mutex_unlock(&capture_mutex);
	RETURN(0, int);
}

//**********************************************************************
// UVC preview frame callback
//**********************************************************************
void UVCPreview::uvc_preview_frame_callback(uvc_frame_t *frame, void *vptr_args) {
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if UNLIKELY(!preview->isRunning() || !frame || !frame->frame_format || !frame->data || !frame->data_bytes) return;
	if (UNLIKELY(frame->width != preview->frameWidth || frame->height != preview->frameHeight)) {

#if LOCAL_DEBUG
		LOGI("broken frame!:format=%d,frameBytes=%zu(%d,%d/%d,%d)",
			frame->frame_format, preview->frameBytes,
			frame->width, frame->height, preview->frameWidth, preview->frameHeight);
#endif
		return;
	}
	if (LIKELY(preview->isRunning())) {
	    // Get frames from frame pool
		uvc_frame_t *copy = preview->get_frame(frame->data_bytes);
		if (UNLIKELY(!copy)) {
#if LOCAL_DEBUG
			LOGE("uvc_callback:unable to allocate duplicate frame!");
#endif
			return;
		}
		// Duplicate frames, preserving color formatting
		uvc_error_t ret = uvc_duplicate_frame(frame, copy);
		if (UNLIKELY(ret)) {
		    // put back into frame pool
			preview->recycle_frame(copy);
			return;
		}
		// Add preview frame
		preview->addPreviewFrame(copy);
	}
}

// Add preview frame
void UVCPreview::addPreviewFrame(uvc_frame_t *frame) {
	pthread_mutex_lock(&preview_mutex);
	if (isRunning() && (previewFrames.size() < MAX_FRAME)) {
	    // Add to preview array
		previewFrames.put(frame);
		frame = NULL;
		// Send a signal to another thread that is in a blocking waiting state to get it out of the blocking state and continue execution.
		pthread_cond_signal(&preview_sync);
	}
	pthread_mutex_unlock(&preview_mutex);
	if (frame) {
		// put back into frame pool
		recycle_frame(frame);
	}
}

// Wait for preview frame
uvc_frame_t *UVCPreview::waitPreviewFrame() {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&preview_mutex);
	{
		if (!previewFrames.size()) {
            // Wait for preview_sync, unlock preview_mutex
			pthread_cond_wait(&preview_sync, &preview_mutex);
		}
		if (LIKELY(isRunning() && previewFrames.size() > 0)) {
			frame = previewFrames.remove(0);
		}
	}
	pthread_mutex_unlock(&preview_mutex);
	return frame;
}

// Clear preview frame
void UVCPreview::clearPreviewFrame() {
	pthread_mutex_lock(&preview_mutex);
	{
		for (int i = 0; i < previewFrames.size(); i++)
		    // put back into frame pool
			recycle_frame(previewFrames[i]);
		previewFrames.clear();
	}
	pthread_mutex_unlock(&preview_mutex);
}

// preview thread
void *UVCPreview::preview_thread_func(void *vptr_args) {
	int result;

	ENTER();
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if (LIKELY(preview)) {
		uvc_stream_ctrl_t ctrl;
		result = preview->prepare_preview(&ctrl);
		if (LIKELY(!result)) {
			preview->do_preview(&ctrl);
		}
	}
	PRE_EXIT();
	pthread_exit(NULL);
}

// Prepare to preview
int UVCPreview::prepare_preview(uvc_stream_ctrl_t *ctrl) {
	uvc_error_t result;

	ENTER();
	result = uvc_get_stream_ctrl_format_size_fps(mDeviceHandle, ctrl,
		!requestMode ? UVC_FRAME_FORMAT_YUYV : UVC_FRAME_FORMAT_MJPEG,
		requestWidth, requestHeight, requestMinFps, requestMaxFps
	);
	if (LIKELY(!result)) {
#if LOCAL_DEBUG
		uvc_print_stream_ctrl(ctrl, stderr);
#endif
		uvc_frame_desc_t *frame_desc;
		result = uvc_get_frame_desc(mDeviceHandle, ctrl, &frame_desc);
		if (LIKELY(!result)) {
			defaultCameraFps = (int)(10000000 / frame_desc->dwDefaultFrameInterval);
			frameWidth = frame_desc->wWidth;
			frameHeight = frame_desc->wHeight;
			LOGI("frameSize=(%d,%d)@%s", frameWidth, frameHeight, (!requestMode ? "YUYV" : "MJPEG"));
			pthread_mutex_lock(&preview_mutex);
			if (LIKELY(mPreviewWindow)) {
				ANativeWindow_setBuffersGeometry(mPreviewWindow, frameWidth, frameHeight, previewFormat);
			}
			pthread_mutex_unlock(&preview_mutex);
			mIsRunning = true;
		} else {
			frameWidth = requestWidth;
			frameHeight = requestHeight;
		}
		frameBytes = frameWidth * frameHeight * (!requestMode ? 2 : 4);
	} else {
		LOGE("could not negotiate with camera:err=%d", result);
	}
	RETURN(result, int);
}

convFunc_t UVCPreview::getConvertFunc(uvc_frame_t *frame, int32_t outputPixelFormat){
	switch (frame->frame_format){
		case UVC_FRAME_FORMAT_MJPEG:
			switch (outputPixelFormat){
				case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
					return uvc_mjpeg2rgba;
				case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM:
					return uvc_mjpeg2rgbx;
				case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM:
					return uvc_mjpeg2rgb565;
				default:
					return nullptr;
			}
		case UVC_FRAME_FORMAT_YUYV:
			switch (outputPixelFormat){
				case AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM:
				case AHARDWAREBUFFER_FORMAT_R8G8B8X8_UNORM:
					return uvc_yuyv2rgbx;
				case AHARDWAREBUFFER_FORMAT_R5G6B5_UNORM:
					//return uvc_yuyv2rgb565;
					// TODO uvc_yuyv2rgb565
				default:
					return nullptr;
			}
		default:
			return nullptr;
	}
}

void UVCPreview::do_preview(uvc_stream_ctrl_t *ctrl) {
	ENTER();
	uvc_frame_t *converted = NULL;
	uvc_frame_t *frame = NULL;
	uvc_error_t result = uvc_start_streaming(
			mDeviceHandle, ctrl, uvc_preview_frame_callback, (void *) this, 0);

	if (LIKELY(!result)) {
		clearPreviewFrame();
		pthread_create(&capture_thread, NULL, capture_thread_func, (void *) this);

#if LOCAL_DEBUG
		LOGI("Streaming...");
#endif

		clock_gettime(CLOCK_REALTIME, &tStart);
		for (; LIKELY(isRunning());) {
			frame = waitPreviewFrame();
			if (LIKELY(frame)) {
				if (rotateImage && frame->frame_format == UVC_FRAME_FORMAT_YUYV) {
					if (frameRotationAngle == 90) {
						rotateImage->rotate_yuyv_90(frame);
					} else if (frameRotationAngle == 180) {
						rotateImage->rotate_yuyv_180(frame);
					} else if (frameRotationAngle == 270) {
						rotateImage->rotate_yuyv_270(frame);
					}
					if (frameHorizontalMirror) {
						rotateImage->horizontal_mirror_yuyv(frame);
					}
					if (frameVerticalMirror) {
						rotateImage->vertical_mirror_yuyv(frame);
					}
				}
                if (!mPreviewConvertFunc) mPreviewConvertFunc = getConvertFunc(frame, previewFormat);
				converted = draw_preview_one(frame, &mPreviewWindow, mPreviewConvertFunc, previewFormatPixelBytes);
				addCaptureFrame(converted);

				framesCounter++;
				clock_gettime(CLOCK_REALTIME, &tEnd);
				if (tEnd.tv_sec - tStart.tv_sec >= 1) {
					clock_gettime(CLOCK_REALTIME, &tStart);
					currentFps = framesCounter;
					framesCounter = 0;
				}
			}
		}
		pthread_cond_signal(&capture_sync);
#if LOCAL_DEBUG
		LOGI("preview_thread_func:wait for all callbacks complete");
#endif
		uvc_stop_streaming(mDeviceHandle);
#if LOCAL_DEBUG
		LOGI("Streaming finished");
#endif
	} else {
		uvc_perror(result, "failed start_streaming");
	}

	EXIT();
}

// Copy frame data
static void copyFrame(const uint8_t *src, uint8_t *dest, const int width, int height, const int stride_src, const int stride_dest) {
	const int h8 = height % 8;
	for (int i = 0; i < h8; i++) {
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
	}
	for (int i = 0; i < height; i += 8) {
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
		memcpy(dest, src, width);
		dest += stride_dest; src += stride_src;
	}
}


// transfer specific frame data to the Surface(ANativeWindow)
int copyToSurface(uvc_frame_t *frame, ANativeWindow **window) {
	// ENTER();
	int result = 0;
	if (LIKELY(*window)) {
		ANativeWindow_Buffer buffer;
		if (LIKELY(ANativeWindow_lock(*window, &buffer, NULL) == 0)) {
			const uint8_t *src = (uint8_t *)frame->data;
			uint8_t *dest = (uint8_t *)buffer.bits;

			const int src_step = frame->width * previewFormatPixelBytes;
			const int dest_step = buffer.stride * previewFormatPixelBytes;

			if (src_step == dest_step){
				memcpy(dest, src, frame->data_bytes);
			}else{
				const int src_w = frame->width * previewFormatPixelBytes;
				const int dest_w = buffer.width * previewFormatPixelBytes;
				// use lower transfer bytes
				const int w = src_w < dest_w ? src_w : dest_w;
				// use lower height
				const int h = frame->height < buffer.height ? frame->height : buffer.height;
				// transfer frame data to Surface
				copyFrame(src, dest, w, h, src_step, dest_step);
			}
			ANativeWindow_unlockAndPost(*window);
		} else {
			result = -1;
		}
	} else {
		result = -1;
	}
	return result; //RETURN(result, int);
}

uvc_frame_t *UVCPreview::draw_preview_one(uvc_frame_t *frame, ANativeWindow **window, convFunc_t convert_func, int pixelBytes) {
	// ENTER();

	int b = 0;
	pthread_mutex_lock(&preview_mutex);
	{
		b = *window != NULL;
	}
	pthread_mutex_unlock(&preview_mutex);
	if (LIKELY(b)) {
		if (convert_func) {
			uvc_frame_t *converted = get_frame(frame->width * frame->height * pixelBytes);
			if LIKELY(converted) {
				b = convert_func(frame, converted);
				if (!b) {
					pthread_mutex_lock(&preview_mutex);
					copyToSurface(converted, window);
					pthread_mutex_unlock(&preview_mutex);
				} else {
					LOGE("failed converting");
				}
				recycle_frame(frame);
                return converted;
			}
		} else {
			pthread_mutex_lock(&preview_mutex);
			copyToSurface(frame, window);
			pthread_mutex_unlock(&preview_mutex);
		}
	}
	return frame; //RETURN(frame, uvc_frame_t *);
}

//======================================================================
// Whether to capture
//======================================================================
inline const bool UVCPreview::isCapturing() const { return mIsCapturing; }

// Set capture display
int UVCPreview::setCaptureDisplay(ANativeWindow *capture_window) {
	ENTER();
	pthread_mutex_lock(&capture_mutex);
	{
		if (isRunning() && isCapturing()) {
			mIsCapturing = false;
			if (mCaptureWindow) {
				pthread_cond_signal(&capture_sync);
				pthread_cond_wait(&capture_sync, &capture_mutex);	// wait finishing capturing
			}
		}
		if (mCaptureWindow != capture_window) {
			// release current Surface if already assigned.
			if (UNLIKELY(mCaptureWindow)) ANativeWindow_release(mCaptureWindow);
			mCaptureWindow = capture_window;
			// if you use Surface came from MediaCodec#createInputSurface
			// you could not change window format at least when you use
			// ANativeWindow_lock / ANativeWindow_unlockAndPost
			// to write frame data to the Surface...
			// So we need check here.
			if (mCaptureWindow) {
				int32_t window_format = ANativeWindow_getFormat(mCaptureWindow);
				if (window_format == 34) window_format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM;
				if (window_format == previewFormat) {
					ANativeWindow_setBuffersGeometry(mCaptureWindow, frameWidth, frameHeight, previewFormat);
				}else{
					LOGE("window format mismatch, cancelled movie capturing.");
					ANativeWindow_release(mCaptureWindow);
					mCaptureWindow = NULL;
				}
			}
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	RETURN(0, int);
}

// Set capture frame
void UVCPreview::addCaptureFrame(uvc_frame_t *frame) {
	pthread_mutex_lock(&capture_mutex);
	if (LIKELY(isRunning())) {
		// keep only latest one
		if (captureQueu) {
		    // put back into frame pool
			recycle_frame(captureQueu);
		}
		captureQueu = frame;

		pthread_cond_broadcast(&capture_sync);
	}else {
	    // put back into frame pool
	    recycle_frame(frame);
    }
	pthread_mutex_unlock(&capture_mutex);
}

/**
 * get frame data for capturing, if not exist, block and wait
 */
uvc_frame_t *UVCPreview::waitCaptureFrame() {
	uvc_frame_t *frame = NULL;
	pthread_mutex_lock(&capture_mutex);
	{
		if (!captureQueu) {
			pthread_cond_wait(&capture_sync, &capture_mutex);
		}
		if (LIKELY(isRunning() && captureQueu)) {
			frame = captureQueu;
			captureQueu = NULL;
		}
	}
	pthread_mutex_unlock(&capture_mutex);
	return frame;
}

/**
 * clear drame data for capturing
 */
void UVCPreview::clearCaptureFrame() {
	pthread_mutex_lock(&capture_mutex);
	{
		if (captureQueu)
		    // put back into frame pool
			recycle_frame(captureQueu);
		captureQueu = NULL;
	}
	pthread_mutex_unlock(&capture_mutex);
}

//======================================================================
/*
 * capture thread method
 * @param vptr_args pointer to UVCPreview instance
 */
// static
void *UVCPreview::capture_thread_func(void *vptr_args) {
	int result;

	ENTER();
	UVCPreview *preview = reinterpret_cast<UVCPreview *>(vptr_args);
	if (LIKELY(preview)) {
		JavaVM *vm = getVM();
		JNIEnv *env;
		// Attach the current thread to a Java (Dalvik) virtual machine
		vm->AttachCurrentThread(&env, NULL);
		preview->do_capture(env);	// never return until finish previewing
		// Detach the current thread from a Java (Dalvik) virtual machine.
		vm->DetachCurrentThread();
		MARK("DetachCurrentThread");
	}
	PRE_EXIT();
	pthread_exit(NULL);
}

/**
 * the actual function for capturing
 */
void UVCPreview::do_capture(JNIEnv *env) {

	ENTER();
	// Clear capture frame
	clearCaptureFrame();
	// Pixel format conversion
	callbackPixelFormatChanged();
	for (; isRunning() ;) {
	    // Mark as capture
		mIsCapturing = true;
		if (mCaptureWindow) {
		    // Write frame data to Surface for capture
			do_capture_surface(env);
		} else {
		    // Execute capture idle loop
			do_capture_idle_loop(env);
		}
		// Wake up all threads blocked by capture_sync
		pthread_cond_broadcast(&capture_sync);
	}	// end of for (; isRunning() ;)
	EXIT();
}

// Execute capture idle loop
void UVCPreview::do_capture_idle_loop(JNIEnv *env) {
	ENTER();
	
	for (; isRunning() && isCapturing() ;) {
		do_capture_callback(env, waitCaptureFrame());
	}
	
	EXIT();
}

/**
 * write frame data to Surface for capturing
 */
void UVCPreview::do_capture_surface(JNIEnv *env) {
	ENTER();

	uvc_frame_t *frame = NULL;
	char *local_picture_path;

	for (; isRunning() && isCapturing() ;) {
	    // Get the frame data to be captured (if it does not exist), block and wait
		frame = waitCaptureFrame();
		if (LIKELY(frame)) {
			if LIKELY(isCapturing()) {
				if (LIKELY(mCaptureWindow)) {
					pthread_mutex_lock(&capture_mutex);
					copyToSurface(frame, &mCaptureWindow);
					pthread_mutex_unlock(&capture_mutex);
				}
			}
			do_capture_callback(env, frame);
		}
	}
	if (mCaptureWindow) {
		ANativeWindow_release(mCaptureWindow);
		mCaptureWindow = NULL;
	}

	EXIT();
}

/**
 * call IFrameCallback#onFrame if needs
 */
void UVCPreview::do_capture_callback(JNIEnv *env, uvc_frame_t *frame) {
	ENTER();

	if (LIKELY(frame)) {
		uvc_frame_t *callback_frame = frame;
		if (mFrameCallbackObj) {
			if (mFrameCallbackFunc) {
			    // Get frames from frame pool
				callback_frame = get_frame(callbackPixelBytes);
				if (LIKELY(callback_frame)) {
				    // Pixel format conversion
					int b = mFrameCallbackFunc(frame, callback_frame);
					// put back into frame pool
					recycle_frame(frame);
					if (UNLIKELY(b)) {
						LOGW("failed to convert for callback frame");
						goto SKIP;
					}
				} else {
					LOGW("failed to allocate for callback frame");
					callback_frame = frame;
					goto SKIP;
				}
			}
			// NewDirectByteBuffer Creates a directly accessible memory of a specified length based on a specified memory address.
			jobject buf = env->NewDirectByteBuffer(callback_frame->data, callbackPixelBytes);
			env->CallVoidMethod(mFrameCallbackObj, iframecallback_fields.onFrame, buf);
			env->ExceptionClear();
			env->DeleteLocalRef(buf);
		}
 SKIP:
        // put back into frame pool
		recycle_frame(callback_frame);
	}
	EXIT();
}

void UVCPreview::setHorizontalMirror(int horizontalMirror){
	frameHorizontalMirror = horizontalMirror;
	if( frameHorizontalMirror && !rotateImage) {
	    rotateImage = new RotateImage();
    }
}

void UVCPreview::setVerticalMirror(int verticalMirror){
	frameVerticalMirror = verticalMirror;
	if( frameVerticalMirror && !rotateImage) {
	    rotateImage = new RotateImage();
    }
}

void UVCPreview::setCameraAngle(int cameraAngle){
	frameRotationAngle = (360 - cameraAngle) % 360;
	LOGW("frameRotationAngle:%d",frameRotationAngle);
	if( frameRotationAngle && !rotateImage) {
	    rotateImage = new RotateImage();
    }
}

int UVCPreview::getCurrentFps() {
    return currentFps;
}

int UVCPreview::getDefaultCameraFps() {
	LOGI("defaultCameraFps: %d", defaultCameraFps);
	return defaultCameraFps;
}

int UVCPreview::getFrameWidth() {
	return frameWidth;
}

int UVCPreview::getFrameHeight() {
	return frameHeight;
}
