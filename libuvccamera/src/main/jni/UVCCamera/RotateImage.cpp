# include <stdlib.h>
#include <string.h>

#include "RotateImage.h"

/*
Since the arrangement of YUYV is (YUYV YUYV YUYV....), its sharing relationship is that every two horizontally adjacent Y will use the same set of U and V.
Therefore, when rotating 180 degrees, the sharing relationship of YUV can not be broken, but the order of 2 Y in every 4 bytes is changed;
However, when rotating 90 degrees or 270 degrees, since the original horizontal Y will be modified to vertical, the sharing relationship of YUV will also be broken.

reference https://www.jianshu.com/p/7e602dea3ca1
*/

RotateImage::RotateImage(void) {
    ENTER();

    rotate_data = NULL;
    rotate_data_bytes = 0;

    EXIT();
}

RotateImage::~RotateImage(void) {
    ENTER();

    if(rotate_data != NULL){
        free(rotate_data);
    }
    rotate_data_bytes = 0;

    EXIT();
}

// Space size processing
void RotateImage::SpaceSizeProcessing(uvc_frame_t *src_frame) {
    if(rotate_data == NULL || rotate_data_bytes < src_frame->data_bytes){
        if(rotate_data != NULL){
            free(rotate_data);
        }

        rotate_data = malloc(src_frame->data_bytes);
        rotate_data_bytes = src_frame->data_bytes;
    }
}

// Rotate 90 degrees clockwise
void RotateImage::rotate_yuyv_90(uvc_frame_t *src_frame) {
    SpaceSizeProcessing(src_frame);
    rotateYuyvDegree90(rotate_data, src_frame->data, src_frame->width, src_frame->height);

    void * temp = src_frame->data;
    src_frame->data = rotate_data;
    rotate_data = temp;

    uint32_t switchTemp = src_frame->width;
    src_frame->width = src_frame->height;
    src_frame->height = switchTemp;
    src_frame->step = src_frame->width * 2;
}

// Rotate 180 degrees clockwise
void RotateImage::rotate_yuyv_180(uvc_frame_t *src_frame) {
    SpaceSizeProcessing(src_frame);
    rotateYuyvDegree180(rotate_data, src_frame->data, src_frame->width, src_frame->height);

    void * temp = src_frame->data;
    src_frame->data = rotate_data;
    rotate_data = temp;
}

// Rotate 270 degrees clockwise
void RotateImage::rotate_yuyv_270(uvc_frame_t *src_frame) {
    SpaceSizeProcessing(src_frame);
    rotateYuyvDegree270(rotate_data, src_frame->data, src_frame->width, src_frame->height);

    void * temp = src_frame->data;
    src_frame->data = rotate_data;
    rotate_data = temp;

    uint32_t switchTemp = src_frame->width;
    src_frame->width = src_frame->height;
    src_frame->height = switchTemp;
    src_frame->step = src_frame->width * 2;
}

// Horizontal mirroring
void RotateImage::horizontal_mirror_yuyv(uvc_frame_t *src_frame){
    SpaceSizeProcessing(src_frame);
    horizontalMirrorYuyv(rotate_data, src_frame->data, src_frame->width, src_frame->height);

    void * temp = src_frame->data;
    src_frame->data = rotate_data;
    rotate_data = temp;
}

// vertical mirror
void RotateImage::vertical_mirror_yuyv(uvc_frame_t *src_frame){
    SpaceSizeProcessing(src_frame);
    verticalMirrorYuyv(rotate_data, src_frame->data, src_frame->width, src_frame->height);

    void * temp = src_frame->data;
    src_frame->data = rotate_data;
    rotate_data = temp;
}

void RotateImage::rotateYuyvDegree90(void *_rotatedYuyv, void *_yuyv, uint32_t width, uint32_t height) {
    char *rotatedYuyv = (char *)_rotatedYuyv;
    char *yuyv = (char *)_yuyv;
    uint32_t lineDataSize = width * 2;
    uint32_t rotatedLineDataSize = height * 2;
    uint32_t rotatedYuyvIndex = 0;
    uint32_t finalLineStartIndex = (height - 2) * lineDataSize;
    for (uint32_t w = 0; w < lineDataSize; w += 4) {
        int yuyvStartIndex = finalLineStartIndex + w;
        int offset = 0;
        for (uint32_t h = 0; h < height; h += 2) {
            /**
             * y1 u1 y2 v2   y3 u2 y4 v2
             *                              ->    Picture after rotation
             * y5 u3 y6 v3   y7 u4 y8 v4
             */
            uint32_t originalOffset = yuyvStartIndex - offset;
            uint32_t originalNextLineOffset = yuyvStartIndex - offset + lineDataSize;
            uint32_t targetNextLineOffset = rotatedYuyvIndex + rotatedLineDataSize;
            //y5
            (rotatedYuyv)[rotatedYuyvIndex] = (yuyv)[originalNextLineOffset];
            //u3
            (rotatedYuyv)[rotatedYuyvIndex + 1] = (yuyv)[originalNextLineOffset + 1];
            //y1
            (rotatedYuyv)[rotatedYuyvIndex + 2] = (yuyv)[originalOffset];
            //v3
            (rotatedYuyv)[rotatedYuyvIndex + 3] = (yuyv)[originalNextLineOffset + 3];

            //y6
            (rotatedYuyv)[targetNextLineOffset] = (yuyv)[originalNextLineOffset + 2];
            //u1
            (rotatedYuyv)[targetNextLineOffset + 1] = (yuyv)[originalOffset + 1];
            //y2
            (rotatedYuyv)[targetNextLineOffset + 2] = (yuyv)[originalOffset + 2];
            //v2
            (rotatedYuyv)[targetNextLineOffset + 3] = (yuyv)[originalOffset + 3];

            rotatedYuyvIndex += 4;
            offset += lineDataSize * 2;
        }
        rotatedYuyvIndex += rotatedLineDataSize;
    }
}

