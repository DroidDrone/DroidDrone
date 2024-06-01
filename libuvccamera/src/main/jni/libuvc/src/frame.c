/*********************************************************************
* Software License Agreement (BSD License)
*
*  Copyright (C) 2010-2012 Ken Tossell
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions
*  are met:
*
*   * Redistributions of source code must retain the above copyright
*     notice, this list of conditions and the following disclaimer.
*   * Redistributions in binary form must reproduce the above
*     copyright notice, this list of conditions and the following
*     disclaimer in the documentation and/or other materials provided
*     with the distribution.
*   * Neither the name of the author nor other contributors may be
*     used to endorse or promote products derived from this software
*     without specific prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
*  "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
*  LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
*  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
*  COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
*  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
*  BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
*  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
*  CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
*  LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
*  ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*********************************************************************/
/**
 * @defgroup frame Frame processing
 * @brief Tools for managing frame buffers and converting between image formats
 */
#include "libuvc/libuvc.h"
#include "libuvc/libuvc_internal.h"

/** @internal */
uvc_error_t uvc_ensure_frame_size(uvc_frame_t *frame, size_t need_bytes) {
  if (frame->library_owns_data) {
    if (!frame->data || frame->data_bytes != need_bytes) {
      frame->data_bytes = need_bytes;
      frame->data = realloc(frame->data, frame->data_bytes);
    }
    if (!frame->data) return UVC_ERROR_NO_MEM;
    return UVC_SUCCESS;
  } else {
    if (!frame->data || frame->data_bytes < need_bytes)
      return UVC_ERROR_NO_MEM;
    return UVC_SUCCESS;
  }
}

/** @brief Allocate a frame structure
 * @ingroup frame
 *
 * @param data_bytes Number of bytes to allocate, or zero
 * @return New frame, or NULL on error
 */
uvc_frame_t *uvc_allocate_frame(size_t data_bytes) {
  uvc_frame_t *frame = malloc(sizeof(*frame));

  if (!frame)
    return NULL;

  memset(frame, 0, sizeof(*frame));

  frame->library_owns_data = 1;

  if (data_bytes > 0) {
    frame->data_bytes = data_bytes;
    frame->data = malloc(data_bytes);

    if (!frame->data) {
      free(frame);
      return NULL;
    }
  }

  return frame;
}

/** @brief Free a frame structure
 * @ingroup frame
 *
 * @param frame Frame to destroy
 */
void uvc_free_frame(uvc_frame_t *frame) {
  if (frame->library_owns_data)
  {
    if (frame->data_bytes > 0)
      free(frame->data);
    if (frame->metadata_bytes > 0)
      free(frame->metadata);
  }

  free(frame);
}

static inline unsigned char sat(int i) {
  return (unsigned char)( i >= 255 ? 255 : (i < 0 ? 0 : i));
}

/** @brief Duplicate a frame, preserving color format
 * @ingroup frame
 *
 * @param in Original frame
 * @param out Duplicate frame
 */
uvc_error_t uvc_duplicate_frame(uvc_frame_t *in, uvc_frame_t *out) {
  if (uvc_ensure_frame_size(out, in->data_bytes) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = in->frame_format;
  out->step = in->step;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  memcpy(out->data, in->data, in->data_bytes);

  if (in->metadata && in->metadata_bytes > 0)
  {
      if (out->metadata_bytes < in->metadata_bytes)
      {
          out->metadata = realloc(out->metadata, in->metadata_bytes);
      }
      out->metadata_bytes = in->metadata_bytes;
      memcpy(out->metadata, in->metadata, in->metadata_bytes);
  }

  return UVC_SUCCESS;
}

#define YUYV2RGB_2(pyuv, prgb) { \
    float r = 1.402f * ((pyuv)[3]-128); \
    float g = -0.34414f * ((pyuv)[1]-128) - 0.71414f * ((pyuv)[3]-128); \
    float b = 1.772f * ((pyuv)[1]-128); \
    (prgb)[0] = sat(pyuv[0] + r); \
    (prgb)[1] = sat(pyuv[0] + g); \
    (prgb)[2] = sat(pyuv[0] + b); \
    (prgb)[3] = sat(pyuv[2] + r); \
    (prgb)[4] = sat(pyuv[2] + g); \
    (prgb)[5] = sat(pyuv[2] + b); \
    }
