#ifndef COMPAT_ABSL_STRINGS_HAS_ABSL_STRINGIFY_H_
#define COMPAT_ABSL_STRINGS_HAS_ABSL_STRINGIFY_H_

#include <string>
#include <type_traits>
#include "absl/strings/string_view.h"

namespace absl {
namespace strings_internal {

struct StringifySinkProbe {
  void Append(absl::string_view) {}
  void Append(const std::string&) {}
  void Append(const char*) {}
};

template <typename T, typename = void>
struct HasAbslStringifyImpl : std::false_type {};

template <typename T>
struct HasAbslStringifyImpl<
    T,
    std::void_t<decltype(AbslStringify(
        std::declval<StringifySinkProbe&>(), std::declval<const T&>()))>>
    : std::true_type {};

}  // namespace strings_internal

template <typename T, typename = void>
struct HasAbslStringify : strings_internal::HasAbslStringifyImpl<T> {};

template <typename T>
inline constexpr bool HasAbslStringifyV = HasAbslStringify<T>::value;

namespace strings_internal {

template <typename T, typename = void>
struct HasAbslStringify : absl::HasAbslStringify<T> {};

template <typename T>
inline constexpr bool HasAbslStringifyV = HasAbslStringify<T>::value;

}  // namespace strings_internal
}  // namespace absl

#endif  // COMPAT_ABSL_STRINGS_HAS_ABSL_STRINGIFY_H_
