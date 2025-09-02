/**
 * Implementação Prática das Otimizações mais Críticas
 * Aplicar essas modificações primeiro para ganhos imediatos de performance
 */

#include <jni.h>
#include <android/log.h>
#include <vulkan/vulkan.h>
#include <unordered_map>
#include <mutex>
#include <string>
#include <sys/epoll.h>
#include <unistd.h>
#include <sys/sysconf.h>

// =========================================================================
// 1. CORREÇÃO CRÍTICA: Vazamentos de Memória no Vulkan.cpp
// =========================================================================

// ANTES (Problemático):
/*
char *library_name = (char *)env->GetStringUTFChars(libraryName, nullptr);
// String nunca liberada - VAZAMENTO DE MEMÓRIA
*/

// DEPOIS (Corrigido):
class ScopedJNIString {
private:
    JNIEnv* env_;
    jstring jstr_;
    const char* cstr_;
public:
    ScopedJNIString(JNIEnv* env, jstring jstr) 
        : env_(env), jstr_(jstr), cstr_(env->GetStringUTFChars(jstr, nullptr)) {}
    
    ~ScopedJNIString() {
        if (cstr_) env_->ReleaseStringUTFChars(jstr_, cstr_);
    }
    
    const char* c_str() const { return cstr_; }
    operator const char*() const { return cstr_; }
};

// Uso:
// ScopedJNIString driver_name(env, driverName);
// const char *name_ptr = driver_name.c_str();

// =========================================================================
// 2. CACHE DE JNI METHOD IDs - Aplicar Imediatamente
// =========================================================================

struct GlobalJNICache {
    // Vulkan-related
    jclass adrenotools_class;
    jmethodID get_library_name_method;
    jclass app_utils_class;
    jmethodID get_native_lib_dir_method;
    
    // XConnector-related  
    jmethodID handle_new_connection;
    jmethodID handle_existing_connection;
    jmethodID add_ancillary_fd;
    
    bool initialized;
    std::mutex mutex;
};

static GlobalJNICache g_jni_cache = {0};

// Inicializar cache (chamar uma vez no JavaVM_OnLoad)
void InitJNICache(JNIEnv* env) {
    std::lock_guard<std::mutex> lock(g_jni_cache.mutex);
    if (g_jni_cache.initialized) return;
    
    // Cache Vulkan classes/methods
    jclass local_class = env->FindClass("com/winlator/cmod/contents/AdrenotoolsManager");
    g_jni_cache.adrenotools_class = (jclass)env->NewGlobalRef(local_class);
    env->DeleteLocalRef(local_class);
    
    g_jni_cache.get_library_name_method = env->GetMethodID(
        g_jni_cache.adrenotools_class, "getLibraryName", "(Ljava/lang/String;)Ljava/lang/String;");
    
    local_class = env->FindClass("com/winlator/cmod/core/AppUtils");
    g_jni_cache.app_utils_class = (jclass)env->NewGlobalRef(local_class);
    env->DeleteLocalRef(local_class);
    
    g_jni_cache.get_native_lib_dir_method = env->GetStaticMethodID(
        g_jni_cache.app_utils_class, "getNativeLibDir", "(Landroid/content/Context;)Ljava/lang/String;");
    
    g_jni_cache.initialized = true;
    __android_log_print(ANDROID_LOG_INFO, "Winlator", "JNI Cache initialized successfully");
}

// =========================================================================
// 3. CACHE DE INSTÂNCIAS VULKAN - Ganho de Performance Significativo
// =========================================================================

struct VulkanInstanceCache {
    VkInstance instance;
    std::vector<VkPhysicalDevice> physical_devices;
    VkPhysicalDeviceProperties device_props;
    std::string driver_name;
    void* vulkan_handle;
    PFN_vkGetInstanceProcAddr get_proc_addr;
    bool valid;
    std::mutex mutex;
    
    VulkanInstanceCache() : instance(VK_NULL_HANDLE), vulkan_handle(nullptr), 
                           get_proc_addr(nullptr), valid(false) {}
};

static VulkanInstanceCache g_vulkan_cache;

VkResult GetCachedVulkanInstance(const char* driver_name, JNIEnv* env, 
                                jobject context, VkInstance* out_instance) {
    std::lock_guard<std::mutex> lock(g_vulkan_cache.mutex);
    
    // Check if cached instance is valid for this driver
    if (g_vulkan_cache.valid && g_vulkan_cache.driver_name == driver_name) {
        *out_instance = g_vulkan_cache.instance;
        return VK_SUCCESS;
    }
    
    // Need to create new instance
    VkResult result = create_instance_internal(driver_name, env, context, &g_vulkan_cache.instance);
    if (result == VK_SUCCESS) {
        g_vulkan_cache.driver_name = driver_name;
        g_vulkan_cache.valid = true;
        
        // Cache physical devices
        uint32_t device_count = 0;
        PFN_vkEnumeratePhysicalDevices enum_devices = 
            (PFN_vkEnumeratePhysicalDevices)g_vulkan_cache.get_proc_addr(
                g_vulkan_cache.instance, "vkEnumeratePhysicalDevices");
        
        enum_devices(g_vulkan_cache.instance, &device_count, nullptr);
        g_vulkan_cache.physical_devices.resize(device_count);
        enum_devices(g_vulkan_cache.instance, &device_count, g_vulkan_cache.physical_devices.data());
        
        // Cache device properties
        if (!g_vulkan_cache.physical_devices.empty()) {
            PFN_vkGetPhysicalDeviceProperties get_props = 
                (PFN_vkGetPhysicalDeviceProperties)g_vulkan_cache.get_proc_addr(
                    g_vulkan_cache.instance, "vkGetPhysicalDeviceProperties");
            get_props(g_vulkan_cache.physical_devices[0], &g_vulkan_cache.device_props);
        }
        
        *out_instance = g_vulkan_cache.instance;
    }
    
    return result;
}

