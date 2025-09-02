cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

# Optimize flags based on build type
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -flto -ffast-math -funroll-loops -march=armv8-a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -flto -ffast-math -funroll-loops -march=armv8-a")
elseif(CMAKE_BUILD_TYPE STREQUAL "RelWithDebInfo")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -g")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -g")
else()
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")
endif()

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wno-unused-function -Wno-unused-parameter -Wimplicit-function-declaration")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++17 -Wno-unused-parameter")

# Performance optimization defines
add_definitions(
    -DMAX_EVENTS_OPTIMAL=32
    -DMAX_FDS_OPTIMAL=256
    -DIO_BUFFER_SIZE_OPTIMAL=8192
)

add_subdirectory(OpenXR-SDK)
add_subdirectory(patchelf)
add_subdirectory(adrenotools/lib/linkernsbypass)
add_subdirectory(adrenotools/src/hook)

# Add Winlator shared library
add_library(winlator SHARED
        xr/engine.c
        xr/framebuffer.c
        xr/input.c
        xr/main.c
        xr/math.c
        xr/renderer.c
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/patchelf_wrapper.cpp
        winlator/vulkan.cpp
        adrenotools/src/bcenabler.cpp
        adrenotools/src/driver.cpp
        adrenotools/include/adrenotools/bcenabler.h
        adrenotools/include/adrenotools/driver.h
        adrenotools/include/adrenotools/priv.h
)

target_include_directories(winlator PUBLIC adrenotools/include)
target_include_directories(winlator PRIVATE adrenotools)
target_compile_options(winlator PRIVATE -Wall -Wextra)
target_link_options(winlator PRIVATE "-Wl,-s,--exclude-libs,liblinkernsbypass.a")

target_link_libraries(winlator
        log
        android
        jnigraphics
        openxr_loader
        aaudio
        EGL
        GLESv2
        GLESv3
        linkernsbypass
)
