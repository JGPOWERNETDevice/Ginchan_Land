/*
 * Copyright 2023 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef AEC_CONFIG_H_
#define AEC_CONFIG_H_

extern "C" {
#include <iniparser.h>
}

#include "api/audio/echo_canceller3_config.h"

/* Keys for EchoCanceller3Config:Buffering */
#define AEC_BUFFERING_EXCESS_RENDER_DETECTION_INTERVAL_BLOCKS \
  "buffering:excess_render_detection_interval_blocks"
#define AEC_BUFFERING_EXCESS_RENDER_DETECTION_INTERVAL_BLOCKS_VALUE 250
#define AEC_BUFFERING_MAX_ALLOWED_EXCESS_RENDER_BLOCKS \
  "buffering:max_allowed_excess_render_blocks"
#define AEC_BUFFERING_MAX_ALLOWED_EXCESS_RENDER_BLOCKS_VALUE 8

/* Keys for EchoCanceller3Config:Delay */
#define AEC_DELAY_DEFAULT_DELAY "delay:default_delay"
#define AEC_DELAY_DEFAULT_DELAY_VALUE 5
#define AEC_DELAY_DOWN_SAMPLING_FACTOR "delay:down_sampling_factor"
#define AEC_DELAY_DOWN_SAMPLING_FACTOR_VALUE 4
#define AEC_DELAY_NUM_FILTERS "delay:num_filters"
#define AEC_DELAY_NUM_FILTERS_VALUE 5
#define AEC_DELAY_DELAY_HEADROOM_SAMPLES "delay:delay_headroom_samples"
#define AEC_DELAY_DELAY_HEADROOM_SAMPLES_VALUE 32
#define AEC_DELAY_HYSTERESIS_LIMIT_BLOCKS "delay:hysteresis_limit_blocks"
#define AEC_DELAY_HYSTERESIS_LIMIT_BLOCKS_VALUE 1
#define AEC_DELAY_FIXED_CAPTURE_DELAY_SAMPLES \
  "delay:fixed_capture_delay_samples"
#define AEC_DELAY_FIXED_CAPTURE_DELAY_SAMPLES_VALUE 0
#define AEC_DELAY_DELAY_ESTIMATE_SMOOTHING "delay:delay_estimate_smoothing"
#define AEC_DELAY_DELAY_ESTIMATE_SMOOTHING_VALUE 0.7f

#define AEC_DELAY_DELAY_CANDIDATE_DETECTION_THRESHOLD \
  "delay:delay_candidate_detection_threshold"
#define AEC_DELAY_DELAY_CANDIDATE_DETECTION_THRESHOLD_VALUE 0.2f
#define AEC_DELAY_DELAY_SELECTION_THRESHOLD_INITIAL \
  "delay:delay_selection_thresholds_initial"
#define AEC_DELAY_DELAY_SELECTION_THRESHOLD_INITIAL_VALUE 5
#define AEC_DELAY_DELAY_SELECTION_THRESHOLD_CONVERGED \
  "delay:delay_selection_thresholds_converged"
#define AEC_DELAY_DELAY_SELECTION_THRESHOLD_CONVERGED_VALUE 20

#define AEC_DELAY_USE_EXTERNAL_DELAY_ESTIMATOR \
  "delay:use_external_delay_estimator"
#define AEC_DELAY_USE_EXTERNAL_DELAY_ESTIMATOR_VALUE 0
#define AEC_DELAY_LOG_WARNING_ON_DELAY_CHANGES \
  "delay:log_warning_on_delay_changes"
#define AEC_DELAY_LOG_WARNING_ON_DELAY_CHANGES_VALUE 0

#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_DOWNMIX \
  "delay:render_alignment_mixing_downmix"
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_DOWNMIX_VALUE 0
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_ADAPTIVE_SELECTION \
  "delay:render_alignment_mixing_adaptive_selection"
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_ADAPTIVE_SELECTION_VALUE 1
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_ACTIVITY_POWER_THRESHOLD \
  "delay:render_alignment_mixing_activity_power_threshold"
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_ACTIVITY_POWER_THRESHOLD_VALUE 10000.f
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_PREFER_FIRST_TWO_CHANNELS \
  "delay:render_alignment_mixing_prefer_first_two_channels"
