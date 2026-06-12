/* Copyright 2018 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <array>
#include <map>
#include <memory>
#include <string>
#include <utility>

#ifdef HAVE_FEATURED
#include <featured/c_feature_library.h>
#endif
#include <errno.h>
#include <metrics/metrics_library.h>

#include "api/audio/audio_processing.h"
#include "api/audio/builtin_audio_processing_builder.h"
#include "api/audio/echo_canceller3_factory.h"
#include "api/environment/environment_factory.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/task_queue/task_queue_base.h"
#include "api/task_queue/task_queue_factory.h"
#include "modules/audio_processing/aec_dump/aec_dump_factory.h"
#include "modules/audio_processing/include/aec_dump.h"
#include "system_wrappers/include/metrics.h"
#include "webrtc_apm/cras_config/aec_config.h"
#include "webrtc_apm/cras_config/apm_config.h"
#include "webrtc_apm/voice_activity_detector.h"
#include "webrtc_apm/webrtc_apm.h"

using webrtc::metrics::SampleInfo;

static std::string hist_name_prefix_;
static std::unique_ptr<MetricsLibraryInterface> metrics_lib_;

struct WebRtcApm {
  webrtc::scoped_refptr<webrtc::AudioProcessing> apm;
  std::unique_ptr<VoiceActivityDetector> vad;
  WebRtcApmStats stats = {};
  std::unique_ptr<webrtc::TaskQueueBase, webrtc::TaskQueueDeleter>
      aec_dump_work_queue;
};

#if !defined(HAVE_FEATURED) || defined(BYPASS_FEATURE_CHECK)
static WebRtcApmFeatures get_webrtc_apm_features() {
  WebRtcApmFeatures features;
  features.agc2_enabled = false;
  return features;
}
#else
const struct Feature WEBRTC_APM_AGC2_FEATURE = {
    .name = "CrOSWebRTCApmAgc2",
    .default_state = FEATURE_DISABLED_BY_DEFAULT,
};

static WebRtcApmFeatures get_webrtc_apm_features() {
  WebRtcApmFeatures features;
  CFeatureLibrary lib = CFeatureLibraryNew();

  features.agc2_enabled =
      CFeatureLibraryIsEnabledBlocking(lib, &WEBRTC_APM_AGC2_FEATURE);
  syslog(LOG_DEBUG, "Chrome Feature Service: %s = %d", AGC2_FEATURE.name,
         features.agc2_enabled);

  CFeatureLibraryDelete(lib);
  return features;
}
#endif

/* Sets the AGC1 and AGC2 configurations based on whether AGC2 must be used and
 * returns true if the configuration has changed. Handles hybrid configurations
 * when `use_agc2` is false, but an AGC2 fixed digital gain is specificied in
 * `agc2_config`.
 * TODO(crbug.com/1318461): Until analog AGC2 ready, only set `use_agc2` to true
 * for testing purposes.
 */
bool configure_gain_controllers(
    webrtc::AudioProcessing::Config::GainController1* agc1_config,
    webrtc::AudioProcessing::Config::GainController2* agc2_config,
    bool use_agc2,
    bool enable_agc) {
  bool config_changed = false;

  if (!enable_agc) {
    config_changed = config_changed || agc1_config->enabled != enable_agc;
    config_changed = config_changed || agc2_config->enabled != enable_agc;
    agc2_config->enabled = false;
    agc2_config->enabled = false;
    return config_changed;
  }

  if (use_agc2) {
    config_changed = agc1_config->enabled || !agc2_config->enabled ||
                     !agc2_config->adaptive_digital.enabled;

    // Disable AGC1; only one AGC must be active.
    agc1_config->enabled = false;
    // Enable AGC2 and its adaptive digital gain controller.
    // The fixed digital gain configuration is not overridden since `apm_ini`
    // may include device specific tunings.
    agc2_config->enabled = true;
    agc2_config->adaptive_digital.enabled = true;
    // TODO(crbug.com/1318461): Enable AGC2 analog once available.
  } else {
    config_changed = !agc1_config->enabled;

    // Enable AGC1.
    agc1_config->enabled = true;
    agc1_config->mode =
        webrtc::AudioProcessing::Config::GainController1::Mode::kAdaptiveAnalog;
    // Disable AGC2; only one AGC must be active.
    if (agc2_config->fixed_digital.gain_db == 0.0f) {
      config_changed = config_changed || agc2_config->enabled;
      // When the AGC2 fixed digital is not used, entirely disable AGC2.
      agc2_config->enabled = false;
    } else {
      config_changed = config_changed || agc2_config->adaptive_digital.enabled;
      // Explicitly disable AGC2 adaptive controllers, but do not entirely
      // disable AGC2 since it might be used to apply a fixed digital gain.
      agc2_config->adaptive_digital.enabled = false;
      // TODO(crbug.com/1318461): Disable AGC2 analog once available.
    }
  }
  return config_changed;
}

void webrtc_apm_init_metrics(const char* prefix) {
  if (prefix == NULL) {
    return;
  }

  webrtc::metrics::Enable();
  hist_name_prefix_ = prefix;
  metrics_lib_ = std::make_unique<MetricsLibrary>();
}

