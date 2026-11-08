# Termux runs on Android/aarch64, while the official NDK ships Linux x86_64
# host tools. Reuse the NDK's Android sysroot and ABI settings, with Termux's
# native ARM64 LLVM tools as the host executables.

if(NOT DEFINED CMAKE_ANDROID_NDK)
  if(DEFINED FCC_NDK_ROOT)
    set(CMAKE_ANDROID_NDK "${FCC_NDK_ROOT}")
  elseif(DEFINED ANDROID_NDK)
    set(CMAKE_ANDROID_NDK "${ANDROID_NDK}")
  elseif(DEFINED ENV{FCC_NDK_ROOT})
    set(CMAKE_ANDROID_NDK "$ENV{FCC_NDK_ROOT}")
  else()
    message(FATAL_ERROR "CMAKE_ANDROID_NDK must point to the installed Android NDK")
  endif()
endif()
set(FCC_NDK_ROOT "${CMAKE_ANDROID_NDK}" CACHE PATH "Android NDK passed to nested CMake checks" FORCE)
list(APPEND CMAKE_TRY_COMPILE_PLATFORM_VARIABLES CMAKE_ANDROID_NDK FCC_NDK_ROOT)

# The NDK toolchain only recognizes Linux, macOS, and Windows as build hosts.
# Keep its Linux sysroot layout, then replace every host executable below.
set(ANDROID_HOST_TAG linux-x86_64)
include("${CMAKE_ANDROID_NDK}/build/cmake/android.toolchain.cmake")

if(DEFINED ENV{PREFIX})
  set(_termux_prefix "$ENV{PREFIX}")
else()
  set(_termux_prefix "/data/data/com.termux/files/usr")
endif()
set(_ndk_llvm_root "${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64")
set(_ndk_clang_resource_dir "${_ndk_llvm_root}/lib/clang/18")
execute_process(
  COMMAND "${_termux_prefix}/bin/clang" -print-resource-dir
  OUTPUT_VARIABLE _termux_clang_resource_dir
  OUTPUT_STRIP_TRAILING_WHITESPACE
  COMMAND_ERROR_IS_FATAL ANY
)

set(CMAKE_C_COMPILER "${_termux_prefix}/bin/clang" CACHE FILEPATH "Termux ARM64 C compiler" FORCE)
set(CMAKE_CXX_COMPILER "${_termux_prefix}/bin/clang++" CACHE FILEPATH "Termux ARM64 C++ compiler" FORCE)
set(CMAKE_ASM_COMPILER "${_termux_prefix}/bin/clang" CACHE FILEPATH "Termux ARM64 assembler driver" FORCE)
set(CMAKE_LINKER "${_termux_prefix}/bin/ld.lld" CACHE FILEPATH "Termux ARM64 linker" FORCE)
set(CMAKE_AR "${_termux_prefix}/bin/llvm-ar" CACHE FILEPATH "Termux ARM64 archiver" FORCE)
set(CMAKE_RANLIB "${_termux_prefix}/bin/llvm-ranlib" CACHE FILEPATH "Termux ARM64 ranlib" FORCE)
set(CMAKE_STRIP "${_termux_prefix}/bin/llvm-strip" CACHE FILEPATH "Termux ARM64 strip" FORCE)
set(CMAKE_C_COMPILER_AR "${_termux_prefix}/bin/llvm-ar" CACHE FILEPATH "Termux ARM64 C archiver" FORCE)
set(CMAKE_C_COMPILER_RANLIB "${_termux_prefix}/bin/llvm-ranlib" CACHE FILEPATH "Termux ARM64 C ranlib" FORCE)
set(CMAKE_CXX_COMPILER_AR "${_termux_prefix}/bin/llvm-ar" CACHE FILEPATH "Termux ARM64 C++ archiver" FORCE)
set(CMAKE_CXX_COMPILER_RANLIB "${_termux_prefix}/bin/llvm-ranlib" CACHE FILEPATH "Termux ARM64 C++ ranlib" FORCE)
# CMP0126 may preserve the NDK's normal variables after a cache assignment.
foreach(_host_tool
    CMAKE_C_COMPILER CMAKE_CXX_COMPILER CMAKE_ASM_COMPILER CMAKE_LINKER
    CMAKE_AR CMAKE_RANLIB CMAKE_STRIP CMAKE_C_COMPILER_AR CMAKE_C_COMPILER_RANLIB
    CMAKE_CXX_COMPILER_AR CMAKE_CXX_COMPILER_RANLIB)
  get_property(_host_tool_path CACHE "${_host_tool}" PROPERTY VALUE)
  set(${_host_tool} "${_host_tool_path}")
endforeach()
if(DEFINED ENV{HOME})
  set(_termux_cmake_wrapper "$ENV{HOME}/.cache/fcc-termux-cmake-arm64/bin/cmake")
  if(EXISTS "${_termux_cmake_wrapper}")
    set(CMAKE_COMMAND "${_termux_cmake_wrapper}" CACHE INTERNAL "CMake wrapper with Termux runtime environment" FORCE)
    set(CMAKE_COMMAND "${_termux_cmake_wrapper}")
  endif()
endif()

# Pair the Termux Clang driver with the NDK's compiler-rt, libatomic, and
# libunwind built for Android/arm64 instead of Termux's Android-host runtime.
foreach(_flag_cache CMAKE_C_FLAGS CMAKE_CXX_FLAGS CMAKE_ASM_FLAGS)
  # The NDK prepends its defaults as normal variables on every configure.
  # Cache only the extra flags, so reconfiguration cannot accumulate defaults.
  get_property(_current_flags CACHE "${_flag_cache}" PROPERTY VALUE)
  string(REPLACE "${ANDROID_COMPILER_FLAGS}" "" _current_flags "${_current_flags}")
  set(_ndk_default_flags "${ANDROID_COMPILER_FLAGS}")
  if(_flag_cache STREQUAL "CMAKE_CXX_FLAGS" AND ANDROID_COMPILER_FLAGS_CXX)
    string(REPLACE "${ANDROID_COMPILER_FLAGS_CXX}" "" _current_flags "${_current_flags}")
    string(APPEND _ndk_default_flags " ${ANDROID_COMPILER_FLAGS_CXX}")
  endif()
  string(FIND "${_current_flags}" "-resource-dir=${_ndk_clang_resource_dir}" _resource_flag_index)
  if(_resource_flag_index EQUAL -1)
    string(APPEND _current_flags " -resource-dir=${_ndk_clang_resource_dir}")
  endif()
  # Intrinsic headers must match the compiler version. Disable the NDK's
  # older builtin headers while still resolving target runtime archives there.
  string(FIND "${_current_flags}" "-nobuiltininc" _builtin_header_flag_index)
  if(_builtin_header_flag_index EQUAL -1)
    string(APPEND _current_flags " -nobuiltininc -idirafter${_termux_clang_resource_dir}/include")
  endif()
  string(STRIP "${_current_flags}" _current_flags)
  set(${_flag_cache} "${_current_flags}" CACHE STRING "Android/arm64 compiler flags" FORCE)
  set(${_flag_cache} "${_ndk_default_flags} ${_current_flags}")
endforeach()
