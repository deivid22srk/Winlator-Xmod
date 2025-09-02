#include "vulkan_optimized.h"
#include <android/log.h>
#include <dlfcn.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>
#include <cstdio>
#include <cstdlib>
#include "../adrenotools/include/adrenotools/driver.h"

#define LOG_TAG "VulkanOptimized"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Definições das variáveis globais estáticas
GPUInfoCache VulkanOptimizer::gpu_cache_;
VulkanJNICache VulkanOptimizer::jni_cache_;
VulkanInstanceCache VulkanOptimizer::instance_cache_;

void VulkanOptimizer::InitializeJNICache(JNIEnv *env, jobject context) {
    std::lock_guard<std::mutex> lock(jni_cache_.mutex);
    
    if (jni_cache_.initialized) {
        LOGD("JNI Cache already initialized");
        return;
    }
    
    // Cache AdrenoToolsManager class
    jclass local_class = env->FindClass("com/winlator/xmod/contents/AdrenotoolsManager");
    if (local_class) {
        jni_cache_.adrenotools_manager_class = (jclass)env->NewGlobalRef(local_class);
        env->DeleteLocalRef(local_class);
        
        jni_cache_.get_library_name_method = env->GetMethodID(
            jni_cache_.adrenotools_manager_class, 
            "getLibraryName", 
            "(Ljava/lang/String;)Ljava/lang/String;");
    }
    
    // Cache AppUtils class
    local_class = env->FindClass("com/winlator/xmod/core/AppUtils");
    if (local_class) {
        jni_cache_.app_utils_class = (jclass)env->NewGlobalRef(local_class);
        env->DeleteLocalRef(local_class);
        
        jni_cache_.get_native_lib_dir_static_method = env->GetStaticMethodID(
            jni_cache_.app_utils_class,
            "getNativeLibDir",
            "(Landroid/content/Context;)Ljava/lang/String;");
    }
    
    jni_cache_.initialized = true;
    LOGI("JNI Cache initialized successfully");
}

void VulkanOptimizer::Cleanup() {
    std::lock_guard<std::mutex> lock1(jni_cache_.mutex);
    std::lock_guard<std::mutex> lock2(instance_cache_.mutex);
    
    // Cleanup JNI cache
    jni_cache_.initialized = false;
    
    // Cleanup Vulkan cache
    if (instance_cache_.valid) {
        if (instance_cache_.instance != VK_NULL_HANDLE) {
            PFN_vkDestroyInstance destroyInstance = 
                (PFN_vkDestroyInstance)instance_cache_.get_proc_addr(instance_cache_.instance, "vkDestroyInstance");
            if (destroyInstance) destroyInstance(instance_cache_.instance, nullptr);
        }
        if (instance_cache_.vulkan_handle) {
            dlclose(instance_cache_.vulkan_handle);
        }
    }
    
    // Reset cache structs properly
    instance_cache_.instance = VK_NULL_HANDLE;
    instance_cache_.physical_devices.clear();
    instance_cache_.driver_name.clear();
    instance_cache_.vulkan_handle = nullptr;
    instance_cache_.get_proc_addr = nullptr;
    instance_cache_.valid = false;
    
    gpu_cache_.driver_version.clear();
    gpu_cache_.vulkan_version.clear();
    gpu_cache_.renderer_name.clear();
    gpu_cache_.extensions.clear();
    gpu_cache_.cached_driver_name.clear();
    gpu_cache_.valid = false;
    
    LOGI("VulkanOptimizer cleanup completed");
}

bool VulkanOptimizer::HasCachedPhysicalDevices() {
    std::lock_guard<std::mutex> lock(instance_cache_.mutex);
    return instance_cache_.valid && !instance_cache_.physical_devices.empty();
}