static webrtc_apm webrtc_apm_create(
    unsigned int num_channels,
    unsigned int frame_rate,
    dictionary* aec_ini,
    dictionary* apm_ini,
    const struct WebRtcApmConfig* webrtc_apm_config,
    WebRtcApmFeatures features) {
  RTC_CHECK(webrtc_apm_config);

  int err;
  webrtc::AudioProcessing* apm;
  webrtc::BuiltinAudioProcessingBuilder apm_builder;
  webrtc::EchoCanceller3Config aec3_config;

  /* Set the AEC-tunings. */
  bool use_aec3_config = false;
  if (aec_ini) {
    aec_config_get(aec_ini, &aec3_config);
    use_aec3_config = true;
  }
  if (webrtc_apm_config->aec3_fixed_capture_delay_samples) {
    aec3_config.delay.fixed_capture_delay_samples =
        webrtc_apm_config->aec3_fixed_capture_delay_samples;
    use_aec3_config = true;
  }
  if (use_aec3_config) {
    std::unique_ptr<webrtc::EchoControlFactory> ec3_factory;
    ec3_factory.reset(new webrtc::EchoCanceller3Factory(aec3_config));
    apm_builder.SetEchoControlFactory(std::move(ec3_factory));
  }

  WebRtcApm* webrtc_apm_state = new WebRtcApm();
  webrtc_apm_state->apm = apm_builder.Build(webrtc::CreateEnvironment());
  apm = webrtc_apm_state->apm.get();

  /* Set the rest of the all settings/tunings. */
  webrtc::AudioProcessing::Config config = apm->GetConfig();
  bool config_changed = false;
  if (aec_ini || webrtc_apm_config->enforce_aec_on) {
    config.echo_canceller.enabled = true;
    /* Activate playout stereo processing by default. This can be
     * turned off in |apm_ini|.
     */
    if (webrtc_apm_config->enforce_aec_on) {
      config.pipeline.multi_channel_render = true;
    }
    config_changed = true;
  }

  if (apm_ini) {
    apm_config_set(apm_ini, &config);
    config_changed = true;
  }

  if (webrtc_apm_config->enforce_ns_on) {
    config.noise_suppression.enabled = true;
    config_changed = true;
  }

  if (webrtc_apm_config->enforce_agc_on) {
    config.capture_level_adjustment.enabled = true;
    config.capture_level_adjustment.analog_mic_gain_emulation.enabled = true;
    configure_gain_controllers(/*agc1_config=*/&config.gain_controller1,
                               /*agc2_config=*/&config.gain_controller2,
                               /*use_agc2=*/features.agc2_enabled,
                               /*enable_agc=*/true);
    config_changed = true;
  }

  if (num_channels > 1) {
    config.pipeline.multi_channel_capture = true;
    config_changed = true;
  }

  if (config_changed) {
    apm->ApplyConfig(config);
  }

  const webrtc::ProcessingConfig processing_config = {{
      webrtc::StreamConfig(frame_rate, num_channels),  // kInputStream
      webrtc::StreamConfig(frame_rate, num_channels),  // kOutputStream
      webrtc::StreamConfig(frame_rate, num_channels),  // kReverseInputStream
      webrtc::StreamConfig(frame_rate, num_channels),  // kReverseOutputStream
  }};
  err = apm->Initialize(processing_config);
  if (err) {
    webrtc_apm_state->apm = nullptr;
    apm = nullptr;
    delete webrtc_apm_state;
    return NULL;
  }

  webrtc_apm_state->vad = VoiceActivityDetector::Create(frame_rate);
  if (webrtc_apm_state->vad == nullptr) {
    delete webrtc_apm_state;
    return NULL;
  }
  return webrtc_apm_state;
}

webrtc_apm webrtc_apm_create_with_enforced_effects(
    unsigned int num_channels,
    unsigned int frame_rate,
    dictionary* aec_ini,
    dictionary* apm_ini,
    const struct WebRtcApmConfig* webrtc_apm_config) {
  return webrtc_apm_create(num_channels, frame_rate, aec_ini, apm_ini,
                           webrtc_apm_config, get_webrtc_apm_features());
}

webrtc_apm webrtc_apm_create_for_testing(
    unsigned int num_channels,
    unsigned int frame_rate,
    dictionary* aec_ini,
    dictionary* apm_ini,
    const struct WebRtcApmConfig* webrtc_apm_config,
    WebRtcApmFeatures features) {
  return webrtc_apm_create(num_channels, frame_rate, aec_ini, apm_ini,
                           webrtc_apm_config, features);
}

void webrtc_apm_enable_vad(webrtc_apm webrtc_apm_ptr, bool enable_vad) {
  webrtc_apm_ptr->vad->Enable(enable_vad);
}

