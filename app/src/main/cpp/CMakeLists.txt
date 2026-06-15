cmake_minimum_required(VERSION 3.10.2)

project("zxingcpp_wrapper")

# Set paths
set(ZXINGCPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../zxingcpp)

# Define the native library
add_library(zxingcpp_wrapper SHARED
            zxingcpp_jni.cpp)

# Add prebuilt ZXing library
add_library(zxing_prebuilt STATIC IMPORTED)
set_target_properties(zxing_prebuilt PROPERTIES
    IMPORTED_LOCATION ${ZXINGCPP_DIR}/lib/libZXing.a)

# Include headers
target_include_directories(zxingcpp_wrapper PRIVATE
                           ${ZXINGCPP_DIR}/include)

# Find standard Android libraries
find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

# Link libraries
target_link_libraries(zxingcpp_wrapper
                      zxing_prebuilt
                      ${log-lib}
                      ${jnigraphics-lib}
                      atomic)

target_link_options(zxingcpp_wrapper PRIVATE "-moutline-atomics")
