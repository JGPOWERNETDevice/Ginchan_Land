#pragma once

#include <memory>

namespace absl {

using std::make_unique;
using std::unique_ptr;

template <typename T>
std::unique_ptr<T> WrapUnique(T* ptr) {
  return std::unique_ptr<T>(ptr);
}

}  // namespace absl