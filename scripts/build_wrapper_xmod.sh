#!/usr/bin/env bash
set -euo pipefail

# Build script to produce graphics_driver/wrapper-xmod.tzst compatible with Winlator
# Requirements (on your machine):
# - Android NDK r26+ (for aarch64)
# - Meson/Ninja, Python3, clang toolchains
# - Zstd, tar
# - Optional: prebuilt helper libs (adrenotools + hooks)

REPO_URL=${REPO_URL:-https://github.com/deivid22srk/bionic-vulkan-wrapper}
REPO_REF=${REPO_REF:-xmod-mobile-optimizations}
WORKDIR=${WORKDIR:-$(pwd)}
OUTDIR=${OUTDIR:-$WORKDIR/out/wrapper-xmod}
ASSET=${ASSET:-$WORKDIR/app/src/main/assets/graphics_driver/wrapper-xmod.tzst}

rm -rf "$OUTDIR" "$WORKDIR/out" && mkdir -p "$OUTDIR/usr/lib" "$OUTDIR/usr/share/vulkan/icd.d"

# 1) Fetch sources
mkdir -p "$WORKDIR/out/src"
if [ ! -d "$WORKDIR/out/src/bionic-vulkan-wrapper" ]; then
  git clone --depth=1 --branch "$REPO_REF" "$REPO_URL" "$WORKDIR/out/src/bionic-vulkan-wrapper"
else
  git -C "$WORKDIR/out/src/bionic-vulkan-wrapper" fetch origin "$REPO_REF" && git -C "$WORKDIR/out/src/bionic-vulkan-wrapper" checkout "$REPO_REF"
fi

SRC="$WORKDIR/out/src/bionic-vulkan-wrapper"

# 2) Build libvulkan_wrapper.so (example using Meson; adapt to your environment)
# This is a template — adjust cross file/ndk path if needed
ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-$HOME/Android/Sdk/ndk/26.3.11579264}
CROSS_FILE="$WORKDIR/out/android-aarch64.cross"
cat > "$CROSS_FILE" <<'EOF'
[binaries]
c = 'clang'
cpp = 'clang++'
ar = 'llvm-ar'
strip = 'llvm-strip'

[host_machine]
system = 'android'
cpu_family = 'aarch64'
cpu = 'armv8-a'
endian = 'little'

[properties]
needs_exe_wrapper = true
skip_sanity_check = true
EOF

# Example build steps (placeholder):
# meson setup "$WORKDIR/out/build" "$SRC" --cross-file "$CROSS_FILE" -Dplatforms=android
# meson compile -C "$WORKDIR/out/build"
# cp "$WORKDIR/out/build/libvulkan_wrapper.so" "$OUTDIR/usr/lib/"

# 3) Provide required companion libs
# If your build does not produce these, copy from your known-good set or SDK
# For initial integration, we reuse the existing libs from wrapper.tzst
TMPDIR=$(mktemp -d)
tar --zstd -xf "$WORKDIR/app/src/main/assets/graphics_driver/wrapper.tzst" -C "$TMPDIR"
cp -a "$TMPDIR/usr/lib/"* "$OUTDIR/usr/lib/"
cp -a "$TMPDIR/usr/share/vulkan/icd.d/wrapper_icd.aarch64.json" "$OUTDIR/usr/share/vulkan/icd.d/"
rm -rf "$TMPDIR"

# If you compiled a new libvulkan_wrapper.so, overwrite here
# cp path/to/new/libvulkan_wrapper.so "$OUTDIR/usr/lib/"

# 4) Package into tzst
mkdir -p "$(dirname "$ASSET")"
(cd "$OUTDIR"/.. && tar --zstd -cf "$ASSET" "$(basename "$OUTDIR")")

echo "Wrapper Xmod packaged at: $ASSET"
