#ifndef COMPAT_ABSL_FLAGS_FLAG_H_
#define COMPAT_ABSL_FLAGS_FLAG_H_

#define ABSL_FLAG(type, name, default_value, help)

namespace absl {
template <typename T>
T GetFlag(const T& value) {
    return value;
}
}

#endif