#define IYUYV2RGB_2(pyuv, prgb) { \
    int r = (22987 * ((pyuv)[3] - 128)) >> 14; \
    int g = (-5636 * ((pyuv)[1] - 128) - 11698 * ((pyuv)[3] - 128)) >> 14; \
    int b = (29049 * ((pyuv)[1] - 128)) >> 14; \
    (prgb)[0] = sat(*(pyuv) + r); \
    (prgb)[1] = sat(*(pyuv) + g); \
    (prgb)[2] = sat(*(pyuv) + b); \
    (prgb)[3] = sat((pyuv)[2] + r); \
    (prgb)[4] = sat((pyuv)[2] + g); \
    (prgb)[5] = sat((pyuv)[2] + b); \
    }
#define IYUYV2RGB_16(pyuv, prgb) IYUYV2RGB_8(pyuv, prgb); IYUYV2RGB_8(pyuv + 16, prgb + 24);
#define IYUYV2RGB_8(pyuv, prgb) IYUYV2RGB_4(pyuv, prgb); IYUYV2RGB_4(pyuv + 8, prgb + 12);
#define IYUYV2RGB_4(pyuv, prgb) IYUYV2RGB_2(pyuv, prgb); IYUYV2RGB_2(pyuv + 4, prgb + 6);

/** @brief Convert a frame from YUYV to RGB
 * @ingroup frame
 *
 * @param in YUYV frame
 * @param out RGB frame
 */
uvc_error_t uvc_yuyv2rgb(uvc_frame_t *in, uvc_frame_t *out) {
  if (in->frame_format != UVC_FRAME_FORMAT_YUYV)
    return UVC_ERROR_INVALID_PARAM;

  if (uvc_ensure_frame_size(out, in->width * in->height * 3) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = UVC_FRAME_FORMAT_RGB;
  out->step = in->width * 3;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  uint8_t *pyuv = in->data;
  uint8_t *prgb = out->data;
  uint8_t *prgb_end = prgb + out->data_bytes;

  while (prgb < prgb_end) {
    IYUYV2RGB_8(pyuv, prgb);

    prgb += 3 * 8;
    pyuv += 2 * 8;
  }

  return UVC_SUCCESS;
}

#define IYUYV2BGR_2(pyuv, pbgr) { \
    int r = (22987 * ((pyuv)[3] - 128)) >> 14; \
    int g = (-5636 * ((pyuv)[1] - 128) - 11698 * ((pyuv)[3] - 128)) >> 14; \
    int b = (29049 * ((pyuv)[1] - 128)) >> 14; \
    (pbgr)[0] = sat(*(pyuv) + b); \
    (pbgr)[1] = sat(*(pyuv) + g); \
    (pbgr)[2] = sat(*(pyuv) + r); \
    (pbgr)[3] = sat((pyuv)[2] + b); \
    (pbgr)[4] = sat((pyuv)[2] + g); \
    (pbgr)[5] = sat((pyuv)[2] + r); \
    }
#define IYUYV2BGR_16(pyuv, pbgr) IYUYV2BGR_8(pyuv, pbgr); IYUYV2BGR_8(pyuv + 16, pbgr + 24);
#define IYUYV2BGR_8(pyuv, pbgr) IYUYV2BGR_4(pyuv, pbgr); IYUYV2BGR_4(pyuv + 8, pbgr + 12);
#define IYUYV2BGR_4(pyuv, pbgr) IYUYV2BGR_2(pyuv, pbgr); IYUYV2BGR_2(pyuv + 4, pbgr + 6);

/** @brief Convert a frame from YUYV to BGR
 * @ingroup frame
 *
 * @param in YUYV frame
 * @param out BGR frame
 */
