cmake_minimum_required(VERSION 3.22.1)
project(fukkatsu_playfair C)

# playfair: FairPlay key decryption for AirPlay screen mirroring.
# Vendored from UxPlay (https://github.com/FDH2/UxPlay, lib/playfair, commit 05b0275), GPL-3.0.
file(GLOB PLAYFAIR_SRC ${CMAKE_CURRENT_SOURCE_DIR}/playfair/*.c)

add_library(playfair SHARED playfair_jni.c ${PLAYFAIR_SRC})
target_include_directories(playfair PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/playfair)
target_compile_options(playfair PRIVATE -O2 -w -fwrapv)
target_link_libraries(playfair m)
