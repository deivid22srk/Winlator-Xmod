// Otimizações para vulkan.cpp - Implementação de Cache e Pool de Recursos
#ifndef VULKAN_OPTIMIZED_H
#define VULKAN_OPTIMIZED_H

#include <vulkan/vulkan.h>
#include <jni.h>
#include <string>
#include <vector>
#include <unordered_map>
#include <mutex>

// Cache para informações da GPU
struct GPUInfoCache {
    std::string driver_version;
    std::string vulkan_version;
    std::string renderer_name;
    std::vector<std::string> extensions;
    VkPhysicalDeviceProperties device_props;
    bool valid;
    std::string cached_driver_name;
};

// Cache para referências JNI
struct VulkanJNICache {
    jclass adrenotools_manager_class;
    jmethodID get_library_name_method;
    jmethodID get_native_lib_dir_method;
    jclass app_utils_class;
    jmethodID get_native_lib_dir_static_method;
    bool initialized;
    std::mutex mutex;
};

// Cache para instâncias Vulkan
struct VulkanInstanceCache {
    VkInstance instance;
    std::vector<VkPhysicalDevice> physical_devices;
    std::string driver_name;
    void* vulkan_handle;
    PFN_vkGetInstanceProcAddr get_proc_addr;
    bool valid;
    std::mutex mutex;
};

class VulkanOptimizer {
private:
    static GPUInfoCache gpu_cache_;
    static VulkanJNICache jni_cache_;
    static VulkanInstanceCache instance_cache_;

public:
    // Inicializar cache JNI (chamar uma vez no início)
    static void InitializeJNICache(JNIEnv *env, jobject context);
    
    // Obter informações da GPU com cache
    static const GPUInfoCache* GetGPUInfo(JNIEnv *env, jobject context, const char *driver_name);
    
    // Criar instância Vulkan com cache
    static VkResult GetCachedVulkanInstance(JNIEnv *env, jobject context, 
                                           const char *driver_name, VkInstance *instance);
    
    // Limpeza de recursos
    static void Cleanup();
    
    // Utilitários internos
private:
    static void PopulateGPUInfo(GPUInfoCache* cache, VkInstance instance, 
                               const std::vector<VkPhysicalDevice>& devices);
    
    static char* GetDriverPathCached(JNIEnv *env, jobject context, const char *driver_name);
    
    static char* GetLibraryNameCached(JNIEnv *env, jobject context, const char *driver_name);
};

// Implementações otimizadas para as funções JNI existentes
extern "C" {
    JNIEXPORT jboolean JNICALL
    Java_com_winlator_cmod_core_GPUInformation_isDriverSupportedOptimized(
        JNIEnv *env, jclass obj, jstring driverName, jobject context);
    
    JNIEXPORT jstring JNICALL
    Java_com_winlator_cmod_core_GPUInformation_getVersionOptimized(
        JNIEnv *env, jclass obj, jstring driverName, jobject context);
    
    JNIEXPORT jstring JNICALL
    Java_com_winlator_cmod_core_GPUInformation_getVulkanVersionOptimized(
        JNIEnv *env, jclass obj, jstring driverName, jobject context);
    
    JNIEXPORT jstring JNICALL
    Java_com_winlator_cmod_core_GPUInformation_getRendererOptimized(
        JNIEnv *env, jclass obj, jstring driverName, jobject context);
    
    JNIEXPORT jobjectArray JNICALL
    Java_com_winlator_cmod_core_GPUInformation_enumerateExtensionsOptimized(
        JNIEnv *env, jclass obj, jstring driverName, jobject context);
}

// Pool de strings para evitar alocações frequentes
class StringPool {
private:
    static std::vector<std::string> pool_;
    static std::mutex pool_mutex_;
    static size_t pool_index_;

public:
    static const char* GetTempString(const char* str);
    static void ClearPool();
};

#endif // VULKAN_OPTIMIZED_H