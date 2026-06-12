#ifndef COMPAT_ANDROID_CPU_FEATURES_H_
#define COMPAT_ANDROID_CPU_FEATURES_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define ANDROID_CPU_FAMILY_UNKNOWN 0
#define ANDROID_CPU_FAMILY_ARM 1
#define ANDROID_CPU_FAMILY_X86 2
#define ANDROID_CPU_FAMILY_MIPS 3
#define ANDROID_CPU_FAMILY_ARM64 4
#define ANDROID_CPU_FAMILY_X86_64 5

#define ANDROID_CPU_ARM_FEATURE_ARMv7 1
#define ANDROID_CPU_ARM_FEATURE_VFPv3 (1 << 1)
#define ANDROID_CPU_ARM_FEATURE_NEON (1 << 2)
#define ANDROID_CPU_ARM_FEATURE_LDREX_STREX (1 << 3)
#define ANDROID_CPU_ARM_FEATURE_VFPv2 (1 << 4)
#define ANDROID_CPU_ARM_FEATURE_VFP_D32 (1 << 5)
#define ANDROID_CPU_ARM_FEATURE_VFP_FP16 (1 << 6)
#define ANDROID_CPU_ARM_FEATURE_VFP_FMA (1 << 7)
#define ANDROID_CPU_ARM_FEATURE_NEON_FMA (1 << 8)
#define ANDROID_CPU_ARM_FEATURE_IDIV_ARM (1 << 9)
#define ANDROID_CPU_ARM_FEATURE_IDIV_THUMB2 (1 << 10)
#define ANDROID_CPU_ARM_FEATURE_iWMMXt (1 << 11)
#define ANDROID_CPU_ARM_FEATURE_AES (1 << 12)
#define ANDROID_CPU_ARM_FEATURE_PMULL (1 << 13)
#define ANDROID_CPU_ARM_FEATURE_SHA1 (1 << 14)
#define ANDROID_CPU_ARM_FEATURE_SHA2 (1 << 15)
#define ANDROID_CPU_ARM_FEATURE_CRC32 (1 << 16)

static inline int android_getCpuFamily(void) {
#if defined(__aarch64__)
  return ANDROID_CPU_FAMILY_ARM64;
#elif defined(__arm__)
  return ANDROID_CPU_FAMILY_ARM;
#elif defined(__x86_64__)
  return ANDROID_CPU_FAMILY_X86_64;
#elif defined(__i386__)
  return ANDROID_CPU_FAMILY_X86;
#else
  return ANDROID_CPU_FAMILY_UNKNOWN;
#endif
}

static inline uint64_t android_getCpuFeatures(void) {
#if defined(__aarch64__)
  return ANDROID_CPU_ARM_FEATURE_NEON;
#elif defined(__ARM_NEON) || defined(__ARM_NEON__)
  return ANDROID_CPU_ARM_FEATURE_NEON;
#else
  return 0;
#endif
}

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // COMPAT_ANDROID_CPU_FEATURES_H_
