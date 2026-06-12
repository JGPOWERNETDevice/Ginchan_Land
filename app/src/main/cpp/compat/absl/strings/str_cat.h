#ifndef COMPAT_ABSL_STRINGS_STR_CAT_H_
#define COMPAT_ABSL_STRINGS_STR_CAT_H_

#include <sstream>
#include <string>
#include <type_traits>

#include "absl/strings/has_absl_stringify.h"
#include "absl/strings/string_view.h"

namespace absl {

class StrCatSink {
 public:
  explicit StrCatSink(std::string* out) : out_(out) {}
  void Append(absl::string_view value) { out_->append(value.data(), value.size()); }
  void Append(const std::string& value) { out_->append(value); }
  void Append(const char* value) { if (value) out_->append(value); }

 private:
  std::string* out_;
};

inline void StrCatAppend(std::string*) {}

template <typename T>
inline void StrCatOne(std::string* out, const T& value) {
  if constexpr (absl::HasAbslStringify<T>::value) {
    StrCatSink sink(out);
    AbslStringify(sink, value);
  } else {
    std::ostringstream oss;
    oss << value;
    out->append(oss.str());
  }
}

inline void StrCatOne(std::string* out, const std::string& value) { out->append(value); }
inline void StrCatOne(std::string* out, absl::string_view value) { out->append(value.data(), value.size()); }
inline void StrCatOne(std::string* out, const char* value) { if (value) out->append(value); }
inline void StrCatOne(std::string* out, char* value) { if (value) out->append(value); }

template <typename T, typename... Args>
inline void StrCatAppend(std::string* out, const T& value, const Args&... args) {
  StrCatOne(out, value);
  StrCatAppend(out, args...);
}

template <typename... Args>
inline std::string StrCat(const Args&... args) {
  std::string out;
  StrCatAppend(&out, args...);
  return out;
}

}  // namespace absl

#endif  // COMPAT_ABSL_STRINGS_STR_CAT_H_
