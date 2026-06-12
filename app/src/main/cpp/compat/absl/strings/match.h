#ifndef COMPAT_ABSL_STRINGS_MATCH_H_
#define COMPAT_ABSL_STRINGS_MATCH_H_

#include "absl/strings/string_view.h"

namespace absl {

inline bool StartsWith(absl::string_view text, absl::string_view prefix) {
  return text.size() >= prefix.size() &&
         text.substr(0, prefix.size()) == prefix;
}

inline bool EndsWith(absl::string_view text, absl::string_view suffix) {
  return text.size() >= suffix.size() &&
         text.substr(text.size() - suffix.size()) == suffix;
}

inline bool StrContains(absl::string_view haystack, absl::string_view needle) {
  return haystack.find(needle) != absl::string_view::npos;
}

}  // namespace absl

#endif  // COMPAT_ABSL_STRINGS_MATCH_H_