// =========================================================================
// 4. CONFIGURAÇÃO DINÂMICA DE RECURSOS - Adaptar ao Hardware
// =========================================================================

struct OptimalConfig {
    int max_epoll_events;
    int max_fds;
    int io_buffer_size;
    int memory_pool_size;
};

OptimalConfig GetOptimalConfiguration() {
    OptimalConfig config = {0};
    
    // Detectar número de cores de CPU
    long num_cores = sysconf(_SC_NPROCESSORS_ONLN);
    if (num_cores <= 0) num_cores = 4; // fallback
    
    // Detectar memória disponível (simplified)
    long page_size = sysconf(_SC_PAGE_SIZE);
    long num_pages = sysconf(_SC_PHYS_PAGES);
    long memory_mb = (num_pages * page_size) / (1024 * 1024);
    
    // Configurar baseado no hardware
    if (num_cores <= 4 && memory_mb < 4096) {
        // Dispositivos low-end
        config.max_epoll_events = 16;
        config.max_fds = 128;
        config.io_buffer_size = 4096;
        config.memory_pool_size = 8;
    } else if (num_cores <= 8 && memory_mb < 8192) {
        // Dispositivos mid-range
        config.max_epoll_events = 32;
        config.max_fds = 256;
        config.io_buffer_size = 8192;
        config.memory_pool_size = 16;
    } else {
        // Dispositivos high-end
        config.max_epoll_events = 64;
        config.max_fds = 512;
        config.io_buffer_size = 16384;
        config.memory_pool_size = 32;
    }
    
    __android_log_print(ANDROID_LOG_INFO, "Winlator", 
        "Optimal config: cores=%ld, mem=%ldMB, events=%d, fds=%d", 
        num_cores, memory_mb, config.max_epoll_events, config.max_fds);
    
    return config;
}

// =========================================================================
// 5. POOL DE BUFFERS I/O - Reduzir Alocações
// =========================================================================

struct IOBuffer {
    char* data;
    size_t size;
    bool in_use;
};

class SimpleIOPool {
private:
    std::vector<IOBuffer> buffers_;
    std::mutex mutex_;
    
public:
    SimpleIOPool(int pool_size, int buffer_size) {
        buffers_.resize(pool_size);
        for (auto& buffer : buffers_) {
            buffer.data = (char*)malloc(buffer_size);
            buffer.size = buffer_size;
            buffer.in_use = false;
        }
    }
    
    ~SimpleIOPool() {
        for (auto& buffer : buffers_) {
            if (buffer.data) free(buffer.data);
        }
    }
    
    char* Acquire(size_t min_size) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& buffer : buffers_) {
            if (!buffer.in_use && buffer.size >= min_size) {
                buffer.in_use = true;
                return buffer.data;
            }
        }
        // Fallback: allocate new buffer
        return (char*)malloc(min_size);
    }
    
    void Release(char* ptr) {
        std::lock_guard<std::mutex> lock(mutex_);
        for (auto& buffer : buffers_) {
            if (buffer.data == ptr) {
                buffer.in_use = false;
                return;
            }
        }
        // Was fallback allocation
        free(ptr);
    }
};

static SimpleIOPool* g_io_pool = nullptr;

void InitIOPool() {
    if (!g_io_pool) {
        OptimalConfig config = GetOptimalConfiguration();
        g_io_pool = new SimpleIOPool(config.memory_pool_size, config.io_buffer_size);
    }
}

// =========================================================================
// 6. MODIFICAÇÕES IMEDIATAS PARA APLICAR
// =========================================================================

/*
MODIFICAÇÕES PRIORITÁRIAS (aplicar primeiro):

1. Substituir todas as chamadas GetStringUTFChars sem Release por ScopedJNIString

2. Adicionar InitJNICache() no JNI_OnLoad:

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    JNIEnv* env;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    
    InitJNICache(env);
    InitIOPool();
    
    return JNI_VERSION_1_6;
}

3. Usar cache JNI ao invés de FindClass/GetMethodID repetidos

4. Trocar constantes #define MAX_EVENTS 10 por configuração dinâmica

5. Implementar GetCachedVulkanInstance() para as funções Vulkan

6. Usar pool de I/O para read/write operations
*/

// =========================================================================
// 7. MÉTRICAS SIMPLES DE PERFORMANCE  
// =========================================================================

struct PerfCounters {
    std::atomic<uint64_t> vulkan_cache_hits;
    std::atomic<uint64_t> vulkan_cache_misses;
    std::atomic<uint64_t> jni_cache_hits;
    std::atomic<uint64_t> io_pool_hits;
    std::atomic<uint64_t> io_pool_misses;
};

static PerfCounters g_perf_counters;

void LogPerfStats() {
    __android_log_print(ANDROID_LOG_INFO, "WinlatorPerf", 
        "VK cache hits=%llu misses=%llu, JNI hits=%llu, IO pool hits=%llu misses=%llu",
        (unsigned long long)g_perf_counters.vulkan_cache_hits.load(),
        (unsigned long long)g_perf_counters.vulkan_cache_misses.load(),
        (unsigned long long)g_perf_counters.jni_cache_hits.load(),
        (unsigned long long)g_perf_counters.io_pool_hits.load(),
        (unsigned long long)g_perf_counters.io_pool_misses.load());
}