cmake_minimum_required(VERSION 3.22.1)

project("biquad_eq")

# 参数均衡 native 实现（见 biquad_eq.c 顶部说明）。
# 只依赖 JNI，无第三方库，支持所有 ABI。
add_library(biquad_eq SHARED biquad_eq.c)
