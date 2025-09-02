#include <vulkan/vulkan.h>
#include <vector>
#include <string>
#include <mutex>

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <cstring>
#include <unistd.h>
#include "../adrenotools/include/adrenotools/driver.h"

#define LOG_TAG "System.out"
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

static PFN_vkGetInstanceProcAddr gip;
static void *vulkan_handle = nullptr;

static std::mutex g_cache_mutex;
static VkInstance g_cached_instance = VK_NULL_HANDLE;
static std::vector<VkPhysicalDevice> g_cached_devices;
static std::string g_cached_driver_name;

static char *get_native_library_dir(JNIEnv *env, jobject context) {
    char *native_libdir = nullptr;
    if (context != nullptr) {
        jclass class_ = env->FindClass("com/winlator/xmod/core/AppUtils");
        jmethodID getNativeLibraryDir = env->GetStaticMethodID(class_, "getNativeLibDir",
                                                               "(Landroid/content/Context;)Ljava/lang/String;");
        jstring nativeLibDir = static_cast<jstring>(env->CallStaticObjectMethod(class_,
                                                                                getNativeLibraryDir,
                                                                                context));
        if (nativeLibDir) {
            const char *temp = env->GetStringUTFChars(nativeLibDir, nullptr);
            native_libdir = strdup(temp);
            env->ReleaseStringUTFChars(nativeLibDir, temp);
        }
    }
    return native_libdir;
}

static char *get_driver_path(JNIEnv *env, jobject context, const char *driver_name) {
    char *driver_path = nullptr;
    char *absolute_path;

    jclass contextWrapperClass = env->FindClass("android/content/ContextWrapper");
    jmethodID  getFilesDir = env->GetMethodID(contextWrapperClass, "getFilesDir", "()Ljava/io/File;");
    jobject  filesDirObj = env->CallObjectMethod(context, getFilesDir);
    jclass fileClass = env->GetObjectClass(filesDirObj);
    jmethodID getAbsolutePath = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring absolutePath = static_cast<jstring>(env->CallObjectMethod(filesDirObj, getAbsolutePath));

    if (absolutePath) {
        absolute_path = (char *)env->GetStringUTFChars(absolutePath, nullptr);
        asprintf(&driver_path, "%s/contents/adrenotools/%s/", absolute_path, driver_name);
        env->ReleaseStringUTFChars(absolutePath, absolute_path);
    }

    return driver_path;
}

static char *get_library_name(JNIEnv *env, jobject context, const char *driver_name) {
    char *library_name = nullptr;

    jclass adrenotoolsManager = env->FindClass("com/winlator/xmod/contents/AdrenotoolsManager");
    jmethodID constructor = env->GetMethodID(adrenotoolsManager, "<init>", "(Landroid/content/Context;)V");
    jobject  adrenotoolsManagerObj = env->NewObject(adrenotoolsManager, constructor, context);
    jmethodID getLibraryName = env->GetMethodID(adrenotoolsManager, "getLibraryName","(Ljava/lang/String;)Ljava/lang/String;");

    jstring driverName = env->NewStringUTF(driver_name);
    jstring libraryName = static_cast<jstring>(env->CallObjectMethod(adrenotoolsManagerObj, getLibraryName, driverName));

    if (libraryName) {
        const char *temp = env->GetStringUTFChars(libraryName, nullptr);
        library_name = strdup(temp);
        env->ReleaseStringUTFChars(libraryName, temp);
    }

    env->DeleteLocalRef(driverName);
    env->DeleteLocalRef(adrenotoolsManagerObj);

    return library_name;
}

static void init_original_vulkan() {
    vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_LOCAL | RTLD_NOW);
}

static void init_vulkan(JNIEnv  *env, jobject context, const char *driver_name) {
    char *tmpdir = nullptr;
    char *library_name = nullptr;
    char *native_library_dir = nullptr;

    char *driver_path = get_driver_path(env, context, driver_name);

    if (driver_path && (access(driver_path, F_OK) == 0)) {
        library_name = get_library_name(env, context, driver_name);
        native_library_dir = get_native_library_dir(env, context);
        asprintf(&tmpdir, "%s%s", driver_path, "temp");
        mkdir(tmpdir, S_IRWXU | S_IRWXG);
    }

    vulkan_handle = adrenotools_open_libvulkan(RTLD_LOCAL | RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, tmpdir, native_library_dir, driver_path, library_name, nullptr, nullptr);

    if (driver_path) free(driver_path);
    if (library_name) free(library_name);
    if (native_library_dir) free(native_library_dir);
    if (tmpdir) free(tmpdir);
}

static VkResult create_instance(jstring driverName, JNIEnv *env, jobject context, VkInstance *instance) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);

    const char *driver_name = env->GetStringUTFChars(driverName, nullptr);

    if (g_cached_instance != VK_NULL_HANDLE && g_cached_driver_name == (driver_name ? driver_name : "")) {
        *instance = g_cached_instance;
        env->ReleaseStringUTFChars(driverName, driver_name);
        return VK_SUCCESS;
    }

    if (g_cached_instance != VK_NULL_HANDLE) {
        PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(g_cached_instance, "vkDestroyInstance");
        if (destroyInstance) destroyInstance(g_cached_instance, nullptr);
        g_cached_instance = VK_NULL_HANDLE;
        g_cached_devices.clear();
    }

    if (vulkan_handle) {
        dlclose(vulkan_handle);
        vulkan_handle = nullptr;
    }

    if (driver_name && strcmp(driver_name, "System"))
        init_vulkan(env, context, driver_name);
    else
        init_original_vulkan();

    if (driver_name) env->ReleaseStringUTFChars(driverName, driver_name);

    if (!vulkan_handle)
        return VK_ERROR_INITIALIZATION_FAILED;

    gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)dlsym(vulkan_handle, "vkCreateInstance");

    VkInstanceCreateInfo create_info = {};
    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pNext = NULL;
    create_info.flags = 0;
    create_info.pApplicationInfo = NULL;
    create_info.enabledLayerCount = 0;
    create_info.enabledExtensionCount = 0;

    VkResult result = createInstance(&create_info, NULL, &g_cached_instance);
    if (result == VK_SUCCESS) {
        const char *name_cstr = env->GetStringUTFChars(driverName, nullptr);
        g_cached_driver_name = name_cstr ? name_cstr : "";
        if (name_cstr) env->ReleaseStringUTFChars(driverName, name_cstr);
        *instance = g_cached_instance;
    }

    return result;
}