WEBRTC_APM_API void webrtc_apm_enable_effects(webrtc_apm webrtc_apm_ptr,
                                              bool enable_aec,
                                              bool enable_ns,
                                              bool enable_agc) {
  webrtc::AudioProcessing* apm = webrtc_apm_ptr->apm.get();
  webrtc::AudioProcessing::Config config = apm->GetConfig();
  bool config_changed = false;

  WebRtcApmFeatures features = get_webrtc_apm_features();

  config_changed = config.echo_canceller.enabled != enable_aec;
  config.echo_canceller.enabled = enable_aec;

  config_changed =
      config_changed || config.noise_suppression.enabled != enable_ns;
  config.noise_suppression.enabled = enable_ns;

  config_changed =
      config_changed || config.gain_controller1.enabled != enable_agc;
  config.gain_controller1.enabled = enable_agc;

  config_changed =
      config_changed || configure_gain_controllers(
                            /*agc1_config=*/&config.gain_controller1,
                            /*agc2_config=*/&config.gain_controller2,
                            /*use_agc2=*/features.agc2_enabled, enable_agc);

  if (config_changed) {
    apm->ApplyConfig(config);
  }
}

void webrtc_apm_dump_configs(dictionary* apm_ini, dictionary* aec_ini) {
  if (apm_ini) {
    apm_config_dump(apm_ini);
  }
  if (aec_ini) {
    aec_config_dump(aec_ini);
  }
}

int webrtc_apm_process_reverse_stream_f(webrtc_apm webrtc_apm_ptr,
                                        int num_channels,
                                        int rate,
                                        float* const* data) {
  webrtc_apm_ptr->stats.reverse_blocks_processed++;

  webrtc::StreamConfig config = webrtc::StreamConfig(rate, num_channels);
  webrtc::AudioProcessing* apm = webrtc_apm_ptr->apm.get();

  return apm->ProcessReverseStream(data, config, config, data);
}

int webrtc_apm_process_stream_f(webrtc_apm webrtc_apm_ptr,
                                int num_channels,
                                int rate,
                                float* const* data) {
  webrtc_apm_ptr->stats.forward_blocks_processed++;

  webrtc::StreamConfig iconfig = webrtc::StreamConfig(rate, num_channels);
  webrtc::StreamConfig oconfig = webrtc::StreamConfig(rate, num_channels);
  webrtc::AudioProcessing* apm = webrtc_apm_ptr->apm.get();
  int ret = apm->ProcessStream(data, iconfig, oconfig, data);
  webrtc_apm_ptr->vad->Process(data, num_channels, rate);
  return ret;
}

int webrtc_apm_get_voice_detected(webrtc_apm webrtc_apm_ptr) {
  return webrtc_apm_ptr->vad->HasActivity();
}

void webrtc_apm_destroy(webrtc_apm webrtc_apm_ptr) {
  webrtc_apm_ptr->apm = nullptr;
  delete webrtc_apm_ptr;

  std::map<std::string, std::unique_ptr<SampleInfo>, webrtc::AbslStringViewCmp>
      hist;

  if (!metrics_lib_) {
    return;
  }

  webrtc::metrics::GetAndReset(&hist);

  for (auto it = hist.begin(); it != hist.end(); it++) {
    SampleInfo* info = it->second.get();
    std::string name = info->name;
    name.insert(0, hist_name_prefix_);
    /* info->samples stores <value, # of events> */
    for (auto sample = info->samples.begin(); sample != info->samples.end();
         sample++) {
      for (int i = 0; i < sample->second; i++) {
        metrics_lib_->SendToUMA(name, sample->first, info->min, info->max,
                                info->bucket_count);
      }
    }
  }
}

int webrtc_apm_set_stream_delay(webrtc_apm webrtc_apm_ptr, int delay_ms) {
  webrtc::AudioProcessing* apm = webrtc_apm_ptr->apm.get();

  return apm->set_stream_delay_ms(delay_ms);
}

int webrtc_apm_aec_dump(webrtc_apm webrtc_apm_ptr, int start, FILE* handle) {
  webrtc::AudioProcessing* apm = webrtc_apm_ptr->apm.get();

  if (start) {
    if (webrtc_apm_ptr->aec_dump_work_queue) {
      // An aec_dump is already attached, detach it first.
      apm->DetachAecDump();
    }
    webrtc_apm_ptr->aec_dump_work_queue =
        webrtc::CreateDefaultTaskQueueFactory()->CreateTaskQueue(
            "aecdump-worker-queue", webrtc::TaskQueueFactory::Priority::LOW);
    if (!apm->CreateAndAttachAecDump(
            handle, -1, webrtc_apm_ptr->aec_dump_work_queue.get())) {
      return -ENOMEM;
    }
  } else {
    apm->DetachAecDump();
    webrtc_apm_ptr->aec_dump_work_queue.reset();
  }
  return 0;
}

struct WebRtcApmActiveEffects webrtc_apm_get_active_effects(
    webrtc_apm webrtc_apm_ptr) {
  webrtc::AudioProcessing::Config config = webrtc_apm_ptr->apm->GetConfig();
  return {
      .echo_cancellation = config.echo_canceller.enabled,
      .noise_suppression = config.noise_suppression.enabled,
      .voice_activity_detection = webrtc_apm_ptr->vad->IsEnabled(),
  };
}

struct WebRtcApmStats webrtc_apm_get_stats(webrtc_apm webrtc_apm_ptr) {
  return webrtc_apm_ptr->stats;
}
