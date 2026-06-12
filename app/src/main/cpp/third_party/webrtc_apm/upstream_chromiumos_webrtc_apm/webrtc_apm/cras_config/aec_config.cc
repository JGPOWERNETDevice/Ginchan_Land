/* Copyright 2018 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include "aec_config.h"

#include <syslog.h>

#define AEC_CONFIG_NAME "aec.ini"

#define AEC_GET_INT(ini, category, key) \
  iniparser_getint(ini, AEC_##category##_##key, AEC_##category##_##key##_VALUE)

#define AEC_GET_FLOAT(ini, category, key)          \
  iniparser_getdouble(ini, AEC_##category##_##key, \
                      AEC_##category##_##key##_VALUE)

void aec_config_get(dictionary* ini, webrtc::EchoCanceller3Config* config) {
  if (ini == NULL) {
    return;
  }

  syslog(LOG_INFO, "Reading AEC3 ini");

  /* Keys for EchoCanceller3Config:Buffering */
  config->buffering.excess_render_detection_interval_blocks =
      AEC_GET_INT(ini, BUFFERING, EXCESS_RENDER_DETECTION_INTERVAL_BLOCKS);
  config->buffering.max_allowed_excess_render_blocks =
      AEC_GET_INT(ini, BUFFERING, MAX_ALLOWED_EXCESS_RENDER_BLOCKS);

  /* Keys for EchoCanceller3Config:Delay */
  config->delay.default_delay = AEC_GET_INT(ini, DELAY, DEFAULT_DELAY);
  config->delay.down_sampling_factor =
      AEC_GET_INT(ini, DELAY, DOWN_SAMPLING_FACTOR);
  config->delay.num_filters = AEC_GET_INT(ini, DELAY, NUM_FILTERS);
  config->delay.delay_headroom_samples =
      AEC_GET_INT(ini, DELAY, DELAY_HEADROOM_SAMPLES);
  config->delay.hysteresis_limit_blocks =
      AEC_GET_INT(ini, DELAY, HYSTERESIS_LIMIT_BLOCKS);
  config->delay.fixed_capture_delay_samples =
      AEC_GET_INT(ini, DELAY, FIXED_CAPTURE_DELAY_SAMPLES);
  config->delay.delay_estimate_smoothing =
      AEC_GET_FLOAT(ini, DELAY, DELAY_ESTIMATE_SMOOTHING);

  config->delay.delay_candidate_detection_threshold =
      AEC_GET_FLOAT(ini, DELAY, DELAY_CANDIDATE_DETECTION_THRESHOLD);
  config->delay.delay_selection_thresholds.initial =
      AEC_GET_INT(ini, DELAY, DELAY_SELECTION_THRESHOLD_INITIAL);
  config->delay.delay_selection_thresholds.converged =
      AEC_GET_INT(ini, DELAY, DELAY_SELECTION_THRESHOLD_CONVERGED);

  config->delay.use_external_delay_estimator =
      AEC_GET_INT(ini, DELAY, USE_EXTERNAL_DELAY_ESTIMATOR);
  config->delay.log_warning_on_delay_changes =
      AEC_GET_INT(ini, DELAY, LOG_WARNING_ON_DELAY_CHANGES);

  config->delay.render_alignment_mixing.downmix =
      AEC_GET_INT(ini, DELAY, RENDER_ALIGNMENT_MIXING_DOWNMIX);
  config->delay.render_alignment_mixing.adaptive_selection =
      AEC_GET_INT(ini, DELAY, RENDER_ALIGNMENT_MIXING_ADAPTIVE_SELECTION);
  config->delay.render_alignment_mixing.activity_power_threshold =
      AEC_GET_FLOAT(ini, DELAY,
                    RENDER_ALIGNMENT_MIXING_ACTIVITY_POWER_THRESHOLD);
  config->delay.render_alignment_mixing.prefer_first_two_channels = AEC_GET_INT(
      ini, DELAY, RENDER_ALIGNMENT_MIXING_PREFER_FIRST_TWO_CHANNELS);
  config->delay.capture_alignment_mixing.downmix =
      AEC_GET_INT(ini, DELAY, CAPTURE_ALIGNMENT_MIXING_DOWNMIX);
  config->delay.capture_alignment_mixing.adaptive_selection =
      AEC_GET_INT(ini, DELAY, CAPTURE_ALIGNMENT_MIXING_ADAPTIVE_SELECTION);
  config->delay.capture_alignment_mixing.activity_power_threshold =
      AEC_GET_FLOAT(ini, DELAY,
                    CAPTURE_ALIGNMENT_MIXING_ACTIVITY_POWER_THRESHOLD);
  config->delay.capture_alignment_mixing.prefer_first_two_channels =
      AEC_GET_INT(ini, DELAY,
                  CAPTURE_ALIGNMENT_MIXING_PREFER_FIRST_TWO_CHANNELS);

  /* Keys for EchoCanceller3Config:Filter */
  config->filter.refined.length_blocks =
      AEC_GET_INT(ini, FILTER_REFINED, LENGTH_BLOCKS);
  config->filter.refined.leakage_converged =
      AEC_GET_FLOAT(ini, FILTER_REFINED, LEAKAGE_CONVERGED);
  config->filter.refined.leakage_diverged =
      AEC_GET_FLOAT(ini, FILTER_REFINED, LEAKAGE_DIVERGED);
  config->filter.refined.error_floor =
      AEC_GET_FLOAT(ini, FILTER_REFINED, ERROR_FLOOR);
  config->filter.refined.error_ceil =
      AEC_GET_FLOAT(ini, FILTER_REFINED, ERROR_CEIL);
  config->filter.refined.noise_gate =
      AEC_GET_FLOAT(ini, FILTER_REFINED, NOISE_GATE);

  config->filter.coarse.length_blocks =
      AEC_GET_INT(ini, FILTER_COARSE, LENGTH_BLOCKS);
  config->filter.coarse.rate = AEC_GET_FLOAT(ini, FILTER_COARSE, RATE);
  config->filter.coarse.noise_gate =
      AEC_GET_FLOAT(ini, FILTER_COARSE, NOISE_GATE);

  config->filter.refined_initial.length_blocks =
      AEC_GET_INT(ini, FILTER_REFINED_INIT, LENGTH_BLOCKS);
  config->filter.refined_initial.leakage_converged =
      AEC_GET_FLOAT(ini, FILTER_REFINED_INIT, LEAKAGE_CONVERGED);
  config->filter.refined_initial.leakage_diverged =
      AEC_GET_FLOAT(ini, FILTER_REFINED_INIT, LEAKAGE_DIVERGED);
  config->filter.refined_initial.error_floor =
      AEC_GET_FLOAT(ini, FILTER_REFINED_INIT, ERROR_FLOOR);
  config->filter.refined_initial.error_ceil =
      AEC_GET_FLOAT(ini, FILTER_REFINED_INIT, ERROR_CEIL);
  config->filter.refined_initial.noise_gate =
      AEC_GET_FLOAT(ini, FILTER_REFINED_INIT, NOISE_GATE);

  config->filter.coarse_initial.length_blocks =
      AEC_GET_INT(ini, FILTER_COARSE_INIT, LENGTH_BLOCKS);
  config->filter.coarse_initial.rate =
      AEC_GET_FLOAT(ini, FILTER_COARSE_INIT, RATE);
  config->filter.coarse_initial.noise_gate =
      AEC_GET_FLOAT(ini, FILTER_COARSE_INIT, NOISE_GATE);

  config->filter.config_change_duration_blocks =
      AEC_GET_INT(ini, FILTER, CONFIG_CHANGE_DURATION_BLOCKS);
  config->filter.initial_state_seconds =
      AEC_GET_FLOAT(ini, FILTER, INITIAL_STATE_SECONDS);
  config->filter.coarse_reset_hangover_blocks =
      AEC_GET_INT(ini, FILTER, COARSE_RESET_HANGOVER_BLOCKS);
  ;
  config->filter.conservative_initial_phase =
      AEC_GET_INT(ini, FILTER, CONSERVATIVE_INITIAL_PHASE);
  config->filter.enable_coarse_filter_output_usage =
      AEC_GET_INT(ini, FILTER, ENABLE_COARSE_FILTER_OUTPUT_USAGE);
  config->filter.use_linear_filter =
      AEC_GET_INT(ini, FILTER, USE_LINEAR_FILTER);
  config->filter.high_pass_filter_echo_reference =
      AEC_GET_INT(ini, FILTER, HIGH_PASS_FILTER_ECHO_REFERENCE);
  config->filter.export_linear_aec_output =
      AEC_GET_INT(ini, FILTER, EXPORT_LINEAR_AEC_OUTPUT);

  /* Keys for EchoCanceller3Config:Erle */
  config->erle.min = AEC_GET_FLOAT(ini, ERLE, MIN);
  config->erle.max_l = AEC_GET_FLOAT(ini, ERLE, MAX_L);
  config->erle.max_h = AEC_GET_FLOAT(ini, ERLE, MAX_H);
  config->erle.onset_detection = AEC_GET_INT(ini, ERLE, ONSET_DETECTION);
  config->erle.num_sections = AEC_GET_INT(ini, ERLE, NUM_SECTIONS);
  config->erle.clamp_quality_estimate_to_zero =
      AEC_GET_INT(ini, ERLE, CLAMP_QUALITY_ESTIMATE_TO_ZERO);
  config->erle.clamp_quality_estimate_to_one =
      AEC_GET_INT(ini, ERLE, CLAMP_QUALITY_ESTIMATE_TO_ONE);

  /* Keys for EchoCanceller3Config:EpStrength */
  config->ep_strength.default_gain =
      AEC_GET_FLOAT(ini, EP_STRENGTH, DEFAULT_GAIN);
  config->ep_strength.default_len =
      AEC_GET_FLOAT(ini, EP_STRENGTH, DEFAULT_LEN);
  config->ep_strength.bounded_erl = AEC_GET_INT(ini, EP_STRENGTH, BOUNDED_ERL);
  config->ep_strength.echo_can_saturate =
      AEC_GET_INT(ini, EP_STRENGTH, ECHO_CAN_SATURATE);

  /* Keys for EchoCanceller3Config:EchoAudibility */
  config->echo_audibility.low_render_limit =
      AEC_GET_FLOAT(ini, ECHO_AUDIBILITY, LOW_RENDER_LIMIT);
  config->echo_audibility.normal_render_limit =
      AEC_GET_FLOAT(ini, ECHO_AUDIBILITY, NORMAL_RENDER_LIMIT);
  config->echo_audibility.floor_power =
      AEC_GET_FLOAT(ini, ECHO_AUDIBILITY, FLOOR_POWER);
  config->echo_audibility.audibility_threshold_lf =
      AEC_GET_FLOAT(ini, ECHO_AUDIBILITY, AUDIBILITY_THRESHOLD_LF);
  config->echo_audibility.audibility_threshold_mf =
      AEC_GET_FLOAT(ini, ECHO_AUDIBILITY, AUDIBILITY_THRESHOLD_MF);
  config->echo_audibility.audibility_threshold_hf =
      AEC_GET_FLOAT(ini, ECHO_AUDIBILITY, AUDIBILITY_THRESHOLD_HF);
  config->echo_audibility.use_stationarity_properties =
      AEC_GET_INT(ini, ECHO_AUDIBILITY, USE_STATIONARITY_PROPERTIES);
  config->echo_audibility.use_stationarity_properties_at_init =
      AEC_GET_INT(ini, ECHO_AUDIBILITY, USE_STATIONARITY_PROPERTIES_AT_INIT);

  /* Keys for EchoCanceller3Config:RenderLevels */
  config->render_levels.active_render_limit =
      AEC_GET_FLOAT(ini, RENDER_LEVELS, ACTIVE_RENDER_LIMIT);
  config->render_levels.poor_excitation_render_limit =
      AEC_GET_FLOAT(ini, RENDER_LEVELS, POOR_EXCITATION_RENDER_LIMIT);
  config->render_levels.poor_excitation_render_limit_ds8 =
      AEC_GET_FLOAT(ini, RENDER_LEVELS, POOR_EXCITATION_RENDER_LIMIT_DS8);
  config->render_levels.render_power_gain_db =
      AEC_GET_FLOAT(ini, RENDER_LEVELS, RENDER_POWER_GAIN_DB);

  /* Keys for EchoCanceller3Config:EchoRemovalControl */
  config->echo_removal_control.has_clock_drift =
      AEC_GET_INT(ini, ECHO_REMOVAL_CTL, HAS_CLOCK_DRIFT);
  config->echo_removal_control.linear_and_stable_echo_path =
      AEC_GET_INT(ini, ECHO_REMOVAL_CTL, LINEAR_AND_STABLE_ECHO_PATH);

  /* Keys for EchoCanceller3Config:EchoModel */
  config->echo_model.noise_floor_hold =
      AEC_GET_INT(ini, ECHO_MODEL, NOISE_FLOOR_HOLD);
  config->echo_model.min_noise_floor_power =
      AEC_GET_FLOAT(ini, ECHO_MODEL, MIN_NOISE_FLOOR_POWER);
  config->echo_model.stationary_gate_slope =
      AEC_GET_FLOAT(ini, ECHO_MODEL, STATIONARY_GATE_SLOPE);
  config->echo_model.noise_gate_power =
      AEC_GET_FLOAT(ini, ECHO_MODEL, NOISE_GATE_POWER);
  config->echo_model.noise_gate_slope =
      AEC_GET_FLOAT(ini, ECHO_MODEL, NOISE_GATE_SLOPE);
  config->echo_model.render_pre_window_size =
      AEC_GET_INT(ini, ECHO_MODEL, RENDER_PRE_WINDOW_SIZE);
  config->echo_model.render_post_window_size =
      AEC_GET_INT(ini, ECHO_MODEL, RENDER_POST_WINDOW_SIZE);
  config->echo_model.model_reverb_in_nonlinear_mode =
      AEC_GET_INT(ini, ECHO_MODEL, MODEL_REVERB_IN_NONLINEAR_MODE);

  /* Keys for EchoCanceller3Config:ComfortNoise */
  config->comfort_noise.noise_floor_dbfs =
      AEC_GET_FLOAT(ini, COMFORT_NOISE, NOISE_FLOOR_DBFS);

  /* Keys for EchoCanceller3Config:Suppressor */
  config->suppressor.nearend_average_blocks =
      AEC_GET_INT(ini, SUPPRESSOR, NEAREND_AVERAGE_BLOCKS);

  config->suppressor.normal_tuning.mask_lf.enr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MASK_LF_ENR_TRANSPARENT);
  config->suppressor.normal_tuning.mask_lf.enr_suppress =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MASK_LF_ENR_SUPPRESS);
  config->suppressor.normal_tuning.mask_lf.emr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MASK_LF_EMR_TRANSPARENT);
  config->suppressor.normal_tuning.mask_hf.enr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MASK_HF_ENR_TRANSPARENT);
  config->suppressor.normal_tuning.mask_hf.enr_suppress =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MASK_HF_ENR_SUPPRESS);
  config->suppressor.normal_tuning.mask_hf.emr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MASK_HF_EMR_TRANSPARENT);
  config->suppressor.normal_tuning.max_inc_factor =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MAX_INC_FACTOR);
  config->suppressor.normal_tuning.max_dec_factor_lf =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NORMAL_TUNING, MAX_DEC_FACTOR_LF);

  config->suppressor.nearend_tuning.mask_lf.enr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MASK_LF_ENR_TRANSPARENT);
  config->suppressor.nearend_tuning.mask_lf.enr_suppress =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MASK_LF_ENR_SUPPRESS);
  config->suppressor.nearend_tuning.mask_lf.emr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MASK_LF_EMR_TRANSPARENT);
  config->suppressor.nearend_tuning.mask_hf.enr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MASK_HF_ENR_TRANSPARENT);
  config->suppressor.nearend_tuning.mask_hf.enr_suppress =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MASK_HF_ENR_SUPPRESS);
  config->suppressor.nearend_tuning.mask_hf.emr_transparent =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MASK_HF_EMR_TRANSPARENT);
  config->suppressor.nearend_tuning.max_inc_factor =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MAX_INC_FACTOR);
  config->suppressor.nearend_tuning.max_dec_factor_lf =
      AEC_GET_FLOAT(ini, SUPPRESSOR_NEAREND_TUNING, MAX_DEC_FACTOR_LF);

  config->suppressor.dominant_nearend_detection.enr_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_DOMINANT_NEAREND_DETECTION, ENR_THRESHOLD);
  config->suppressor.dominant_nearend_detection.enr_exit_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_DOMINANT_NEAREND_DETECTION,
                    ENR_EXIT_THRESHOLD);
  config->suppressor.dominant_nearend_detection.snr_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_DOMINANT_NEAREND_DETECTION, SNR_THRESHOLD);
  config->suppressor.dominant_nearend_detection.hold_duration =
      AEC_GET_INT(ini, SUPPRESSOR_DOMINANT_NEAREND_DETECTION, HOLD_DURATION);
  config->suppressor.dominant_nearend_detection.trigger_threshold = AEC_GET_INT(
      ini, SUPPRESSOR_DOMINANT_NEAREND_DETECTION, TRIGGER_THRESHOLD);
  config->suppressor.dominant_nearend_detection.use_during_initial_phase =
      AEC_GET_INT(ini, SUPPRESSOR_DOMINANT_NEAREND_DETECTION,
                  USE_DURING_INITIAL_PHASE);

  config->suppressor.subband_nearend_detection.nearend_average_blocks =
      AEC_GET_INT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION,
                  NEAREND_AVERAGE_BLOCKS);
  config->suppressor.subband_nearend_detection.subband1.low =
      AEC_GET_INT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION, SUBBAND1_LOW);
  config->suppressor.subband_nearend_detection.subband1.high =
      AEC_GET_INT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION, SUBBAND1_HIGH);
  config->suppressor.subband_nearend_detection.subband2.low =
      AEC_GET_INT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION, SUBBAND2_LOW);
  config->suppressor.subband_nearend_detection.subband2.high =
      AEC_GET_INT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION, SUBBAND2_HIGH);
  config->suppressor.subband_nearend_detection.nearend_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION,
                    NEAREND_THRESHOLD);
  config->suppressor.subband_nearend_detection.snr_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_SUBBAND_NEAREND_DETECTION, SNR_THRESHOLD);

  config->suppressor.use_subband_nearend_detection =
      AEC_GET_INT(ini, SUPPRESSOR, USE_SUBBAND_NEAREND_DETECTION);

  config->suppressor.high_bands_suppression.enr_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_HIGH_BANDS_SUPPRESSION, ENR_THRESHOLD);
  config->suppressor.high_bands_suppression.max_gain_during_echo =
      AEC_GET_FLOAT(ini, SUPPRESSOR_HIGH_BANDS_SUPPRESSION,
                    MAX_GAIN_DURING_ECHO);
  config->suppressor.high_bands_suppression.anti_howling_activation_threshold =
      AEC_GET_FLOAT(ini, SUPPRESSOR_HIGH_BANDS_SUPPRESSION,
                    ANTI_HOWLING_ACTIVATION_THRESHOLD);
  config->suppressor.high_bands_suppression.anti_howling_gain =
      AEC_GET_FLOAT(ini, SUPPRESSOR_HIGH_BANDS_SUPPRESSION, ANTI_HOWLING_GAIN);

  config->suppressor.floor_first_increase =
      AEC_GET_FLOAT(ini, SUPPRESSOR, FLOOR_FIRST_INCREASE);

  config->suppressor.conservative_hf_suppression =
      AEC_GET_INT(ini, SUPPRESSOR, CONSERVATIVE_HF_SUPPRESSION);
}

