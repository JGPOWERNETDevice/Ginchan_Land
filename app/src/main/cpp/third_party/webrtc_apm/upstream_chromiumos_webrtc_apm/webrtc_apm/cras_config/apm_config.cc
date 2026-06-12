/* Copyright 2018 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include "apm_config.h"

#include <syslog.h>

#define APM_CONFIG_NAME "apm.ini"

#define APM_GET_INT(ini, key) iniparser_getint(ini, key, key##_VALUE)
#define APM_GET_FLOAT(ini, key) iniparser_getdouble(ini, key, key##_VALUE)

typedef webrtc::AudioProcessing::Config ApConfig;

void apm_config_set(dictionary* ini, webrtc::AudioProcessing::Config* config) {
  if (ini == NULL) {
    return;
  }

  syslog(LOG_INFO, "Reading APM ini");

  /* Keys for AudioProcessing::Pipeline */
  config->pipeline.maximum_internal_processing_rate =
      APM_GET_INT(ini, PIPELINE_MAXIMUM_INTERNAL_PROCESSING_RATE);
  config->pipeline.multi_channel_render =
      APM_GET_INT(ini, PIPELINE_MULTI_CHANNEL_RENDER);
  config->pipeline.multi_channel_capture =
      APM_GET_INT(ini, PIPELINE_MULTI_CHANNEL_CAPTURE);

  /* Keys for AudioProcessing::PreAmplifier */
  config->pre_amplifier.enabled = APM_GET_INT(ini, APM_PRE_AMPLIFIER_ENABLED);
  config->pre_amplifier.fixed_gain_factor =
      APM_GET_FLOAT(ini, APM_PRE_AMPLIFIER_FIXED_GAIN_FACTOR);

  /* Keys for AudioProcessing::CaptureLevelAdjustment */
  config->capture_level_adjustment.enabled =
      APM_GET_INT(ini, CAPTURE_LEVEL_ADJUSTMENT_ENABLED);
  config->capture_level_adjustment.pre_gain_factor =
      APM_GET_FLOAT(ini, CAPTURE_LEVEL_ADJUSTMENT_PRE_GAIN_FACTOR);
  config->capture_level_adjustment.post_gain_factor =
      APM_GET_FLOAT(ini, CAPTURE_LEVEL_ADJUSTMENT_POST_GAIN_FACTOR);

  /* Keys for AudioProcessing::CaptureLevelAdjustment::AnalogMicGainEmulation */
  config->capture_level_adjustment.analog_mic_gain_emulation.enabled =
      APM_GET_INT(ini,
                  CAPTURE_LEVEL_ADJUSTMENT_ANALOG_MIC_GAIN_EMULATION_ENABLED);
  config->capture_level_adjustment.analog_mic_gain_emulation.initial_level =
      APM_GET_INT(
          ini,
          CAPTURE_LEVEL_ADJUSTMENT_ANALOG_MIC_GAIN_EMULATION_INITIAL_LEVEL);

  /* Keys for AudioProcessing::HighPassFilter */
  config->high_pass_filter.enabled =
      APM_GET_INT(ini, APM_HIGH_PASS_FILTER_ENABLED);
  config->high_pass_filter.apply_in_full_band =
      APM_GET_INT(ini, APM_HIGH_PASS_FILTER_APPLY_IN_FULL_BAND);

  /* Keys for AudioProcessing::EchoCanceller */
  config->echo_canceller.enabled = APM_GET_INT(ini, APM_ECHO_CANCELLER_ENABLED);
  config->echo_canceller.mobile_mode =
      APM_GET_INT(ini, APM_ECHO_CANCELLER_MOBILE_MODE);
  config->echo_canceller.export_linear_aec_output =
      APM_GET_INT(ini, APM_ECHO_CANCELLER_EXPORT_LINEAR_AEC_OUTPUT);
  config->echo_canceller.enforce_high_pass_filtering =
      APM_GET_INT(ini, APM_ECHO_CANCELLER_ENFORCE_HIGH_PASS_FILTERING);

  /* Keys for AudioProcessing::NoiseSuppression */
  config->noise_suppression.enabled =
      APM_GET_INT(ini, APM_NOISE_SUPPRESSION_ENABLED);
  config->noise_suppression.level =
      static_cast<ApConfig::NoiseSuppression::Level>(
          APM_GET_INT(ini, APM_NOISE_SUPPRESSION_LEVEL));
  config->noise_suppression.analyze_linear_aec_output_when_available =
      APM_GET_INT(
          ini, APM_NOISE_SUPPRESSION_ANALYZE_LINEAR_AEC_OUTPUT_WHEN_AVAILABLE);

  /* Keys for AudioProcessing::TransientSuppression */
  config->transient_suppression.enabled =
      APM_GET_INT(ini, APM_TRANSIENT_SUPPRESSION_ENABLED);

  /* Keys for AudioProcessing::GainController1 */
  config->gain_controller1.enabled = APM_GET_INT(ini, APM_GAIN_CONTROL_ENABLED);
  config->gain_controller1.mode = static_cast<ApConfig::GainController1::Mode>(
      APM_GET_INT(ini, APM_GAIN_CONTROL_MODE));
  config->gain_controller1.target_level_dbfs =
      APM_GET_INT(ini, APM_GAIN_CONTROL_TARGET_LEVEL_DBFS);
  config->gain_controller1.compression_gain_db =
      APM_GET_INT(ini, APM_GAIN_CONTROL_COMPRESSION_GAIN_DB);
  config->gain_controller1.enable_limiter =
      APM_GET_INT(ini, APM_GAIN_CONTROL_ENABLE_LIMITER);

  /* Keys for AudioProcessing::GainController1::AnalogGainController */
  config->gain_controller1.analog_gain_controller.enabled =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_ENABLED);
  config->gain_controller1.analog_gain_controller.startup_min_volume =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_STARTUP_MIN_VOLUME);
  config->gain_controller1.analog_gain_controller.clipped_level_min =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_LEVEL_MIN);
  config->gain_controller1.analog_gain_controller.enable_digital_adaptive =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_ENABLE_DIGITAL_ADAPTIVE);
  config->gain_controller1.analog_gain_controller.clipped_level_step =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_LEVEL_STEP);
  config->gain_controller1.analog_gain_controller.clipped_ratio_threshold =
      APM_GET_FLOAT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_RATIO_THRESHOLD);
  config->gain_controller1.analog_gain_controller.clipped_wait_frames =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_WAIT_FRAMES);
  config->gain_controller1.analog_gain_controller.clipping_predictor.enabled =
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_ENABLED);
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .mode = static_cast<
      ApConfig::GainController1::AnalogGainController::ClippingPredictor::Mode>(
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_MODE));
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .window_length = APM_GET_INT(
      ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_WINDOW_LENGTH);
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .reference_window_length = APM_GET_INT(
      ini,
      APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_REFERENCE_WINDOW_LENGTH);
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .reference_window_delay = APM_GET_INT(
      ini,
      APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_REFERENCE_WINDOW_DELAY);
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .clipping_threshold = APM_GET_FLOAT(
      ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_CLIPPING_THRESHOLD);
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .crest_factor_margin = APM_GET_FLOAT(
      ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_CREST_FACTOR_MARGIN);
  config->gain_controller1.analog_gain_controller.clipping_predictor
      .use_predicted_step = APM_GET_INT(
      ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_USE_PREDICTED_STEP);

  /* Keys for AudioProcessing::GainController2 */
  config->gain_controller2.enabled =
      APM_GET_INT(ini, APM_GAIN_CONTROLLER2_ENABLED);

  /* Keys for AudioProcessing::GainController2::FixedDigital */
  config->gain_controller2.fixed_digital.gain_db =
      APM_GET_FLOAT(ini, APM_GAIN_CONTROLLER2_FIXED_DIGITAL_GAIN_DB);

  /* Keys for AudioProcessing::GainController2::AdaptiveDigital */
  config->gain_controller2.adaptive_digital.headroom_db =
      APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_HEADROOM_DB);
  config->gain_controller2.adaptive_digital.max_gain_db =
      APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_MAX_GAIN_DB);
  config->gain_controller2.adaptive_digital.max_gain_change_db_per_second =
      APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_MAX_GAIN_CHANGE_DB_PER_SECOND);
  config->gain_controller2.adaptive_digital.max_output_noise_level_dbfs =
      APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_MAX_OUTPUT_NOISE_LEVEL_DBFS);
}