uvc_error_t uvc_yuyv2bgr(uvc_frame_t *in, uvc_frame_t *out) {
  if (in->frame_format != UVC_FRAME_FORMAT_YUYV)
    return UVC_ERROR_INVALID_PARAM;

  if (uvc_ensure_frame_size(out, in->width * in->height * 3) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = UVC_FRAME_FORMAT_BGR;
  out->step = in->width * 3;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  uint8_t *pyuv = in->data;
  uint8_t *pbgr = out->data;
  uint8_t *pbgr_end = pbgr + out->data_bytes;

  while (pbgr < pbgr_end) {
    IYUYV2BGR_8(pyuv, pbgr);

    pbgr += 3 * 8;
    pyuv += 2 * 8;
  }

  return UVC_SUCCESS;
}

#define IYUYV2Y(pyuv, py) { \
    (py)[0] = (pyuv[0]); \
    }

/** @brief Convert a frame from YUYV to Y (GRAY8)
 * @ingroup frame
 *
 * @param in YUYV frame
 * @param out GRAY8 frame
 */
uvc_error_t uvc_yuyv2y(uvc_frame_t *in, uvc_frame_t *out) {
  if (in->frame_format != UVC_FRAME_FORMAT_YUYV)
    return UVC_ERROR_INVALID_PARAM;

  if (uvc_ensure_frame_size(out, in->width * in->height) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = UVC_FRAME_FORMAT_GRAY8;
  out->step = in->width;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  uint8_t *pyuv = in->data;
  uint8_t *py = out->data;
  uint8_t *py_end = py + out->data_bytes;

  while (py < py_end) {
    IYUYV2Y(pyuv, py);

    py += 1;
    pyuv += 2;
  }

  return UVC_SUCCESS;
}

#define IYUYV2UV(pyuv, puv) { \
    (puv)[0] = (pyuv[1]); \
    }

/** @brief Convert a frame from YUYV to UV (GRAY8)
 * @ingroup frame
 *
 * @param in YUYV frame
 * @param out GRAY8 frame
 */
uvc_error_t uvc_yuyv2uv(uvc_frame_t *in, uvc_frame_t *out) {
  if (in->frame_format != UVC_FRAME_FORMAT_YUYV)
    return UVC_ERROR_INVALID_PARAM;

  if (uvc_ensure_frame_size(out, in->width * in->height) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = UVC_FRAME_FORMAT_GRAY8;
  out->step = in->width;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  uint8_t *pyuv = in->data;
  uint8_t *puv = out->data;
  uint8_t *puv_end = puv + out->data_bytes;

  while (puv < puv_end) {
    IYUYV2UV(pyuv, puv);

    puv += 1;
    pyuv += 2;
  }

  return UVC_SUCCESS;
}

#define IUYVY2RGB_2(pyuv, prgb) { \
    int r = (22987 * ((pyuv)[2] - 128)) >> 14; \
    int g = (-5636 * ((pyuv)[0] - 128) - 11698 * ((pyuv)[2] - 128)) >> 14; \
    int b = (29049 * ((pyuv)[0] - 128)) >> 14; \
    (prgb)[0] = sat((pyuv)[1] + r); \
    (prgb)[1] = sat((pyuv)[1] + g); \
    (prgb)[2] = sat((pyuv)[1] + b); \
    (prgb)[3] = sat((pyuv)[3] + r); \
    (prgb)[4] = sat((pyuv)[3] + g); \
    (prgb)[5] = sat((pyuv)[3] + b); \
    }
#define IUYVY2RGB_16(pyuv, prgb) IUYVY2RGB_8(pyuv, prgb); IUYVY2RGB_8(pyuv + 16, prgb + 24);
#define IUYVY2RGB_8(pyuv, prgb) IUYVY2RGB_4(pyuv, prgb); IUYVY2RGB_4(pyuv + 8, prgb + 12);
#define IUYVY2RGB_4(pyuv, prgb) IUYVY2RGB_2(pyuv, prgb); IUYVY2RGB_2(pyuv + 4, prgb + 6);

/** @brief Convert a frame from UYVY to RGB
 * @ingroup frame
 * @param ini UYVY frame
 * @param out RGB frame
 */
