/*
 * Copyright 2023 The ChromiumOS Authors
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef METRICS_METRICS_LIBRARY_H_
#define METRICS_METRICS_LIBRARY_H_

#include <string>

class MetricsLibraryInterface {
 public:
  virtual bool SendToUMA(const std::string& name, int sample, int min, int max,
                         int nbuckets) {
    return true;
  }
};

class MetricsLibrary : public MetricsLibraryInterface {};

#endif
