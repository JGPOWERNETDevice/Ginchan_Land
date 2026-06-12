#ifndef COMPAT_ABSL_FLAGS_PARSE_H_
#define COMPAT_ABSL_FLAGS_PARSE_H_

#include <vector>
#include <string>

namespace absl {
inline std::vector<char*> ParseCommandLine(int argc, char** argv) {
    (void)argc;
    return std::vector<char*>(argv, argv + argc);
}
}

#endif
