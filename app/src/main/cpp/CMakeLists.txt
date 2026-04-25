cmake_minimum_required(VERSION 3.22.1)

project(aurus_unishox LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- libcodec2 (sources from upstream; codebook*.c pre-generated for Android — no host generate_codebook) ---
include(FetchContent)
FetchContent_Declare(
    codec2_src
    GIT_REPOSITORY https://github.com/drowe67/codec2.git
    GIT_TAG 1.2.0
    GIT_SHALLOW TRUE
)
FetchContent_GetProperties(codec2_src)
if(NOT codec2_src_POPULATED)
    FetchContent_Populate(codec2_src)
endif()

set(CODEC2_SRC_DIR "${codec2_src_SOURCE_DIR}/src")
set(CODEC2_HOSTGEN "${CMAKE_CURRENT_SOURCE_DIR}/codec2_hostgen")

# codebook*.c + codec2/version.h + config.h: pre-generated from codec2 1.2.0 desktop build
# (Android cross-build cannot run generate_codebook). Regenerate after retagging: build
# libcodec2 on host, copy build/src/codebook*.c, build/codec2/version.h, build/config.h;
# ensure #define SIZEOF_INT 4 in config.h.
set(CODEC2_CODEBOOKS
    "${CODEC2_HOSTGEN}/codebook.c"
    "${CODEC2_HOSTGEN}/codebookd.c"
    "${CODEC2_HOSTGEN}/codebookjmv.c"
    "${CODEC2_HOSTGEN}/codebookge.c"
    "${CODEC2_HOSTGEN}/codebooknewamp1.c"
    "${CODEC2_HOSTGEN}/codebooknewamp1_energy.c"
    "${CODEC2_HOSTGEN}/codebooknewamp2.c"
    "${CODEC2_HOSTGEN}/codebooknewamp2_energy.c"
)

set(CODEC2_CORE
    dump.c
    lpc.c
    nlp.c
    postfilter.c
    sine.c
    codec2.c
    codec2_fft.c
    cohpsk.c
    codec2_fifo.c
    fdmdv.c
    fm.c
    fsk.c
    fmfsk.c
    kiss_fft.c
    kiss_fftr.c
    linreg.c
    interp.c
    lsp.c
    mbest.c
    newamp1.c
    ofdm.c
    ofdm_mode.c
    phase.c
    quantise.c
    pack.c
    golay23.c
    freedv_api.c
    freedv_1600.c
    freedv_700.c
    freedv_2020.c
    freedv_fsk.c
    freedv_vhf_framing.c
    freedv_data_channel.c
    varicode.c
    modem_stats.c
    mpdecode_core.c
    phi0.c
    gp_interleaver.c
    interldpc.c
    filter.c
    HRA_112_112.c
    HRA_56_56.c
    HRAb_396_504.c
    H_256_768_22.c
    H_256_512_4.c
    HRAa_1536_512.c
    H_128_256_5.c
    H_2064_516_sparse.c
    H_4096_8192_3d.c
    H_16200_9720.c
    H_1024_2048_4f.c
    H_212_158.c
    ldpc_codes.c
    lpcnet_freq.c
    reliable_text.c
)

set(CODEC2_OBJECTS "")
foreach(c IN LISTS CODEC2_CORE)
    list(APPEND CODEC2_OBJECTS "${CODEC2_SRC_DIR}/${c}")
endforeach()
list(APPEND CODEC2_OBJECTS ${CODEC2_CODEBOOKS})

add_library(codec2 STATIC ${CODEC2_OBJECTS})
target_include_directories(codec2 PUBLIC
    "${CODEC2_SRC_DIR}"
    "${CODEC2_HOSTGEN}"
)
target_compile_options(codec2 PRIVATE -std=gnu11 -Wall -Wno-strict-overflow)
target_compile_definitions(codec2 PRIVATE GIT_HASH="android")

find_library(m-lib m)
if(m-lib)
    target_link_libraries(codec2 PUBLIC ${m-lib})
endif()

add_library(
    unishox2_jni
    SHARED
    unishox2_jni.cpp
    unishox2.cpp
)

add_library(
    codec2_jni
    SHARED
    codec2_jni.cpp
)

find_library(log-lib log)

target_link_libraries(unishox2_jni ${log-lib})
target_link_libraries(codec2_jni PRIVATE codec2 ${log-lib})
