#ifndef COMPAT_METRICS_LIBRARY_H_
#define COMPAT_METRICS_LIBRARY_H_

#include <string>

class MetricsLibraryInterface {
public:
    virtual ~MetricsLibraryInterface() = default;

    virtual bool SendToUMA(
            const std::string& name,
            int sample,
            int min,
            int max,
            int nbuckets
    ) {
        (void)name;
        (void)sample;
        (void)min;
        (void)max;
        (void)nbuckets;
        return true;
    }
};

class MetricsLibrary : public MetricsLibraryInterface {
public:
    MetricsLibrary() = default;
    ~MetricsLibrary() override = default;
};

#endif