void aec_config_dump(dictionary* ini) {
  webrtc::EchoCanceller3Config config;

  aec_config_get(ini, &config);

  syslog(LOG_ERR, "---- aec config dump ----");
  syslog(LOG_ERR, "Buffering:");
  syslog(LOG_ERR, "    excess_render_detection_interval_blocks %zu",
         config.buffering.excess_render_detection_interval_blocks);
  syslog(LOG_ERR, "    max_allowed_excess_render_blocks %zu",
         config.buffering.max_allowed_excess_render_blocks);

  syslog(LOG_ERR, "Delay:");
  syslog(LOG_ERR, "    default_delay %zu sampling_factor %zu num_filters %zu",
         config.delay.default_delay, config.delay.down_sampling_factor,
         config.delay.num_filters);
  syslog(LOG_ERR, "    delay_headroom_samples %zu, hysteresis_limit_blocks %zu",
         config.delay.delay_headroom_samples,
         config.delay.hysteresis_limit_blocks);
  syslog(LOG_ERR, "    fixed_capture_delay_samples %zu",
         config.delay.fixed_capture_delay_samples);
  syslog(LOG_ERR, "    delay_estimate_smoothing %f",
         config.delay.delay_estimate_smoothing);
  syslog(LOG_ERR, "    delay_candidate_detection_threshold %f",
         config.delay.delay_candidate_detection_threshold);
  syslog(LOG_ERR, "    delay_selection_thresholds.initial %d",
         config.delay.delay_selection_thresholds.initial);
  syslog(LOG_ERR, "    delay_selection_thresholds.converged %d",
         config.delay.delay_selection_thresholds.converged);
  syslog(LOG_ERR, "    use_external_delay_estimator %d",
         config.delay.use_external_delay_estimator);
  syslog(LOG_ERR, "    log_warning_on_delay_changes %d",
         config.delay.log_warning_on_delay_changes);
  syslog(LOG_ERR, "    render_alignment_mixing.downmix %d",
         config.delay.render_alignment_mixing.downmix);
  syslog(LOG_ERR, "    render_alignment_mixing.adaptive_selection %d",
         config.delay.render_alignment_mixing.adaptive_selection);
  syslog(LOG_ERR, "    render_alignment_mixing.activity_power_threshold %f",
         config.delay.render_alignment_mixing.activity_power_threshold);
  syslog(LOG_ERR, "    render_alignment_mixing.prefer_first_two_channels %d",
         config.delay.render_alignment_mixing.prefer_first_two_channels);
  syslog(LOG_ERR, "    capture_alignment_mixing.downmix %d",
         config.delay.capture_alignment_mixing.downmix);
  syslog(LOG_ERR, "    capture_alignment_mixing.adaptive_selection %d",
         config.delay.capture_alignment_mixing.adaptive_selection);
  syslog(LOG_ERR, "    capture_alignment_mixing.activity_power_threshold %f",
         config.delay.capture_alignment_mixing.activity_power_threshold);
  syslog(LOG_ERR, "    capture_alignment_mixing.prefer_first_two_channels %d",
         config.delay.capture_alignment_mixing.prefer_first_two_channels);

  syslog(LOG_ERR, "Filter refined configuration:");
  syslog(LOG_ERR,
         "    length_blocks %zu, leakage_converged %f, leakage_diverged %f",
         config.filter.refined.length_blocks,
         config.filter.refined.leakage_converged,
         config.filter.refined.leakage_diverged);
  syslog(LOG_ERR, "    error_floor %f, error_ceil %f, noise_gate %f",
         config.filter.refined.error_floor, config.filter.refined.error_ceil,
         config.filter.refined.noise_gate);
  syslog(LOG_ERR, "Filter coarse configuration:");
  syslog(LOG_ERR, "    length_blocks %zu, rate %f, noise_gate %f",
         config.filter.coarse.length_blocks, config.filter.coarse.rate,
         config.filter.coarse.noise_gate);
  syslog(LOG_ERR, "Filter refined initial configuration:");
  syslog(LOG_ERR,
         "    length_blocks %zu, leakage_converged %f leakage_diverged %f",
         config.filter.refined_initial.length_blocks,
         config.filter.refined_initial.leakage_converged,
         config.filter.refined_initial.leakage_diverged);
  syslog(LOG_ERR, "    error_floor %f, error_ceil %f, noise_gate %f",
         config.filter.refined_initial.error_floor,
         config.filter.refined_initial.error_ceil,
         config.filter.refined_initial.noise_gate);
  syslog(LOG_ERR, "Filter coarse initial configuration:");
  syslog(LOG_ERR, "    length_blocks %zu, rate %f, noise_gate %f",
         config.filter.coarse_initial.length_blocks,
         config.filter.coarse_initial.rate,
         config.filter.coarse_initial.noise_gate);
  syslog(LOG_ERR, "Filter:    config_change_duration_blocks %zd",
         config.filter.config_change_duration_blocks);
  syslog(LOG_ERR, "    initial_state_seconds %f",
         config.filter.initial_state_seconds);

  syslog(LOG_ERR, "    coarse_reset_hangover_blocks %d",
         config.filter.coarse_reset_hangover_blocks);
  syslog(LOG_ERR, "    conservative_initial_phase %d",
         config.filter.conservative_initial_phase);
  syslog(LOG_ERR, "    enable_coarse_filter_output_usage %d",
         config.filter.enable_coarse_filter_output_usage);
  syslog(LOG_ERR, "    use_linear_filter %d", config.filter.use_linear_filter);
  syslog(LOG_ERR, "    high_pass_filter_echo_reference %d",
         config.filter.high_pass_filter_echo_reference);
  syslog(LOG_ERR, "    export_linear_aec_output %d",
         config.filter.export_linear_aec_output);

  syslog(LOG_ERR, "Erle: min %f max_l %f max_h %f onset_detection %d",
         config.erle.min, config.erle.max_l, config.erle.max_h,
         config.erle.onset_detection);
  syslog(LOG_ERR, "      num_sections %zu clamp_quality_estimate_to_zero %d",
         config.erle.num_sections, config.erle.clamp_quality_estimate_to_zero);
  syslog(LOG_ERR, "      clamp_quality_estimate_to_one %d",
         config.erle.clamp_quality_estimate_to_one);

  syslog(LOG_ERR, "Ep strength: default_gain %f default_len %f",
         config.ep_strength.default_gain, config.ep_strength.default_len);
  syslog(LOG_ERR, "    echo_can_saturate %d, bounded_erl %d,",
         config.ep_strength.echo_can_saturate, config.ep_strength.bounded_erl);
  syslog(LOG_ERR, "Echo audibility:");
  syslog(LOG_ERR, "    low_render_limit %f, normal_render_limit %f",
         config.echo_audibility.low_render_limit,
         config.echo_audibility.normal_render_limit);
  syslog(LOG_ERR, "    floor_power %f, audibility_threshold_lf %f",
         config.echo_audibility.floor_power,
         config.echo_audibility.audibility_threshold_lf);
  syslog(LOG_ERR, "    audibility_threshold_mf %f",
         config.echo_audibility.audibility_threshold_mf);
  syslog(LOG_ERR, "    audibility_threshold_hf %f",
         config.echo_audibility.audibility_threshold_hf);
  syslog(LOG_ERR, "    use_stationarity_properties %d",
         config.echo_audibility.use_stationarity_properties);
  syslog(LOG_ERR, "    use_stationarity_properties_at_init %d",
         config.echo_audibility.use_stationarity_properties_at_init);

  syslog(LOG_ERR, "Render levels:");
  syslog(LOG_ERR, "    active_render_limit %f",
         config.render_levels.active_render_limit);
  syslog(LOG_ERR, "    poor_excitation_render_limit %f",
         config.render_levels.poor_excitation_render_limit);
  syslog(LOG_ERR, "    poor_excitation_render_limit_ds8 %f",
         config.render_levels.poor_excitation_render_limit_ds8);
  syslog(LOG_ERR, "    render_power_gain_db %f",
         config.render_levels.render_power_gain_db);
  syslog(LOG_ERR, "Echo removal control:");
  syslog(LOG_ERR, "    gain rampup:");
  syslog(LOG_ERR, "    has_clock_drift %d",
         config.echo_removal_control.has_clock_drift);
  syslog(LOG_ERR, "    linear_and_stable_echo_path %d",
         config.echo_removal_control.linear_and_stable_echo_path);
  syslog(LOG_ERR, "Echo model:");
  syslog(LOG_ERR, "    noise_floor_hold %zu, min_noise_floor_power %f",
         config.echo_model.noise_floor_hold,
         config.echo_model.min_noise_floor_power);
  syslog(LOG_ERR, "    stationary_gate_slope %f, noise_gate_power %f",
         config.echo_model.stationary_gate_slope,
         config.echo_model.noise_gate_power);
  syslog(LOG_ERR, "    noise_gate_slope %f, render_pre_window_size %zu",
         config.echo_model.noise_gate_slope,
         config.echo_model.render_pre_window_size);
  syslog(LOG_ERR, "Suppressor:");
  syslog(LOG_ERR, "    nearend_average_blocks %zu",
         config.suppressor.nearend_average_blocks);
  syslog(LOG_ERR, "    Normal tuning, mask_lf %f %f %f",
         config.suppressor.normal_tuning.mask_lf.enr_transparent,
         config.suppressor.normal_tuning.mask_lf.enr_suppress,
         config.suppressor.normal_tuning.mask_lf.emr_transparent);
  syslog(LOG_ERR, "                   mask_hf %f %f %f",
         config.suppressor.normal_tuning.mask_hf.enr_transparent,
         config.suppressor.normal_tuning.mask_hf.enr_suppress,
         config.suppressor.normal_tuning.mask_hf.emr_transparent);
  syslog(LOG_ERR, "                   max_inc_factor %f max_dec_factor_lf %f",
         config.suppressor.normal_tuning.max_inc_factor,
         config.suppressor.normal_tuning.max_dec_factor_lf);
  syslog(LOG_ERR, "    Nearend tuning, mask_lf %f %f %f",
         config.suppressor.nearend_tuning.mask_lf.enr_transparent,
         config.suppressor.nearend_tuning.mask_lf.enr_suppress,
         config.suppressor.nearend_tuning.mask_lf.emr_transparent);
  syslog(LOG_ERR, "                   mask_hf %f %f %f",
         config.suppressor.nearend_tuning.mask_hf.enr_transparent,
         config.suppressor.nearend_tuning.mask_hf.enr_suppress,
         config.suppressor.nearend_tuning.mask_hf.emr_transparent);
  syslog(LOG_ERR, "                   max_inc_factor %f max_dec_factor_lf %f",
         config.suppressor.nearend_tuning.max_inc_factor,
         config.suppressor.nearend_tuning.max_dec_factor_lf);
  syslog(LOG_ERR, "    Dominant nearend detection:");
  syslog(LOG_ERR, "        enr_threshold %f",
         config.suppressor.dominant_nearend_detection.enr_threshold);
  syslog(LOG_ERR, "        enr_exit_threshold %f",
         config.suppressor.dominant_nearend_detection.enr_exit_threshold);
  syslog(LOG_ERR, "        snr_threshold %f",
         config.suppressor.dominant_nearend_detection.snr_threshold);
  syslog(LOG_ERR, "        hold_duration %d",
         config.suppressor.dominant_nearend_detection.hold_duration);
  syslog(LOG_ERR, "        trigger_threshold %d",
         config.suppressor.dominant_nearend_detection.trigger_threshold);
  syslog(LOG_ERR, "        use_during_initial_phase %d",
         config.suppressor.dominant_nearend_detection.use_during_initial_phase);
  syslog(LOG_ERR, "    Subband nearend detection:");
  syslog(LOG_ERR, "        nearend_average_blocks %zu",
         config.suppressor.subband_nearend_detection.nearend_average_blocks);
  syslog(LOG_ERR, "        subband1.low %zu subband1.high %zu",
         config.suppressor.subband_nearend_detection.subband1.low,
         config.suppressor.subband_nearend_detection.subband1.high);
  syslog(LOG_ERR, "        subband2.low %zu subband2.high %zu",
         config.suppressor.subband_nearend_detection.subband2.low,
         config.suppressor.subband_nearend_detection.subband2.high);
  syslog(LOG_ERR, "        nearend_threshold %f snr_threshold %f",
         config.suppressor.subband_nearend_detection.nearend_threshold,
         config.suppressor.subband_nearend_detection.snr_threshold);
  syslog(LOG_ERR, "    use_subband_nearend_detection %d",
         config.suppressor.use_subband_nearend_detection);

  syslog(LOG_ERR, "    High bands suppression:");
  syslog(LOG_ERR, "        enr_threshold %f max_gain_during_echo %f",
         config.suppressor.high_bands_suppression.enr_threshold,
         config.suppressor.high_bands_suppression.max_gain_during_echo);
  syslog(LOG_ERR, "        anti_howling_activation_threshold %f",
         config.suppressor.high_bands_suppression
             .anti_howling_activation_threshold);
  syslog(LOG_ERR, "        anti_howling_gain %f",
         config.suppressor.high_bands_suppression.anti_howling_gain);
  syslog(LOG_ERR, "    floor_first_increase %f",
         config.suppressor.floor_first_increase);
  syslog(LOG_ERR, "    conservative_hf_suppression %d",
         config.suppressor.conservative_hf_suppression);
}
