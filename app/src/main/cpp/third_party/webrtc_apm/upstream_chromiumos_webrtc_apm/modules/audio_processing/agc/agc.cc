/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/agc/agc.h"

#include <cmath>
#include <cstdint>
#include <cstdlib>

#include "api/array_view.h"
#include "modules/audio_processing/agc/loudness_histogram.h"
#include "modules/audio_processing/agc/utility.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace {

constexpr int kDefaultLevelDbfs = -18;

}  // namespace

Agc::Agc()
    : target_level_loudness_(Dbfs2Loudness(kDefaultLevelDbfs)),
      target_level_dbfs_(kDefaultLevelDbfs),
      histogram_(LoudnessHistogram::Create()),
      inactive_histogram_(LoudnessHistogram::Create()) {}

Agc::~Agc() = default;

void Agc::Process(ArrayView<const int16_t> audio) {
  // No-op for minimal Android standalone APM build.
  // This project currently uses AEC + NS only. Legacy AGC/VAD analysis is
  // intentionally disabled to avoid pulling the full VAD/iSAC dependency chain.
}

bool Agc::GetRmsErrorDb(int* error) {
  if (!error) {
    RTC_DCHECK_NOTREACHED();
    return false;
  }

  // No AGC level update in minimal AEC + NS mode.
  return false;
}

void Agc::Reset() {
  if (histogram_) {
    histogram_->Reset();
  }
  if (inactive_histogram_) {
    inactive_histogram_->Reset();
  }
}

int Agc::set_target_level_dbfs(int level) {
  if (level >= 0 || level <= -100) {
    return -1;
  }

  target_level_dbfs_ = level;
  target_level_loudness_ = Dbfs2Loudness(level);
  return 0;
}

int Agc::target_level_dbfs() const {
  return target_level_dbfs_;
}

float Agc::voice_probability() const {
  // Always report active voice for the minimal no-op AGC path.
  return 1.0f;
}

}  // namespace webrtc