
#include "libUVCCamera.h"

#pragma interface

#ifndef ROTATEIMAGE_H_
#define ROTATEIMAGE_H_

class RotateImage {
private:
    // image data
    void * rotate_data;
    // des_data length
    size_t rotate_data_bytes;

    void SpaceSizeProcessing(uvc_frame_t *src_frame);

public:
	RotateImage();
	~RotateImage();

    // Rotate 90 degrees clockwise
    void rotate_yuyv_90(uvc_frame_t *src_frame);
    void rotateYuyvDegree90(void *rotatedYuyv, void *yuyv, uint32_t width, uint32_t height);

    // Rotate 180 degrees clockwise
    void rotate_yuyv_180(uvc_frame_t *src_frame);
    void rotateYuyvDegree180(void *rotatedYuyv, void *yuyv, uint32_t width, uint32_t height);

    // Rotate 270 degrees clockwise
    void rotate_yuyv_270(uvc_frame_t *src_frame);
    void rotateYuyvDegree270(void *rotatedYuyv, void *yuyv, uint32_t width, uint32_t height);

    // Horizontal mirroring
    void horizontal_mirror_yuyv(uvc_frame_t *src_frame);
    void horizontalMirrorYuyv(void *_mirrorYuyv, void *_yuyv, uint32_t width, uint32_t height);


    // vertical mirror
    void vertical_mirror_yuyv(uvc_frame_t *src_frame);
    void verticalMirrorYuyv(void *_mirrorYuyv, void *_yuyv, uint32_t width, uint32_t height);
};

#endif