#define AEC_DELAY_RENDER_ALIGNMENT_MIXING_PREFER_FIRST_TWO_CHANNELS_VALUE 1
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_DOWNMIX \
  "delay:capture_alignment_mixing_downmix"
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_DOWNMIX_VALUE 0
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_ADAPTIVE_SELECTION \
  "delay:capture_alignment_mixing_adaptive_selection"
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_ADAPTIVE_SELECTION_VALUE 1
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_ACTIVITY_POWER_THRESHOLD \
  "delay:capture_alignment_mixing_activity_power_threshold"
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_ACTIVITY_POWER_THRESHOLD_VALUE \
  10000.f
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_PREFER_FIRST_TWO_CHANNELS \
  "delay:capture_alignment_mixing_prefer_first_two_channels"
#define AEC_DELAY_CAPTURE_ALIGNMENT_MIXING_PREFER_FIRST_TWO_CHANNELS_VALUE 0

/* Keys for EchoCanceller3Config:Filter */
// Filter refined configuration
#define AEC_FILTER_REFINED_LENGTH_BLOCKS "filter.refined:length_blocks"
#define AEC_FILTER_REFINED_LENGTH_BLOCKS_VALUE 13
#define AEC_FILTER_REFINED_LEAKAGE_CONVERGED "filter.refined:leakage_converged"
#define AEC_FILTER_REFINED_LEAKAGE_CONVERGED_VALUE 0.00005f
#define AEC_FILTER_REFINED_LEAKAGE_DIVERGED "filter.refined:leakage_diverged"
#define AEC_FILTER_REFINED_LEAKAGE_DIVERGED_VALUE 0.05f
#define AEC_FILTER_REFINED_ERROR_FLOOR "filter.refined:error_floor"
#define AEC_FILTER_REFINED_ERROR_FLOOR_VALUE 0.001f
#define AEC_FILTER_REFINED_ERROR_CEIL "filter.refined:error_ceil"
#define AEC_FILTER_REFINED_ERROR_CEIL_VALUE 2.f
#define AEC_FILTER_REFINED_NOISE_GATE "filter.refined:noise_gate"
#define AEC_FILTER_REFINED_NOISE_GATE_VALUE 20075344.f

// Filter coarse configuration
#define AEC_FILTER_COARSE_LENGTH_BLOCKS "filter.coarse:length_blocks"
#define AEC_FILTER_COARSE_LENGTH_BLOCKS_VALUE 13
#define AEC_FILTER_COARSE_RATE "filter.coarse:rate"
#define AEC_FILTER_COARSE_RATE_VALUE 0.7f
#define AEC_FILTER_COARSE_NOISE_GATE "filter.coarse:noise_gate"
#define AEC_FILTER_COARSE_NOISE_GATE_VALUE 20075344.f

// Filter refined initial configuration
#define AEC_FILTER_REFINED_INIT_LENGTH_BLOCKS \
  "filter.refined_initial:length_blocks"
#define AEC_FILTER_REFINED_INIT_LENGTH_BLOCKS_VALUE 12
#define AEC_FILTER_REFINED_INIT_LEAKAGE_CONVERGED \
  "filter.refined_initial:leakage_converged"
#define AEC_FILTER_REFINED_INIT_LEAKAGE_CONVERGED_VALUE 0.005f
#define AEC_FILTER_REFINED_INIT_LEAKAGE_DIVERGED \
  "filter.refined_initial:leakage_diverged"
#define AEC_FILTER_REFINED_INIT_LEAKAGE_DIVERGED_VALUE 0.5f
#define AEC_FILTER_REFINED_INIT_ERROR_FLOOR "filter.refined_initial:error_floor"
#define AEC_FILTER_REFINED_INIT_ERROR_FLOOR_VALUE 0.001f
#define AEC_FILTER_REFINED_INIT_ERROR_CEIL "filter.refined_initial:error_ceil"
#define AEC_FILTER_REFINED_INIT_ERROR_CEIL_VALUE 2.f
#define AEC_FILTER_REFINED_INIT_NOISE_GATE "filter.refined_initial:noise_gate"
#define AEC_FILTER_REFINED_INIT_NOISE_GATE_VALUE 20075344.f

// Filter coarse initial configuration
#define AEC_FILTER_COARSE_INIT_LENGTH_BLOCKS \
  "filter.coarse_initial:length_blocks"
#define AEC_FILTER_COARSE_INIT_LENGTH_BLOCKS_VALUE 12
#define AEC_FILTER_COARSE_INIT_RATE "filter.coarse_initial:rate"
#define AEC_FILTER_COARSE_INIT_RATE_VALUE 0.9f
#define AEC_FILTER_COARSE_INIT_NOISE_GATE "filter.coarse_initial:noise_gate"
#define AEC_FILTER_COARSE_INIT_NOISE_GATE_VALUE 20075344.f

