# WebRTC APM

APM is the audio processing module of WebRTC project in charge of effects
like echo cancellation, noise suppression, etc.  The purpose of this
project is to build a standalone library for Chrome OS system side audio
processing.

# Files content

* scripts to copy over folders and files from upstream WebRTC project
  for APM and its dependencies.
* Copied files from upstream WebRTC project. For example: common_audio,
  modules, rtc_base and system_wrappers.
* webrtc_apm.cc/h C wrappers to access APM functions.
* common-mk based makefiles to build shared library libwebrtc_apm.so

# Update

To update this package to latest upstream WebRC:
* Run `./script/sync-apm.sh path/to/webrtc-checkout/src .`
* `emerge-${BOARD} adhd` to see if anything breaks.
* If emerge success, then we're good.
* Otherwise look into the emerge failure, and then possibly:
  * Update sync-apm.sh to copy more files if upstream directory structure changes.
  * Update BUILD.bazel files if upstream build files has changed.
  * Update the [adhd ebuild file] if dependencies changed.
* Create a new commit

[adhd ebuild file]: https://source.chromium.org/chromiumos/chromiumos/codesearch/+/main:src/third_party/chromiumos-overlay/media-sound/adhd/adhd-9999.ebuild

# Build

There are three ways to build/test this package:

1.  Build webrtc-apm alone
2.  Build webrtc-apm with cras
3.  Build webrtc-apm inside a ChromiumOS SDK

The first one is the easiest to iterate with, and the last one is closest to
the way ChromiumOS infra actually use to build OS images. The second one is
somewhere in-between.

## Build webrtc-apm alone

In the directory same as this README file, run:

```
bazel test //:tests
```

If you see pkg-config errors you are missing some system dependencies.
(Googlers: follow go/cras-dev#prerequisites to install dependencies on gLinux)

## Build webrtc-apm with cras

1.  Clone https://chromium.googlesource.com/chromiumos/third_party/adhd/ to the
    directory adjacent to this directory, so that `adhd` and `webrtc-apm`
    (this repository) are in the same directory.

    NOTE: If you use `repo` to set up a ChromiumOS checkout, the directory
    tree is already set up this way.

2.  In the `adhd` directory, run:

    ```
    bazel test //... @webrtc_apm//:tests
    ```

    There are some configs you can use, such as `--config=local-clang` and
    `--config=asan`. Refer to [adhd's bazelrc](https://chromium.googlesource.com/chromiumos/third_party/adhd/+/refs/heads/main/.bazelrc)
    for details.

    If you see pkg-config errors you are missing some system dependencies.
    (Googlers: follow go/cras-dev#prerequisites to install dependencies on gLinux)

## Build webrtc-apm inside a ChromiumOS SDK

Example:

```
FEATURES=test USE=asan emerge-${BOARD} adhd
```