uvc_error_t uvc_uyvy2rgb(uvc_frame_t *in, uvc_frame_t *out) {
  if (in->frame_format != UVC_FRAME_FORMAT_UYVY)
    return UVC_ERROR_INVALID_PARAM;

  if (uvc_ensure_frame_size(out, in->width * in->height * 3) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = UVC_FRAME_FORMAT_RGB;
  out->step = in->width *3;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  uint8_t *pyuv = in->data;
  uint8_t *prgb = out->data;
  uint8_t *prgb_end = prgb + out->data_bytes;

  while (prgb < prgb_end) {
    IUYVY2RGB_8(pyuv, prgb);

    prgb += 3 * 8;
    pyuv += 2 * 8;
  }

  return UVC_SUCCESS;
}

#define IUYVY2BGR_2(pyuv, pbgr) { \
    int r = (22987 * ((pyuv)[2] - 128)) >> 14; \
    int g = (-5636 * ((pyuv)[0] - 128) - 11698 * ((pyuv)[2] - 128)) >> 14; \
    int b = (29049 * ((pyuv)[0] - 128)) >> 14; \
    (pbgr)[0] = sat((pyuv)[1] + b); \
    (pbgr)[1] = sat((pyuv)[1] + g); \
    (pbgr)[2] = sat((pyuv)[1] + r); \
    (pbgr)[3] = sat((pyuv)[3] + b); \
    (pbgr)[4] = sat((pyuv)[3] + g); \
    (pbgr)[5] = sat((pyuv)[3] + r); \
    }
#define IUYVY2BGR_16(pyuv, pbgr) IUYVY2BGR_8(pyuv, pbgr); IUYVY2BGR_8(pyuv + 16, pbgr + 24);
#define IUYVY2BGR_8(pyuv, pbgr) IUYVY2BGR_4(pyuv, pbgr); IUYVY2BGR_4(pyuv + 8, pbgr + 12);
#define IUYVY2BGR_4(pyuv, pbgr) IUYVY2BGR_2(pyuv, pbgr); IUYVY2BGR_2(pyuv + 4, pbgr + 6);

/** @brief Convert a frame from UYVY to BGR
 * @ingroup frame
 * @param ini UYVY frame
 * @param out BGR frame
 */
uvc_error_t uvc_uyvy2bgr(uvc_frame_t *in, uvc_frame_t *out) {
  if (in->frame_format != UVC_FRAME_FORMAT_UYVY)
    return UVC_ERROR_INVALID_PARAM;

  if (uvc_ensure_frame_size(out, in->width * in->height * 3) < 0)
    return UVC_ERROR_NO_MEM;

  out->width = in->width;
  out->height = in->height;
  out->frame_format = UVC_FRAME_FORMAT_BGR;
  out->step = in->width *3;
  out->sequence = in->sequence;
  out->capture_time = in->capture_time;
  out->capture_time_finished = in->capture_time_finished;
  out->source = in->source;

  uint8_t *pyuv = in->data;
  uint8_t *pbgr = out->data;
  uint8_t *pbgr_end = pbgr + out->data_bytes;

  while (pbgr < pbgr_end) {
    IUYVY2BGR_8(pyuv, pbgr);

    pbgr += 3 * 8;
    pyuv += 2 * 8;
  }

  return UVC_SUCCESS;
}

/** @brief Convert a frame to RGB
 * @ingroup frame
 *
 * @param in non-RGB frame
 * @param out RGB frame
 */
uvc_error_t uvc_any2rgb(uvc_frame_t *in, uvc_frame_t *out) {
  switch (in->frame_format) {
#ifdef LIBUVC_HAS_JPEG
    case UVC_FRAME_FORMAT_MJPEG:
      return uvc_mjpeg2rgb(in, out);
#endif
    case UVC_FRAME_FORMAT_YUYV:
      return uvc_yuyv2rgb(in, out);
    case UVC_FRAME_FORMAT_UYVY:
      return uvc_uyvy2rgb(in, out);
    case UVC_FRAME_FORMAT_RGB:
      return uvc_duplicate_frame(in, out);
    default:
      return UVC_ERROR_NOT_SUPPORTED;
  }
}

/** @brief Convert a frame to BGR
 * @ingroup frame
 *
 * @param in non-BGR frame
 * @param out BGR frame
 */
uvc_error_t uvc_any2bgr(uvc_frame_t *in, uvc_frame_t *out) {
  switch (in->frame_format) {
    case UVC_FRAME_FORMAT_YUYV:
      return uvc_yuyv2bgr(in, out);
    case UVC_FRAME_FORMAT_UYVY:
      return uvc_uyvy2bgr(in, out);
    case UVC_FRAME_FORMAT_BGR:
      return uvc_duplicate_frame(in, out);
    default:
      return UVC_ERROR_NOT_SUPPORTED;
  }
}