#define AEC_FILTER_CONFIG_CHANGE_DURATION_BLOCKS \
  "filter:config_change_duration_blocks"
#define AEC_FILTER_CONFIG_CHANGE_DURATION_BLOCKS_VALUE 250
#define AEC_FILTER_INITIAL_STATE_SECONDS "filter:initial_state_seconds"
#define AEC_FILTER_INITIAL_STATE_SECONDS_VALUE 2.5f
#define AEC_FILTER_COARSE_RESET_HANGOVER_BLOCKS \
  "filter:coarse_reset_hangover_blocks"
#define AEC_FILTER_COARSE_RESET_HANGOVER_BLOCKS_VALUE 25
#define AEC_FILTER_CONSERVATIVE_INITIAL_PHASE \
  "filter:conservative_initial_phase"
#define AEC_FILTER_CONSERVATIVE_INITIAL_PHASE_VALUE 0
#define AEC_FILTER_ENABLE_COARSE_FILTER_OUTPUT_USAGE \
  "filter:enable_coarse_filter_output_usage"
#define AEC_FILTER_ENABLE_COARSE_FILTER_OUTPUT_USAGE_VALUE 1
#define AEC_FILTER_USE_LINEAR_FILTER "filter:use_linear_filter"
#define AEC_FILTER_USE_LINEAR_FILTER_VALUE 1
#define AEC_FILTER_HIGH_PASS_FILTER_ECHO_REFERENCE \
  "filter:high_pass_filter_echo_reference"
#define AEC_FILTER_HIGH_PASS_FILTER_ECHO_REFERENCE_VALUE 0
#define AEC_FILTER_EXPORT_LINEAR_AEC_OUTPUT "filter:export_linear_aec_output"
#define AEC_FILTER_EXPORT_LINEAR_AEC_OUTPUT_VALUE 0

// Erle
#define AEC_ERLE_MIN "erle:min"
#define AEC_ERLE_MIN_VALUE 1.f
#define AEC_ERLE_MAX_L "erle:max_l"
#define AEC_ERLE_MAX_L_VALUE 4.f
#define AEC_ERLE_MAX_H "erle:max_h"
#define AEC_ERLE_MAX_H_VALUE 1.5f
#define AEC_ERLE_ONSET_DETECTION "erle:onset_detection"
#define AEC_ERLE_ONSET_DETECTION_VALUE 1
#define AEC_ERLE_NUM_SECTIONS "erle:num_sections"
#define AEC_ERLE_NUM_SECTIONS_VALUE 1
#define AEC_ERLE_CLAMP_QUALITY_ESTIMATE_TO_ZERO \
  "erle:clamp_quality_estimate_to_zero"
#define AEC_ERLE_CLAMP_QUALITY_ESTIMATE_TO_ZERO_VALUE 1
#define AEC_ERLE_CLAMP_QUALITY_ESTIMATE_TO_ONE \
  "erle:clamp_quality_estimate_to_one"
#define AEC_ERLE_CLAMP_QUALITY_ESTIMATE_TO_ONE_VALUE 1

// EpStrength
#define AEC_EP_STRENGTH_DEFAULT_GAIN "ep_strength:default_gain"
#define AEC_EP_STRENGTH_DEFAULT_GAIN_VALUE 1.f
#define AEC_EP_STRENGTH_DEFAULT_LEN "ep_strength:default_len"
#define AEC_EP_STRENGTH_DEFAULT_LEN_VALUE 0.83f
#define AEC_EP_STRENGTH_ECHO_CAN_SATURATE "ep_strength:echo_can_saturate"
#define AEC_EP_STRENGTH_ECHO_CAN_SATURATE_VALUE 1
#define AEC_EP_STRENGTH_BOUNDED_ERL "ep_strength:bounded_erl"
#define AEC_EP_STRENGTH_BOUNDED_ERL_VALUE 0

// EchoAudibility
#define AEC_ECHO_AUDIBILITY_LOW_RENDER_LIMIT "echo_audibility:low_render_limit"
#define AEC_ECHO_AUDIBILITY_LOW_RENDER_LIMIT_VALUE 4 * 64.f
#define AEC_ECHO_AUDIBILITY_NORMAL_RENDER_LIMIT \
  "echo_audibility:normal_render_limit"
