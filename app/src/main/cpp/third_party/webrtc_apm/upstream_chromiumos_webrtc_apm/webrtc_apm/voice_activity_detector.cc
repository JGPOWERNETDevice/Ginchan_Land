/* Copyright 2023 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
#include "webrtc_apm/voice_activity_detector.h"

#include <algorithm>
#include <array>
#include <cstdint>
#include <limits>
#include <memory>

#include "common_audio/resampler/push_sinc_resampler.h"
#include "common_audio/vad/include/webrtc_vad.h"
#include "rtc_base/checks.h"

namespace {

constexpr int kNumSamples10MsAt8k = 80;

class VoiceActivityDetectorImpl : public VoiceActivityDetector {
 public:
  VoiceActivityDetectorImpl(int sample_rate, VadInst* vad);
  ~VoiceActivityDetectorImpl() {
    // `vad_` cannot be null as it can only be created via
    // VoiceActivityDetector::Create which take care of NULL value.
    WebRtcVad_Free(vad_);
  }

  void Process(float* const* audio, int num_channels, int rate) override;

  bool HasActivity() const override { return active_speech_; }
  void Enable(bool enable) override { voice_detection_enabled_ = enable; }

  bool IsEnabled() const override { return voice_detection_enabled_; }

 private:
  int sample_rate_;
  int ten_ms_buffer_size_;
  bool active_speech_ = false;
  bool voice_detection_enabled_ = false;
  std::unique_ptr<webrtc::PushSincResampler> resampler_;
  VadInst* vad_;
  std::array<int16_t, kNumSamples10MsAt8k> int16_buffer_;
  std::array<float, kNumSamples10MsAt8k> float_buffer_;
};

}  // namespace

std::unique_ptr<VoiceActivityDetector> VoiceActivityDetector::Create(
    int sample_rate) {
  VadInst* vad = WebRtcVad_Create();
  if (vad == nullptr || WebRtcVad_Init(vad) != 0) {
    if (vad) {
      WebRtcVad_Free(vad);
    }
    return nullptr;
  }
  return std::make_unique<VoiceActivityDetectorImpl>(sample_rate, vad);
}

VoiceActivityDetectorImpl::VoiceActivityDetectorImpl(int sample_rate,
                                                     VadInst* vad)
    : sample_rate_(sample_rate),
      ten_ms_buffer_size_(sample_rate * 10 / 1000),
      resampler_(
          std::make_unique<webrtc::PushSincResampler>(ten_ms_buffer_size_,
                                                      kNumSamples10MsAt8k)),
      vad_(vad) {
  // Set the VAD in most aggressive mode.
  WebRtcVad_set_mode(vad_, /*mode=*/3);
}

void VoiceActivityDetectorImpl::Process(float* const* audio,
                                        int num_channels,
                                        int sample_rate) {
  active_speech_ = false;
  if (!voice_detection_enabled_) {
    return;
  }

  if (sample_rate_ != sample_rate) {
    ten_ms_buffer_size_ = sample_rate_ * 10 / 1000;
    sample_rate_ = sample_rate;
    resampler_ = std::make_unique<webrtc::PushSincResampler>(
        ten_ms_buffer_size_, kNumSamples10MsAt8k);
  }

  if (resampler_->Resample(audio[0], ten_ms_buffer_size_, float_buffer_.data(),
                           kNumSamples10MsAt8k) != kNumSamples10MsAt8k) {
    return;
  }

  std::transform(float_buffer_.begin(), float_buffer_.end(),
                 int16_buffer_.begin(), [](float x) {
                   constexpr float kMaxInt16 =
                       static_cast<float>(std::numeric_limits<int16_t>::max());
                   constexpr float kMinInt16 =
                       static_cast<float>(std::numeric_limits<int16_t>::min());
                   constexpr float kMaxInt16Plus1 = kMaxInt16 + 1.0f;
                   return static_cast<int16_t>(
                       std::clamp(x * kMaxInt16Plus1, kMinInt16, kMaxInt16));
                 });

  const int vad = WebRtcVad_Process(vad_, /*rate=*/8000, int16_buffer_.data(),
                                    kNumSamples10MsAt8k);
  RTC_DCHECK(vad == 1 || vad == 0);
  active_speech_ = vad == 1 ? true : false;
}