/**
 * @brief Convert a frame to RGBX8888
 * @ingroup frame
 *
 * @param in non-rgbx frame
 * @param out rgbx frame
 * Convert frames to RGBX8888
 */
uvc_error_t uvc_any2rgbx(uvc_frame_t *in, uvc_frame_t *out) {

    switch (in->frame_format) {
#ifdef LIBUVC_HAS_JPEG
        case UVC_FRAME_FORMAT_MJPEG:
		return uvc_mjpeg2rgbx(in, out);
#endif
        case UVC_FRAME_FORMAT_YUYV:
            return uvc_yuyv2rgbx(in, out);
        case UVC_FRAME_FORMAT_UYVY:
            return uvc_uyvy2rgbx(in, out);
        case UVC_FRAME_FORMAT_RGBX:
            return uvc_duplicate_frame(in, out);
        case UVC_FRAME_FORMAT_RGB:
            return uvc_rgb2rgbx(in, out);
        default:
            return UVC_ERROR_NOT_SUPPORTED;
    }
}

#define IYUYV2RGBX_2(pyuv, prgbx, ax, bx) { \
		const int d1 = (pyuv)[ax+1]; \
		const int d3 = (pyuv)[ax+3]; \
		const int r = (22987 * (d3/*(pyuv)[ax+3]*/ - 128)) >> 14; \
		const int g = (-5636 * (d1/*(pyuv)[ax+1]*/ - 128) - 11698 * (d3/*(pyuv)[ax+3]*/ - 128)) >> 14; \
		const int b = (29049 * (d1/*(pyuv)[ax+1]*/ - 128)) >> 14; \
		const int y0 = (pyuv)[ax+0]; \
		(prgbx)[bx+0] = sat(y0 + r); \
		(prgbx)[bx+1] = sat(y0 + g); \
		(prgbx)[bx+2] = sat(y0 + b); \
		(prgbx)[bx+3] = 0xff; \
		const int y2 = (pyuv)[ax+2]; \
		(prgbx)[bx+4] = sat(y2 + r); \
		(prgbx)[bx+5] = sat(y2 + g); \
		(prgbx)[bx+6] = sat(y2 + b); \
		(prgbx)[bx+7] = 0xff; \
    }
#define IYUYV2RGBX_16(pyuv, prgbx, ax, bx) \
	IYUYV2RGBX_8(pyuv, prgbx, ax, bx) \
	IYUYV2RGBX_8(pyuv, prgbx, ax + PIXEL8_YUYV, bx + PIXEL8_RGBX);
#define IYUYV2RGBX_8(pyuv, prgbx, ax, bx) \
	IYUYV2RGBX_4(pyuv, prgbx, ax, bx) \
	IYUYV2RGBX_4(pyuv, prgbx, ax + PIXEL4_YUYV, bx + PIXEL4_RGBX);
#define IYUYV2RGBX_4(pyuv, prgbx, ax, bx) \
	IYUYV2RGBX_2(pyuv, prgbx, ax, bx) \
	IYUYV2RGBX_2(pyuv, prgbx, ax + PIXEL2_YUYV, bx + PIXEL2_RGBX);

#define PIXEL_RGB565		2
#define PIXEL_UYVY			2
#define PIXEL_YUYV			2
#define PIXEL_RGB			3
#define PIXEL_BGR			3
#define PIXEL_RGBX			4

#define PIXEL2_RGB565		(PIXEL_RGB565 * 2)
#define PIXEL2_UYVY			(PIXEL_UYVY * 2)
#define PIXEL2_YUYV			(PIXEL_YUYV * 2)
#define PIXEL2_RGB			(PIXEL_RGB * 2)
#define PIXEL2_BGR			(PIXEL_BGR * 2)
#define PIXEL2_RGBX			(PIXEL_RGBX * 2)

#define PIXEL4_RGB565		(PIXEL_RGB565 * 4)
#define PIXEL4_UYVY			(PIXEL_UYVY * 4)
#define PIXEL4_YUYV			(PIXEL_YUYV * 4)
#define PIXEL4_RGB			(PIXEL_RGB * 4)
#define PIXEL4_BGR			(PIXEL_BGR * 4)
#define PIXEL4_RGBX			(PIXEL_RGBX * 4)