#define AEC_ECHO_AUDIBILITY_NORMAL_RENDER_LIMIT_VALUE 64.f
#define AEC_ECHO_AUDIBILITY_FLOOR_POWER "echo_audibility:floor_power"
#define AEC_ECHO_AUDIBILITY_FLOOR_POWER_VALUE 2 * 64.f
#define AEC_ECHO_AUDIBILITY_AUDIBILITY_THRESHOLD_LF \
  "echo_audibility:audibility_threshold_lf"
#define AEC_ECHO_AUDIBILITY_AUDIBILITY_THRESHOLD_LF_VALUE 10
#define AEC_ECHO_AUDIBILITY_AUDIBILITY_THRESHOLD_MF \
  "echo_audibility:audibility_threshold_mf"
#define AEC_ECHO_AUDIBILITY_AUDIBILITY_THRESHOLD_MF_VALUE 10
#define AEC_ECHO_AUDIBILITY_AUDIBILITY_THRESHOLD_HF \
  "echo_audibility:audibility_threshold_hf"
#define AEC_ECHO_AUDIBILITY_AUDIBILITY_THRESHOLD_HF_VALUE 10
#define AEC_ECHO_AUDIBILITY_USE_STATIONARITY_PROPERTIES \
  "echo_audibility:use_stationarity_properties"
#define AEC_ECHO_AUDIBILITY_USE_STATIONARITY_PROPERTIES_VALUE 0
#define AEC_ECHO_AUDIBILITY_USE_STATIONARITY_PROPERTIES_AT_INIT \
  "echo_audibility:use_stationarity_properties_at_init"
#define AEC_ECHO_AUDIBILITY_USE_STATIONARITY_PROPERTIES_AT_INIT_VALUE 0

// Rendering levels
#define AEC_RENDER_LEVELS_ACTIVE_RENDER_LIMIT \
  "render_levels:active_render_limit"
#define AEC_RENDER_LEVELS_ACTIVE_RENDER_LIMIT_VALUE 100.f
#define AEC_RENDER_LEVELS_POOR_EXCITATION_RENDER_LIMIT \
  "render_levels:poor_excitation_render_limit"
#define AEC_RENDER_LEVELS_POOR_EXCITATION_RENDER_LIMIT_VALUE 150.f
#define AEC_RENDER_LEVELS_POOR_EXCITATION_RENDER_LIMIT_DS8 \
  "render_levels:poor_excitation_render_limit_ds8"
#define AEC_RENDER_LEVELS_POOR_EXCITATION_RENDER_LIMIT_DS8_VALUE 20.f
#define AEC_RENDER_LEVELS_RENDER_POWER_GAIN_DB \
  "render_levels:render_power_gain_db"
#define AEC_RENDER_LEVELS_RENDER_POWER_GAIN_DB_VALUE 0.f

// Echo removal controls
#define AEC_ECHO_REMOVAL_CTL_HAS_CLOCK_DRIFT \
  "echo_removal_control:has_clock_drift"
#define AEC_ECHO_REMOVAL_CTL_HAS_CLOCK_DRIFT_VALUE 0
#define AEC_ECHO_REMOVAL_CTL_LINEAR_AND_STABLE_ECHO_PATH \
  "echo_removal_control:linear_and_stable_echo_path"
#define AEC_ECHO_REMOVAL_CTL_LINEAR_AND_STABLE_ECHO_PATH_VALUE 0

// EchoModel
#define AEC_ECHO_MODEL_NOISE_FLOOR_HOLD "echo_model:noise_floor_hold"
#define AEC_ECHO_MODEL_NOISE_FLOOR_HOLD_VALUE 50
#define AEC_ECHO_MODEL_MIN_NOISE_FLOOR_POWER "echo_model:min_noise_floor_power"
#define AEC_ECHO_MODEL_MIN_NOISE_FLOOR_POWER_VALUE 1638400.f
#define AEC_ECHO_MODEL_STATIONARY_GATE_SLOPE "echo_model:stationary_gate_slope"
#define AEC_ECHO_MODEL_STATIONARY_GATE_SLOPE_VALUE 10.f
#define AEC_ECHO_MODEL_NOISE_GATE_POWER "echo_model:noise_gate_power"
#define AEC_ECHO_MODEL_NOISE_GATE_POWER_VALUE 27509.42f
#define AEC_ECHO_MODEL_NOISE_GATE_SLOPE "echo_model:noise_gate_slope"
#define AEC_ECHO_MODEL_NOISE_GATE_SLOPE_VALUE 0.3f
#define AEC_ECHO_MODEL_RENDER_PRE_WINDOW_SIZE \
  "echo_model:render_pre_window_size"