void apm_config_dump(dictionary* ini) {
  syslog(LOG_ERR, "---- apm config dump ----");

  /* Keys for AudioProcessing::Pipeline */
  syslog(LOG_ERR, "pipeline_maximum_internal_processing_rate %u",
         APM_GET_INT(ini, PIPELINE_MAXIMUM_INTERNAL_PROCESSING_RATE));
  syslog(LOG_ERR, "pipeline_multi_channel_render %u",
         APM_GET_INT(ini, PIPELINE_MULTI_CHANNEL_RENDER));
  syslog(LOG_ERR, "pipeline_multi_channel_capture %u",
         APM_GET_INT(ini, PIPELINE_MULTI_CHANNEL_CAPTURE));

  /* Keys for AudioProcessing::PreAmplifier */
  syslog(LOG_ERR, "pre_amplifier_enabled %u",
         APM_GET_INT(ini, APM_PRE_AMPLIFIER_ENABLED));
  syslog(LOG_ERR, "pre_amplifier_fixed_gain_factor %f",
         APM_GET_FLOAT(ini, APM_PRE_AMPLIFIER_FIXED_GAIN_FACTOR));

  /* Keys for AudioProcessing::CaptureLevelAdjustment */
  syslog(LOG_ERR, "capture_level_adjustment_enabled %u",
         APM_GET_INT(ini, CAPTURE_LEVEL_ADJUSTMENT_ENABLED));
  syslog(LOG_ERR, "capture_level_adjustment_pre_gain_factor %f",
         APM_GET_FLOAT(ini, CAPTURE_LEVEL_ADJUSTMENT_PRE_GAIN_FACTOR));
  syslog(LOG_ERR, "capture_level_adjustment_post_gain_factor %f",
         APM_GET_FLOAT(ini, CAPTURE_LEVEL_ADJUSTMENT_POST_GAIN_FACTOR));

  /* Keys for AudioProcessing::CaptureLevelAdjustment::AnalogMicGainEmulation */
  syslog(LOG_ERR,
         "capture_level_adjustment_analog_mic_gain_emulation_enabled %u",
         APM_GET_INT(
             ini, CAPTURE_LEVEL_ADJUSTMENT_ANALOG_MIC_GAIN_EMULATION_ENABLED));
  syslog(LOG_ERR,
         "capture_level_adjustment_analog_mic_gain_emulation_initial_level %u",
         APM_GET_INT(
             ini,
             CAPTURE_LEVEL_ADJUSTMENT_ANALOG_MIC_GAIN_EMULATION_INITIAL_LEVEL));

  /* Keys for AudioProcessing::HighPassFilter */
  syslog(LOG_ERR, "high_pass_filter_enabled %u",
         APM_GET_INT(ini, APM_HIGH_PASS_FILTER_ENABLED));
  syslog(LOG_ERR, "high_pass_filter_apply_in_full_band %d",
         APM_GET_INT(ini, APM_HIGH_PASS_FILTER_APPLY_IN_FULL_BAND));

  /* Keys for AudioProcessing::EchoCanceller */
  syslog(LOG_ERR, "echo_canceller_enabled %d",
         APM_GET_INT(ini, APM_ECHO_CANCELLER_ENABLED));
  syslog(LOG_ERR, "echo_canceller_mobile_mode %d",
         APM_GET_INT(ini, APM_ECHO_CANCELLER_MOBILE_MODE));
  syslog(LOG_ERR, "echo_canceller_export_linear_aec_output %d",
         APM_GET_INT(ini, APM_ECHO_CANCELLER_EXPORT_LINEAR_AEC_OUTPUT));
  syslog(LOG_ERR, "echo_canceller_enforce_high_pass_filtering %d",
         APM_GET_INT(ini, APM_ECHO_CANCELLER_ENFORCE_HIGH_PASS_FILTERING));

  /* Keys for AudioProcessing::NoiseSuppression */
  syslog(LOG_ERR, "noise_suppression_enabled %u",
         APM_GET_INT(ini, APM_NOISE_SUPPRESSION_ENABLED));
  syslog(LOG_ERR, "noise_suppression_level %u",
         APM_GET_INT(ini, APM_NOISE_SUPPRESSION_LEVEL));
  syslog(
      LOG_ERR, "noise_suppression_analyze_linear_aec_output_when_available %u",
      APM_GET_INT(
          ini, APM_NOISE_SUPPRESSION_ANALYZE_LINEAR_AEC_OUTPUT_WHEN_AVAILABLE));

  /* Keys for AudioProcessing::TransientSuppression */
  syslog(LOG_ERR, "transient_suppression_enabled %d",
         APM_GET_INT(ini, APM_TRANSIENT_SUPPRESSION_ENABLED));

  /* Keys for AudioProcessing::GainController1 */
  syslog(LOG_ERR, "gain_control_enabled %u",
         APM_GET_INT(ini, APM_GAIN_CONTROL_ENABLED));
  syslog(LOG_ERR, "gain_control_mode %u",
         APM_GET_INT(ini, APM_GAIN_CONTROL_MODE));
  syslog(LOG_ERR, "gain_control_target_level_dbfs %d",
         APM_GET_INT(ini, APM_GAIN_CONTROL_TARGET_LEVEL_DBFS));
  syslog(LOG_ERR, "gain_control_compression_gain_db %u",
         APM_GET_INT(ini, APM_GAIN_CONTROL_COMPRESSION_GAIN_DB));
  syslog(LOG_ERR, "gain_control_enable_limiter %d",
         APM_GET_INT(ini, APM_GAIN_CONTROL_ENABLE_LIMITER));

  /* Keys for AudioProcessing::GainController1::AnalogGainController */
  syslog(LOG_ERR, "analog_gain_controller_enabled %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_ENABLED));
  syslog(LOG_ERR, "analog_gain_controller_startup_min_volume %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_STARTUP_MIN_VOLUME));
  syslog(LOG_ERR, "analog_gain_controller_clipped_level_min %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_LEVEL_MIN));
  syslog(LOG_ERR, "analog_gain_controller_enable_digital_adaptive %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_ENABLE_DIGITAL_ADAPTIVE));
  syslog(LOG_ERR, "analog_gain_controller_clipped_level_step %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_LEVEL_STEP));
  syslog(
      LOG_ERR, "analog_gain_controller_clipped_ratio_threshold %f",
      APM_GET_FLOAT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_RATIO_THRESHOLD));
  syslog(LOG_ERR, "analog_gain_controller_clipped_wait_frames %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPED_WAIT_FRAMES));
  syslog(
      LOG_ERR, "analog_gain_controller_clipping_predictor_enabled %d",
      APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_ENABLED));
  syslog(LOG_ERR, "analog_gain_controller_clipping_predictor_mode %d",
         APM_GET_INT(ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_MODE));
  syslog(LOG_ERR, "analog_gain_controller_clipping_predictor_window_length %d",
         APM_GET_INT(
             ini, APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_WINDOW_LENGTH));
  syslog(
      LOG_ERR,
      "analog_gain_controller_clipping_predictor_reference_window_length %d",
      APM_GET_INT(
          ini,
          APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_REFERENCE_WINDOW_LENGTH));
  syslog(
      LOG_ERR,
      "analog_gain_controller_clipping_predictor_reference_window_delay %d",
      APM_GET_INT(
          ini,
          APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_REFERENCE_WINDOW_DELAY));
  syslog(LOG_ERR,
         "analog_gain_controller_clipping_predictor_clipping_threshold %f",
         APM_GET_FLOAT(
             ini,
             APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_CLIPPING_THRESHOLD));
  syslog(
      LOG_ERR,
      "analog_gain_controller_clipping_predictor_crest_factor_margin %f",
      APM_GET_FLOAT(
          ini,
          APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_CREST_FACTOR_MARGIN));
  syslog(LOG_ERR,
         "analog_gain_controller_clipping_predictor_use_predicted_step %d",
         APM_GET_INT(
             ini,
             APM_ANALOG_GAIN_CONTROLLER_CLIPPING_PREDICTOR_USE_PREDICTED_STEP));

  /* Keys for AudioProcessing::GainController2 */
  syslog(LOG_ERR, "gain_controller2_enabled %u",
         APM_GET_INT(ini, APM_GAIN_CONTROLLER2_ENABLED));

  /* Keys for AudioProcessing::GainController2::FixedDigital */
  syslog(LOG_ERR, "gain_controller2_fixed_digital_gain_db %f",
         APM_GET_FLOAT(ini, APM_GAIN_CONTROLLER2_FIXED_DIGITAL_GAIN_DB));

  /* Keys for AudioProcessing::GainController2::AdaptiveDigital */
  syslog(LOG_ERR, "adaptive_digital_headroom_db %f",
         APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_HEADROOM_DB));
  syslog(LOG_ERR, "adaptive_digital_max_gain_db %f",
         APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_MAX_GAIN_DB));
  syslog(LOG_ERR, "adaptive_digital_max_gain_change_db_per_second %f",
         APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_MAX_GAIN_CHANGE_DB_PER_SECOND));
  syslog(LOG_ERR, "adaptive_digital_max_output_noise_level_dbfs %f",
         APM_GET_FLOAT(ini, ADAPTIVE_DIGITAL_MAX_OUTPUT_NOISE_LEVEL_DBFS));
}