#define PIXEL8_RGB565		(PIXEL_RGB565 * 8)
#define PIXEL8_UYVY			(PIXEL_UYVY * 8)
#define PIXEL8_YUYV			(PIXEL_YUYV * 8)
#define PIXEL8_RGB			(PIXEL_RGB * 8)
#define PIXEL8_BGR			(PIXEL_BGR * 8)
#define PIXEL8_RGBX			(PIXEL_RGBX * 8)

#define PIXEL16_RGB565		(PIXEL_RGB565 * 16)
#define PIXEL16_UYVY		(PIXEL_UYVY * 16)
#define PIXEL16_YUYV		(PIXEL_YUYV * 16)
#define PIXEL16_RGB			(PIXEL_RGB * 16)
#define PIXEL16_BGR			(PIXEL_BGR * 16)
#define PIXEL16_RGBX		(PIXEL_RGBX * 16)

#define RGB2RGBX_2(prgb, prgbx, ax, bx) { \
		(prgbx)[bx+0] = (prgb)[ax+0]; \
		(prgbx)[bx+1] = (prgb)[ax+1]; \
		(prgbx)[bx+2] = (prgb)[ax+2]; \
		(prgbx)[bx+3] = 0xff; \
		(prgbx)[bx+4] = (prgb)[ax+3]; \
		(prgbx)[bx+5] = (prgb)[ax+4]; \
		(prgbx)[bx+6] = (prgb)[ax+5]; \
		(prgbx)[bx+7] = 0xff; \
	}
#define RGB2RGBX_16(prgb, prgbx, ax, bx) \
	RGB2RGBX_8(prgb, prgbx, ax, bx) \
	RGB2RGBX_8(prgb, prgbx, ax + PIXEL8_RGB, bx +PIXEL8_RGBX);
#define RGB2RGBX_8(prgb, prgbx, ax, bx) \
	RGB2RGBX_4(prgb, prgbx, ax, bx) \
	RGB2RGBX_4(prgb, prgbx, ax + PIXEL4_RGB, bx + PIXEL4_RGBX);
#define RGB2RGBX_4(prgb, prgbx, ax, bx) \
	RGB2RGBX_2(prgb, prgbx, ax, bx) \
	RGB2RGBX_2(prgb, prgbx, ax + PIXEL2_RGB, bx + PIXEL2_RGBX);

/** @brief Convert a frame from YUYV to RGBX8888
 * @ingroup frame
 * @param ini YUYV frame
 * @param out RGBX8888 frame
 *
 * Convert frames from YUYV to RGBX8888
 */
uvc_error_t uvc_yuyv2rgbx(uvc_frame_t *in, uvc_frame_t *out) {
    if (UNLIKELY(in->frame_format != UVC_FRAME_FORMAT_YUYV))
        return UVC_ERROR_INVALID_PARAM;

    if (UNLIKELY(uvc_ensure_frame_size(out, in->width * in->height * PIXEL_RGBX) < 0))
        return UVC_ERROR_NO_MEM;

    out->width = in->width;
    out->height = in->height;
    out->frame_format = UVC_FRAME_FORMAT_RGBX;
    if (out->library_owns_data)
        out->step = in->width * PIXEL_RGBX;
    out->sequence = in->sequence;
    out->capture_time = in->capture_time;
    out->source = in->source;

    uint8_t *pyuv = in->data;
    const uint8_t *pyuv_end = pyuv + in->data_bytes - PIXEL8_YUYV;
    uint8_t *prgbx = out->data;
    const uint8_t *prgbx_end = prgbx + out->data_bytes - PIXEL8_RGBX;

    // YUYV => RGBX8888
#if USE_STRIDE
    if (in->step && out->step && (in->step != out->step)) {
		const int hh = in->height < out->height ? in->height : out->height;
		const int ww = in->width < out->width ? in->width : out->width;
		int h, w;
		for (h = 0; h < hh; h++) {
			w = 0;
			pyuv = in->data + in->step * h;
			prgbx = out->data + out->step * h;
			for (; (prgbx <= prgbx_end) && (pyuv <= pyuv_end) && (w < ww) ;) {
				IYUYV2RGBX_8(pyuv, prgbx, 0, 0);

				prgbx += PIXEL8_RGBX;
				pyuv += PIXEL8_YUYV;
				w += 8;
			}
		}
	} else {
		// compressed format? XXX if only one of the frame in / out has step, this may lead to crash...
		for (; (prgbx <= prgbx_end) && (pyuv <= pyuv_end) ;) {
			IYUYV2RGBX_8(pyuv, prgbx, 0, 0);

			prgbx += PIXEL8_RGBX;
			pyuv += PIXEL8_YUYV;
		}
	}
#else
    for (; (prgbx <= prgbx_end) && (pyuv <= pyuv_end) ;) {
        IYUYV2RGBX_8(pyuv, prgbx, 0, 0);

        prgbx += PIXEL8_RGBX;
        pyuv += PIXEL8_YUYV;
    }
#endif
    return UVC_SUCCESS;
}

