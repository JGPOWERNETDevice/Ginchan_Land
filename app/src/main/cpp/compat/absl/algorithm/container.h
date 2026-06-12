#ifndef COMPAT_ABSL_ALGORITHM_CONTAINER_H_
#define COMPAT_ABSL_ALGORITHM_CONTAINER_H_

#include <algorithm>
#include <iterator>

namespace absl {

template <typename Container, typename Value>
auto c_find(Container&& c, const Value& v) {
  return std::find(std::begin(c), std::end(c), v);
}

template <typename Container, typename Pred>
auto c_find_if(Container&& c, Pred p) {
  return std::find_if(std::begin(c), std::end(c), p);
}

template <typename Container, typename BinaryPred>
auto c_adjacent_find(Container&& c, BinaryPred p) {
  return std::adjacent_find(std::begin(c), std::end(c), p);
}

template <typename Container, typename Value, typename Compare>
auto c_lower_bound(Container&& c, const Value& v, Compare comp) {
  return std::lower_bound(std::begin(c), std::end(c), v, comp);
}

template <typename Container, typename Value, typename Compare>
auto c_upper_bound(Container&& c, const Value& v, Compare comp) {
  return std::upper_bound(std::begin(c), std::end(c), v, comp);
}

template <typename Container, typename Value, typename Compare>
auto c_equal_range(Container&& c, const Value& v, Compare comp) {
  return std::equal_range(std::begin(c), std::end(c), v, comp);
}

}  // namespace absl

#endif  // COMPAT_ABSL_ALGORITHM_CONTAINER_H_