void RotateImage::rotateYuyvDegree180(void *_rotatedYuyv, void *_yuyv, uint32_t width, uint32_t height) {
    char *rotatedYuyv = (char *)_rotatedYuyv;
    char *yuyv = (char *)_yuyv;
    uint32_t lineDataSize = width * 2;
    uint32_t yuyvIndex = lineDataSize * height - 4;
    uint32_t rotatedIndex = 0;
    //rotate
    for (int h = height - 1; h >= 0; h--) {
        for (int w = lineDataSize - 4; w >= 0; w -= 4) {
            (rotatedYuyv)[rotatedIndex++] = (yuyv)[yuyvIndex + 2];
            (rotatedYuyv)[rotatedIndex++] = (yuyv)[yuyvIndex + 1];
            (rotatedYuyv)[rotatedIndex++] = (yuyv)[yuyvIndex];
            (rotatedYuyv)[rotatedIndex++] = (yuyv)[yuyvIndex + 3];
            yuyvIndex -= 4;
        }
    }
}

void RotateImage::rotateYuyvDegree270(void *_rotatedYuyv, void *_yuyv, uint32_t width, uint32_t height) {
    char *rotatedYuyv = (char *)_rotatedYuyv;
    char *yuyv = (char *)_yuyv;
    uint32_t lineDataSize = width * 2;
    uint32_t rotatedLineDataSize = height * 2;
    uint32_t rotatedYuyvIndex = 0;
    uint32_t finalColumnStartIndex = lineDataSize - 4;
    for (uint32_t w = 0; w < lineDataSize; w += 4) {
        uint32_t offset = 0;
        for (uint32_t h = 0; h < height; h += 2) {
            /**
             * y1 u1 y2 v1   y3 u2 y4 v2
             *                              ->    Picture after rotation
             * y5 u3 y6 v3   y7 u4 y8 v4
             */

            uint32_t originalOffset = finalColumnStartIndex + offset;
            uint32_t originalNextLineOffset = finalColumnStartIndex + offset + lineDataSize;
            uint32_t targetNextLineOffset = rotatedYuyvIndex + rotatedLineDataSize;
            //y4
            (rotatedYuyv)[rotatedYuyvIndex] = (yuyv)[originalOffset + 2];
            //u2
            (rotatedYuyv)[rotatedYuyvIndex + 1] = (yuyv)[originalOffset + 1];
            //y8
            (rotatedYuyv)[rotatedYuyvIndex + 2] = (yuyv)[originalNextLineOffset + 2];
            //v2
            (rotatedYuyv)[rotatedYuyvIndex + 3] = (yuyv)[originalOffset + 3];

            //y3
            (rotatedYuyv)[targetNextLineOffset] = (yuyv)[finalColumnStartIndex + offset];
            //u4
            (rotatedYuyv)[targetNextLineOffset + 1] = (yuyv)[originalNextLineOffset + 1];
            //y7
            (rotatedYuyv)[targetNextLineOffset + 2] = (yuyv)[originalNextLineOffset];
            //v4
            (rotatedYuyv)[targetNextLineOffset + 3] = (yuyv)[originalNextLineOffset + 3];

            rotatedYuyvIndex += 4;
            offset += lineDataSize * 2;
        }
        finalColumnStartIndex -= 4;
        rotatedYuyvIndex += rotatedLineDataSize;
    }
}

// Horizontal Mirror Reference https://www.jianshu.com/p/777b7ea0059c
void RotateImage::horizontalMirrorYuyv(void *_mirrorYuyv, void *_yuyv, uint32_t width, uint32_t height) {
    char *mirrorYuyv = (char *)_mirrorYuyv;
    char *yuyv = (char *)_yuyv;
    uint32_t lineStartIndex = 0;
    uint32_t lineDataSize = width * 2;
    for (uint32_t h = 0; h < height; h++) {
        for (uint32_t w = 0; w < lineDataSize; w += 4) {
            mirrorYuyv[lineStartIndex + w] = yuyv[lineStartIndex + lineDataSize - w - 2];
            mirrorYuyv[lineStartIndex + w + 1] = yuyv[lineStartIndex + lineDataSize - w - 3];
            mirrorYuyv[lineStartIndex + w + 2] = yuyv[lineStartIndex + lineDataSize - w - 4];
            mirrorYuyv[lineStartIndex + w + 3] = yuyv[lineStartIndex + lineDataSize - w - 1];
        }
        lineStartIndex += lineDataSize;
    }
}

// Vertical Mirror Reference https://www.jianshu.com/p/777b7ea0059c
void RotateImage::verticalMirrorYuyv(void *_mirrorYuyv, void *_yuyv, uint32_t width, uint32_t height) {
    char *mirrorYuyv = (char *)_mirrorYuyv;
    char *yuyv = (char *)_yuyv;
    uint32_t lineDataSize = width * 2;
    yuyv += width * height * 2;
    for (uint32_t h = 0; h < height; h++) {
        memcpy(mirrorYuyv, yuyv, lineDataSize);
        mirrorYuyv += lineDataSize;
        yuyv -= lineDataSize;
    }
}