#define IUYVY2RGBX_2(pyuv, prgbx, ax, bx) { \
		const int d0 = (pyuv)[ax+0]; \
		const int d2 = (pyuv)[ax+2]; \
	    const int r = (22987 * (d2/*(pyuv)[ax+2]*/ - 128)) >> 14; \
	    const int g = (-5636 * (d0/*(pyuv)[ax+0]*/ - 128) - 11698 * (d2/*(pyuv)[ax+2]*/ - 128)) >> 14; \
	    const int b = (29049 * (d0/*(pyuv)[ax+0]*/ - 128)) >> 14; \
		const int y1 = (pyuv)[ax+1]; \
		(prgbx)[bx+0] = sat(y1 + r); \
		(prgbx)[bx+1] = sat(y1 + g); \
		(prgbx)[bx+2] = sat(y1 + b); \
		(prgbx)[bx+3] = 0xff; \
		const int y3 = (pyuv)[ax+3]; \
		(prgbx)[bx+4] = sat(y3 + r); \
		(prgbx)[bx+5] = sat(y3 + g); \
		(prgbx)[bx+6] = sat(y3 + b); \
		(prgbx)[bx+7] = 0xff; \
    }
#define IUYVY2RGBX_16(pyuv, prgbx, ax, bx) \
	IUYVY2RGBX_8(pyuv, prgbx, ax, bx) \
	IUYVY2RGBX_8(pyuv, prgbx, ax + PIXEL8_UYVY, bx + PIXEL8_RGBX)
#define IUYVY2RGBX_8(pyuv, prgbx, ax, bx) \
	IUYVY2RGBX_4(pyuv, prgbx, ax, bx) \
	IUYVY2RGBX_4(pyuv, prgbx, ax + PIXEL4_UYVY, bx + PIXEL4_RGBX)
#define IUYVY2RGBX_4(pyuv, prgbx, ax, bx) \
	IUYVY2RGBX_2(pyuv, prgbx, ax, bx) \
	IUYVY2RGBX_2(pyuv, prgbx, ax + PIXEL2_UYVY, bx + PIXEL2_RGBX)

/** @brief Convert a frame from UYVY to RGBX8888
 * @ingroup frame
 * @param ini UYVY frame
 * @param out RGBX8888 frame
 * Convert frame from UYVY to RGBX8888
 */
