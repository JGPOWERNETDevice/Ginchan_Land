/* Copyright 2023 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
#ifndef VOICE_ACTIVITY_DETECTOR_H_
#define VOICE_ACTIVITY_DETECTOR_H_

#include <cstdint>
#include <memory>

// Voice/Speech activity detector.
// It uses WebRTC voice activity detector available in common_audio/vad
class VoiceActivityDetector {
 public:
  virtual ~VoiceActivityDetector() = default;

  static std::unique_ptr<VoiceActivityDetector> Create(int sample_rate);

  // Process 10ms of audio. If disabled it return directly.
  virtual void Process(float* const* audio, int num_channels, int rate) = 0;
  virtual bool HasActivity() const = 0;
  virtual void Enable(bool enable) = 0;
  virtual bool IsEnabled() const = 0;
};

#endif  // VOICE_ACTIVITY_DETECTOR_H_
