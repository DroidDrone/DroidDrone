#ifndef LIBUVC_CONFIG_H
#define LIBUVC_CONFIG_H

#include "utilbase.h"

#define LIBUVC_VERSION_MAJOR 0
#define LIBUVC_VERSION_MINOR 0
#define LIBUVC_VERSION_PATCH 7
#define LIBUVC_VERSION_STR "0.0.7"
#define LIBUVC_VERSION_INT \
  (0 << 16) |              \
  (0 << 8) |               \
  (7)

/** @brief Test whether libuvc is new enough
 * This macro evaluates true if and only if the current version is
 * at least as new as the version specified.
 */
#define LIBUVC_VERSION_GTE(major, minor, patch)                         \
  (LIBUVC_VERSION_INT >= (((major) << 16) | ((minor) << 8) | (patch)))

#define LIBUVC_HAS_JPEG
#define LIBUVC_USE_TURBOJPEG

#endif // !def(LIBUVC_CONFIG_H)