uvc_error_t uvc_uyvy2rgbx(uvc_frame_t *in, uvc_frame_t *out) {
    if (UNLIKELY(in->frame_format != UVC_FRAME_FORMAT_UYVY))
        return UVC_ERROR_INVALID_PARAM;

    if (UNLIKELY(uvc_ensure_frame_size(out, in->width * in->height * PIXEL_RGBX) < 0))
        return UVC_ERROR_NO_MEM;

    out->width = in->width;
    out->height = in->height;
    out->frame_format = UVC_FRAME_FORMAT_RGBX;
    if (out->library_owns_data)
        out->step = in->width * PIXEL_RGBX;
    out->sequence = in->sequence;
    out->capture_time = in->capture_time;
    out->source = in->source;

    uint8_t *pyuv = in->data;
    const uint8_t *pyuv_end = pyuv + in->data_bytes - PIXEL8_UYVY;
    uint8_t *prgbx = out->data;
    const uint8_t *prgbx_end = prgbx + out->data_bytes - PIXEL8_RGBX;

    // UYVY => RGBX8888
#if USE_STRIDE
    if (in->step && out->step && (in->step != out->step)) {
		const int hh = in->height < out->height ? in->height : out->height;
		const int ww = in->width < out->width ? in->width : out->width;
		int h, w;
		for (h = 0; h < hh; h++) {
			w = 0;
			pyuv = in->data + in->step * h;
			prgbx = out->data + out->step * h;
			for (; (prgbx <= prgbx_end) && (pyuv <= pyuv_end) && (w < ww) ;) {
				IUYVY2RGBX_8(pyuv, prgbx, 0, 0);

				prgbx += PIXEL8_RGBX;
				pyuv += PIXEL8_UYVY;
				w += 8;
			}
		}
	} else {
		// compressed format? XXX if only one of the frame in / out has step, this may lead to crash...
		for (; (prgbx <= prgbx_end) && (pyuv <= pyuv_end) ;) {
			IUYVY2RGBX_8(pyuv, prgbx, 0, 0);

			prgbx += PIXEL8_RGBX;
			pyuv += PIXEL8_UYVY;
		}
	}
#else
    for (; (prgbx <= prgbx_end) && (pyuv <= pyuv_end) ;) {
        IUYVY2RGBX_8(pyuv, prgbx, 0, 0);

        prgbx += PIXEL8_RGBX;
        pyuv += PIXEL8_UYVY;
    }
#endif
    return UVC_SUCCESS;
}

/** @brief Convert a frame from RGB888 to RGBX8888
 * @ingroup frame
 * @param ini RGB888 frame
 * @param out RGBX8888 frame
 *
 * Convert frames from RGB888 to RGBX8888
 */
uvc_error_t uvc_rgb2rgbx(uvc_frame_t *in, uvc_frame_t *out) {
    if (UNLIKELY(in->frame_format != UVC_FRAME_FORMAT_RGB))
        return UVC_ERROR_INVALID_PARAM;

    if (UNLIKELY(uvc_ensure_frame_size(out, in->width * in->height * PIXEL_RGBX) < 0))
        return UVC_ERROR_NO_MEM;

    out->width = in->width;
    out->height = in->height;
    out->frame_format = UVC_FRAME_FORMAT_RGBX;
    if (out->library_owns_data)
        out->step = in->width * PIXEL_RGBX;
    out->sequence = in->sequence;
    out->capture_time = in->capture_time;
    out->source = in->source;

    uint8_t *prgb = in->data;
    const uint8_t *prgb_end = prgb + in->data_bytes - PIXEL8_RGB;
    uint8_t *prgbx = out->data;
    const uint8_t *prgbx_end = prgbx + out->data_bytes - PIXEL8_RGBX;

    // RGB888 to RGBX8888
#if USE_STRIDE
    if (in->step && out->step && (in->step != out->step)) {
		const int hh = in->height < out->height ? in->height : out->height;
		const int ww = in->width < out->width ? in->width : out->width;
		int h, w;
		for (h = 0; h < hh; h++) {
			w = 0;
			prgb = in->data + in->step * h;
			prgbx = out->data + out->step * h;
			for (; (prgbx <= prgbx_end) && (prgb <= prgb_end) && (w < ww) ;) {
				RGB2RGBX_8(prgb, prgbx, 0, 0);

				prgb += PIXEL8_RGB;
				prgbx += PIXEL8_RGBX;
				w += 8;
			}
		}
	} else {
		// compressed format? XXX if only one of the frame in / out has step, this may lead to crash...
		for (; (prgbx <= prgbx_end) && (prgb <= prgb_end) ;) {
			RGB2RGBX_8(prgb, prgbx, 0, 0);

			prgb += PIXEL8_RGB;
			prgbx += PIXEL8_RGBX;
		}
	}
#else
    for (; (prgbx <= prgbx_end) && (prgb <= prgb_end) ;) {
        RGB2RGBX_8(prgb, prgbx, 0, 0);

        prgb += PIXEL8_RGB;
        prgbx += PIXEL8_RGBX;
    }
#endif
    return UVC_SUCCESS;
}