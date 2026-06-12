#ifndef COMPAT_ABSL_FUNCTIONAL_ANY_INVOCABLE_H_
#define COMPAT_ABSL_FUNCTIONAL_ANY_INVOCABLE_H_

#include <functional>
#include <utility>

namespace absl {

template <typename Signature>
class AnyInvocable;

template <typename R, typename... Args>
class AnyInvocable<R(Args...)> {
 public:
  AnyInvocable() = default;
  AnyInvocable(std::nullptr_t) {}

  template <typename F>
  AnyInvocable(F&& f) : fn_(std::forward<F>(f)) {}

  AnyInvocable(AnyInvocable&&) noexcept = default;
  AnyInvocable& operator=(AnyInvocable&&) noexcept = default;
  AnyInvocable(const AnyInvocable&) = default;
  AnyInvocable& operator=(const AnyInvocable&) = default;

  explicit operator bool() const { return static_cast<bool>(fn_); }
  R operator()(Args... args) { return fn_(std::forward<Args>(args)...); }

 private:
  std::function<R(Args...)> fn_;
};

template <typename R, typename... Args>
class AnyInvocable<R(Args...) &&> {
 public:
  AnyInvocable() = default;
  AnyInvocable(std::nullptr_t) {}

  template <typename F>
  AnyInvocable(F&& f) : fn_(std::forward<F>(f)) {}

  AnyInvocable(AnyInvocable&&) noexcept = default;
  AnyInvocable& operator=(AnyInvocable&&) noexcept = default;
  AnyInvocable(const AnyInvocable&) = default;
  AnyInvocable& operator=(const AnyInvocable&) = default;

  explicit operator bool() const { return static_cast<bool>(fn_); }
  R operator()(Args... args) && { return fn_(std::forward<Args>(args)...); }
  R operator()(Args... args) & { return fn_(std::forward<Args>(args)...); }

 private:
  std::function<R(Args...)> fn_;
};

}  // namespace absl

#endif  // COMPAT_ABSL_FUNCTIONAL_ANY_INVOCABLE_H_