#define AEC_ECHO_MODEL_RENDER_PRE_WINDOW_SIZE_VALUE 1
#define AEC_ECHO_MODEL_RENDER_POST_WINDOW_SIZE \
  "echo_model:render_post_window_size"
#define AEC_ECHO_MODEL_RENDER_POST_WINDOW_SIZE_VALUE 1
#define AEC_ECHO_MODEL_MODEL_REVERB_IN_NONLINEAR_MODE \
  "echo_model:model_reverb_in_nonlinear_mode"
#define AEC_ECHO_MODEL_MODEL_REVERB_IN_NONLINEAR_MODE_VALUE 1

// ComfortNoise
#define AEC_COMFORT_NOISE_NOISE_FLOOR_DBFS "comfort_noise:noise_floor_dbfs"
#define AEC_COMFORT_NOISE_NOISE_FLOOR_DBFS_VALUE -96.03406f

// Suppressor
#define AEC_SUPPRESSOR_NEAREND_AVERAGE_BLOCKS \
  "suppressor:nearend_average_blocks"
#define AEC_SUPPRESSOR_NEAREND_AVERAGE_BLOCKS_VALUE 4

#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_LF_ENR_TRANSPARENT \
  "suppressor.normal_tuning:mask_lf_enr_transparent"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_LF_ENR_TRANSPARENT_VALUE .3f
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_LF_ENR_SUPPRESS \
  "suppressor.normal_tuning:mask_lf_enr_suppress"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_LF_ENR_SUPPRESS_VALUE .4f
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_LF_EMR_TRANSPARENT \
  "suppressor.normal_tuning:mask_lf_emr_transparent"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_LF_EMR_TRANSPARENT_VALUE .3f

#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_HF_ENR_TRANSPARENT \
  "suppressor.normal_tuning:mask_hf_enr_transparent"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_HF_ENR_TRANSPARENT_VALUE .07f
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_HF_ENR_SUPPRESS \
  "suppressor.normal_tuning:mask_hf_enr_suppress"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_HF_ENR_SUPPRESS_VALUE .1f
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_HF_EMR_TRANSPARENT \
  "suppressor.normal_tuning:mask_hf_emr_transparent"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MASK_HF_EMR_TRANSPARENT_VALUE .3f

#define AEC_SUPPRESSOR_NORMAL_TUNING_MAX_INC_FACTOR \
  "suppressor.normal_tuning:max_inc_factor"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MAX_INC_FACTOR_VALUE 2.0f
#define AEC_SUPPRESSOR_NORMAL_TUNING_MAX_DEC_FACTOR_LF \
  "suppressor.normal_tuning:max_dec_factor_lf"
#define AEC_SUPPRESSOR_NORMAL_TUNING_MAX_DEC_FACTOR_LF_VALUE 0.25f

#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_LF_ENR_TRANSPARENT \
  "suppressor.nearend_tuning:mask_lf_enr_transparent"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_LF_ENR_TRANSPARENT_VALUE 1.09f
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_LF_ENR_SUPPRESS \
  "suppressor.nearend_tuning:mask_lf_enr_suppress"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_LF_ENR_SUPPRESS_VALUE 1.1f
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_LF_EMR_TRANSPARENT \
  "suppressor.nearend_tuning:mask_lf_emr_transparent"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_LF_EMR_TRANSPARENT_VALUE .3f

#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_HF_ENR_TRANSPARENT \
  "suppressor.nearend_tuning:mask_hf_enr_transparent"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_HF_ENR_TRANSPARENT_VALUE .1f
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_HF_ENR_SUPPRESS \
  "suppressor.nearend_tuning:mask_hf_enr_suppress"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_HF_ENR_SUPPRESS_VALUE .3f
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_HF_EMR_TRANSPARENT \
  "suppressor.nearend_tuning:mask_hf_emr_transparent"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MASK_HF_EMR_TRANSPARENT_VALUE .3f

#define AEC_SUPPRESSOR_NEAREND_TUNING_MAX_INC_FACTOR \
  "suppressor.nearend_tuning:max_inc_factor"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MAX_INC_FACTOR_VALUE 2.0f