static VkResult get_physical_devices(VkInstance instance, std::vector<VkPhysicalDevice> &physical_devices) {
    std::lock_guard<std::mutex> lock(g_cache_mutex);

    if (instance == g_cached_instance && !g_cached_devices.empty()) {
        physical_devices = g_cached_devices;
        return VK_SUCCESS;
    }

    uint32_t deviceCount = 0;
    PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)gip(instance, "vkEnumeratePhysicalDevices");
    if (!enumeratePhysicalDevices)
        return VK_ERROR_INITIALIZATION_FAILED;

    VkResult result = enumeratePhysicalDevices(instance, &deviceCount, NULL);

    if (result == VK_SUCCESS && deviceCount > 0) {
        physical_devices.resize(deviceCount);
        result = enumeratePhysicalDevices(instance, &deviceCount, physical_devices.data());
        if (instance == g_cached_instance && result == VK_SUCCESS) {
            g_cached_devices = physical_devices;
        }
    }

    return result;
}

extern "C" JNIEXPORT jboolean  JNICALL
Java_com_winlator_xmod_core_GPUInformation_isDriverSupported(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkResult result;
    VkInstance instance;
    std::vector<VkPhysicalDevice> pdevices;
    jboolean isSupported = false;

    result = create_instance(driverName, env, context, &instance);

    if (result == VK_SUCCESS) {
        result = get_physical_devices(instance, pdevices);
        if (result == VK_SUCCESS && (static_cast<uint32_t>(pdevices.size()) > 0))
            isSupported = true;
    }

    return isSupported;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_xmod_core_GPUInformation_getVersion(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    std::vector<VkPhysicalDevice> pdevices;
    VkInstance instance;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewStringUTF("Unknown");
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS || pdevices.empty()) {
        printf("Failed to query physical devices");
        return env->NewStringUTF("Unknown");
    }

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    if (!getPhysicalDeviceProperties) {
        printf("Failed to get function pointers");
        return env->NewStringUTF("Unknown");
    }

    getPhysicalDeviceProperties(pdevices[0], &props);
    char buf[32];
    snprintf(buf, sizeof(buf), "%u.%u.%u", VK_VERSION_MAJOR(props.driverVersion), VK_VERSION_MINOR(props.driverVersion), VK_VERSION_PATCH(props.driverVersion));

    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_xmod_core_GPUInformation_getVulkanVersion(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    std::vector<VkPhysicalDevice> pdevices;
    VkInstance instance;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewStringUTF("Unknown");
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS || pdevices.empty()) {
        printf("Failed to query physical devices");
        return env->NewStringUTF("Unknown");
    }

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    if (!getPhysicalDeviceProperties) {
        printf("Failed to get function pointers");
        return env->NewStringUTF("Unknown");
    }

    getPhysicalDeviceProperties(pdevices[0], &props);
    char buf[32];
    snprintf(buf, sizeof(buf), "%u.%u.%u", VK_VERSION_MAJOR(props.apiVersion), VK_VERSION_MINOR(props.apiVersion), VK_VERSION_PATCH(props.apiVersion));

    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_xmod_core_GPUInformation_getRenderer(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    std::vector<VkPhysicalDevice> pdevices;
    VkInstance instance;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewStringUTF("Unknown");
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS || pdevices.empty()) {
        printf("Failed to query physical devices");
        return env->NewStringUTF("Unknown");
    }

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    if (!getPhysicalDeviceProperties) {
        printf("Failed to get function pointers");
        return env->NewStringUTF("Unknown");
    }

    getPhysicalDeviceProperties(pdevices[0], &props);
    return env->NewStringUTF(props.deviceName);
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_winlator_xmod_core_GPUInformation_enumerateExtensions(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    jobjectArray extensions;
    VkInstance instance;
    std::vector<VkPhysicalDevice> pdevices;
    uint32_t extensionCount;
    std::vector<VkExtensionProperties> extensionProperties;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS || pdevices.empty()) {
        printf("Failed to query physical devices");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    PFN_vkEnumerateDeviceExtensionProperties enumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)gip(instance, "vkEnumerateDeviceExtensionProperties");
    if (!enumerateDeviceExtensionProperties) {
        printf("Failed to get function pointers");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    for (const auto &pdevice : pdevices) {
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, NULL);
        extensionProperties.resize(extensionCount);
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, extensionProperties.data());
        jclass stringClass = env->FindClass("java/lang/String");
        extensions = (jobjectArray)env->NewObjectArray(extensionCount, stringClass, env->NewStringUTF(""));
        int index = 0;
        for (const auto &extensionProperty : extensionProperties) {
            env->SetObjectArrayElement(extensions, index, env->NewStringUTF(extensionProperty.extensionName));
            index++;
        }
    }

    return extensions;
}