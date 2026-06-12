#include "modules/audio_processing/vad/voice_activity_detector.h"

#include "modules/audio_processing/vad/pitch_based_vad.h"
#include "modules/audio_processing/vad/standalone_vad.h"
#include "modules/audio_processing/vad/vad_audio_proc.h"
#include "modules/audio_processing/vad/pole_zero_filter.h"
#include "modules/audio_processing/vad/vad_circular_buffer.h"

namespace webrtc {

VadAudioProc::VadAudioProc() = default;

VadAudioProc::~VadAudioProc() = default;

int VadAudioProc::ExtractFeatures(const int16_t* audio_frame,
                                  size_t length,
                                  AudioFeatures* audio_features) {
  return 0;
}

PitchBasedVad::PitchBasedVad() = default;

PitchBasedVad::~PitchBasedVad() = default;

int PitchBasedVad::VoicingProbability(const AudioFeatures& features,
                                      double* p_combined) {
  return 0;
}

StandaloneVad* StandaloneVad::Create() {
  return nullptr;
}

StandaloneVad::~StandaloneVad() = default;

int StandaloneVad::GetActivity(double* p, size_t length_p) {
  return -1;
}

int StandaloneVad::AddAudio(const int16_t* data, size_t length) {
  return 0;
}

int StandaloneVad::set_mode(int mode) {
  return 0;
}

VoiceActivityDetector::VoiceActivityDetector()
    : last_voice_probability_(1.0f) {}

VoiceActivityDetector::~VoiceActivityDetector() = default;

void VoiceActivityDetector::ProcessChunk(const int16_t* audio,
                                         size_t length,
                                         int sample_rate_hz) {
  chunkwise_voice_probabilities_.clear();
  chunkwise_rms_.clear();

  chunkwise_voice_probabilities_.push_back(1.0);
  chunkwise_rms_.push_back(0.0);
  last_voice_probability_ = 1.0f;
}

}  // namespace webrtc