#define AEC_SUPPRESSOR_NEAREND_TUNING_MAX_DEC_FACTOR_LF \
  "suppressor.nearend_tuning:max_dec_factor_lf"
#define AEC_SUPPRESSOR_NEAREND_TUNING_MAX_DEC_FACTOR_LF_VALUE 0.25f

#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_ENR_THRESHOLD \
  "suppressor.dominant_nearend_detection:enr_threshold"
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_ENR_THRESHOLD_VALUE .25f
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_ENR_EXIT_THRESHOLD \
  "suppressor.dominant_nearend_detection:enr_exit_threshold"
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_ENR_EXIT_THRESHOLD_VALUE 10.f
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_SNR_THRESHOLD \
  "suppressor.dominant_nearend_detection:snr_threshold"
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_SNR_THRESHOLD_VALUE 30.f
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_HOLD_DURATION \
  "suppressor.dominant_nearend_detection:hold_duration"
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_HOLD_DURATION_VALUE 50
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_TRIGGER_THRESHOLD \
  "suppressor.dominant_nearend_detection:trigger_threshold"
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_TRIGGER_THRESHOLD_VALUE 12
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_USE_DURING_INITIAL_PHASE \
  "suppressor.dominant_nearend_detection:use_during_initial_phase"
#define AEC_SUPPRESSOR_DOMINANT_NEAREND_DETECTION_USE_DURING_INITIAL_PHASE_VALUE \
  1

#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_NEAREND_AVERAGE_BLOCKS \
  "suppressor.subband_nearend_detection:nearend_average_blocks"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_NEAREND_AVERAGE_BLOCKS_VALUE 1
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND1_LOW \
  "suppressor.subband_nearend_detection:subband1.low"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND1_LOW_VALUE 1
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND1_HIGH \
  "suppressor.subband_nearend_detection:subband1.high"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND1_HIGH_VALUE 1
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND2_LOW \
  "suppressor.subband_nearend_detection:subband2.low"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND2_LOW_VALUE 1
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND2_HIGH \
  "suppressor.subband_nearend_detection:subband2.high"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SUBBAND2_HIGH_VALUE 1
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_NEAREND_THRESHOLD \
  "suppressor.subband_nearend_detection:nearend_threshold"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_NEAREND_THRESHOLD_VALUE 1.f
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SNR_THRESHOLD \
  "suppressor.subband_nearend_detection:snr_threshold"
#define AEC_SUPPRESSOR_SUBBAND_NEAREND_DETECTION_SNR_THRESHOLD_VALUE 1.f

#define AEC_SUPPRESSOR_USE_SUBBAND_NEAREND_DETECTION \
  "suppressor:use_subband_nearend_detection"
#define AEC_SUPPRESSOR_USE_SUBBAND_NEAREND_DETECTION_VALUE 0

#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_ENR_THRESHOLD \
  "suppressor.high_bands_suppression:enr_threshold"
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_ENR_THRESHOLD_VALUE 1.f
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_MAX_GAIN_DURING_ECHO \
  "suppressor.high_bands_suppression:max_gain_during_echo"
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_MAX_GAIN_DURING_ECHO_VALUE 1.f
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_ANTI_HOWLING_ACTIVATION_THRESHOLD \
  "suppressor.high_bands_suppression:anti_howling_activation_threshold"
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_ANTI_HOWLING_ACTIVATION_THRESHOLD_VALUE \
  400.f
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_ANTI_HOWLING_GAIN \
  "suppressor.high_bands_suppression:anti_howling_gain"
#define AEC_SUPPRESSOR_HIGH_BANDS_SUPPRESSION_ANTI_HOWLING_GAIN_VALUE 1.f

#define AEC_SUPPRESSOR_FLOOR_FIRST_INCREASE "suppressor:floor_first_increase"
#define AEC_SUPPRESSOR_FLOOR_FIRST_INCREASE_VALUE 0.00001f

#define AEC_SUPPRESSOR_CONSERVATIVE_HF_SUPPRESSION \
  "suppressor:CONSERVATIVE_HF_SUPPRESSION"
#define AEC_SUPPRESSOR_CONSERVATIVE_HF_SUPPRESSION_VALUE 0

/* Gets the aec config from given config directory. */
void aec_config_get(dictionary* ini, webrtc::EchoCanceller3Config* config);

/* Prints the config content to syslog. */
void aec_config_dump(dictionary* config);

#endif /* AEC_CONFIG_H_ */
