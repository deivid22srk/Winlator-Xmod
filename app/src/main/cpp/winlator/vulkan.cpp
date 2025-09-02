#include <vulkan/vulkan.h>
#include <iostream>
#include <map>
#include <vector>

#include <jni.h>
#include <dlfcn.h>
#include <android/log.h>
#include <sys/stat.h>
#include <fcntl.h>
#include "../adrenotools/include/adrenotools/driver.h"

#define LOG_TAG "System.out"
#define printf(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

PFN_vkGetInstanceProcAddr gip;
static void *vulkan_handle = nullptr;

char *get_native_library_dir(JNIEnv *env, jobject context) {
    char *native_libdir = nullptr;

    if (context != nullptr) {
        jclass class_ = env->FindClass("com/winlator/cmod/core/AppUtils");
        jmethodID getNativeLibraryDir = env->GetStaticMethodID(class_, "getNativeLibDir",
                                                               "(Landroid/content/Context;)Ljava/lang/String;");
        jstring nativeLibDir = static_cast<jstring>(env->CallStaticObjectMethod(class_,
                                                                                getNativeLibraryDir,
                                                                                context));
        if (nativeLibDir) {
            const char *temp = env->GetStringUTFChars(nativeLibDir, nullptr);
            native_libdir = strdup(temp); // Make a copy to avoid memory leak
            env->ReleaseStringUTFChars(nativeLibDir, temp);
        }
    }
    return native_libdir;
}

char *get_driver_path(JNIEnv *env, jobject context, const char *driver_name) {
    char *driver_path;
    char *absolute_path;

    jclass contextWrapperClass = env->FindClass("android/content/ContextWrapper");
    jmethodID  getFilesDir = env->GetMethodID(contextWrapperClass, "getFilesDir", "()Ljava/io/File;");
    jobject  filesDirObj = env->CallObjectMethod(context, getFilesDir);
    jclass fileClass = env->GetObjectClass(filesDirObj);
    jmethodID getAbsolutePath = env->GetMethodID(fileClass, "getAbsolutePath", "()Ljava/lang/String;");
    jstring absolutePath = static_cast<jstring>(env->CallObjectMethod(filesDirObj,
                                                                      getAbsolutePath));

    if (absolutePath) {
        absolute_path = (char *)env->GetStringUTFChars(absolutePath, nullptr);
        asprintf(&driver_path, "%s/contents/adrenotools/%s/", absolute_path, driver_name);
        env->ReleaseStringUTFChars(absolutePath, absolute_path);
    }

    return driver_path;
}

char *get_library_name(JNIEnv *env, jobject context, const char *driver_name) {
    char *library_name = nullptr;

    jclass adrenotoolsManager = env->FindClass("com/winlator/cmod/contents/AdrenotoolsManager");
    jmethodID constructor = env->GetMethodID(adrenotoolsManager, "<init>", "(Landroid/content/Context;)V");
    jobject  adrenotoolsManagerObj = env->NewObject(adrenotoolsManager, constructor, context);
    jmethodID getLibraryName = env->GetMethodID(adrenotoolsManager, "getLibraryName","(Ljava/lang/String;)Ljava/lang/String;");

    jstring driverName = env->NewStringUTF(driver_name);

    jstring libraryName = static_cast<jstring>(env->CallObjectMethod(adrenotoolsManagerObj,getLibraryName, driverName));

    if (libraryName) {
        const char *temp = env->GetStringUTFChars(libraryName, nullptr);
        library_name = strdup(temp); // Make a copy to avoid memory leak
        env->ReleaseStringUTFChars(libraryName, temp);
    }

    // Clean up local references
    env->DeleteLocalRef(driverName);
    env->DeleteLocalRef(adrenotoolsManagerObj);

    return library_name;
}

void init_original_vulkan() {
    vulkan_handle = dlopen("/system/lib64/libvulkan.so", RTLD_LOCAL | RTLD_NOW);
}

void init_vulkan(JNIEnv  *env, jobject context, const char *driver_name) {
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
        
        // Clean up allocated memory
        if (driver_path) free(driver_path);
        if (library_name) free(library_name);
        if (native_library_dir) free(native_library_dir);
        if (tmpdir) free(tmpdir);
}

VkResult create_instance(jstring driverName, JNIEnv *env, jobject context, VkInstance *instance) {
    VkResult result;
    VkInstanceCreateInfo create_info = {};

    const char *driver_name = env->GetStringUTFChars(driverName, nullptr);

    if (driver_name && strcmp(driver_name, "System"))
      init_vulkan(env, context, driver_name);
    else
      init_original_vulkan();

    // Release the JNI string
    if (driver_name) {
        env->ReleaseStringUTFChars(driverName, driver_name);
    }

    if (!vulkan_handle)
        return VK_ERROR_INITIALIZATION_FAILED;

    gip = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_handle, "vkGetInstanceProcAddr");
    PFN_vkCreateInstance createInstance = (PFN_vkCreateInstance)dlsym(vulkan_handle, "vkCreateInstance");

    create_info.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    create_info.pNext = NULL;
    create_info.flags = 0;
    create_info.pApplicationInfo = NULL;
    create_info.enabledLayerCount = 0;
    create_info.enabledExtensionCount = 0;

    result = createInstance(&create_info, NULL, instance);

    return result;
}

VkResult get_physical_devices(VkInstance instance, std::vector<VkPhysicalDevice> &physical_devices) {
    VkResult result = VK_ERROR_UNKNOWN;
    uint32_t deviceCount;

    PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = (PFN_vkEnumeratePhysicalDevices)gip(instance, "vkEnumeratePhysicalDevices");
    if (!enumeratePhysicalDevices)
        return VK_ERROR_INITIALIZATION_FAILED;

    result = enumeratePhysicalDevices(instance, &deviceCount, NULL);

    if (result == VK_SUCCESS && deviceCount > 0) {
        physical_devices.resize(deviceCount);
        result = enumeratePhysicalDevices(instance, &deviceCount, physical_devices.data());
    }

    return result;
}

extern "C" JNIEXPORT jboolean  JNICALL
Java_com_winlator_cmod_core_GPUInformation_isDriverSupported(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkResult result;
    VkInstance instance;
    std::vector<VkPhysicalDevice> pdevices;
    PFN_vkDestroyInstance destroyInstance;
    jboolean isSupported = false;

    result = create_instance(driverName, env, context, &instance);

    if (result == VK_SUCCESS) {
        result = get_physical_devices(instance, pdevices);
        if (result == VK_SUCCESS && (static_cast<uint32_t>(pdevices.size()) > 0))
            isSupported = true;
        destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");
        destroyInstance(instance, nullptr);
    }

    if (vulkan_handle)
        dlclose(vulkan_handle);

    return isSupported;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVersion(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    std::vector<VkPhysicalDevice> pdevices;
    char *driverVersion;
    VkInstance instance;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewStringUTF("Unknown");
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS) {
        printf("Failed to query physical devices");
        return env->NewStringUTF("Unknown");
    }

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    if (!getPhysicalDeviceProperties || !destroyInstance) {
        printf("Failed to get function pointers");
        return env->NewStringUTF("Unknown");
    }

    for (const auto &pdevice: pdevices) {
        getPhysicalDeviceProperties(pdevice, &props);
        uint32_t vk_driver_major = VK_VERSION_MAJOR(props.driverVersion);
        uint32_t vk_driver_minor = VK_VERSION_MINOR(props.driverVersion);
        uint32_t vk_driver_patch = VK_VERSION_PATCH(props.driverVersion);
        asprintf(&driverVersion, "%d.%d.%d", vk_driver_major, vk_driver_minor,
                 vk_driver_patch);
    }

    destroyInstance(instance, NULL);

    if (vulkan_handle)
        dlclose(vulkan_handle);

    return (env->NewStringUTF(driverVersion));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getVulkanVersion(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    std::vector<VkPhysicalDevice> pdevices;
    char *vulkanVersion;
    VkInstance instance;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewStringUTF("Unknown");
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS) {
        printf("Failed to query physical devices");
        return env->NewStringUTF("Unknown");
    }

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    if (!getPhysicalDeviceProperties || !destroyInstance) {
        printf("Failed to get function pointers");
        return env->NewStringUTF("Unknown");
    }

    for (const auto &pdevice: pdevices) {
        getPhysicalDeviceProperties(pdevice, &props);
        uint32_t vk_driver_major = VK_VERSION_MAJOR(props.apiVersion);
        uint32_t vk_driver_minor = VK_VERSION_MINOR(props.apiVersion);
        uint32_t vk_driver_patch = VK_VERSION_PATCH(props.apiVersion);
        asprintf(&vulkanVersion, "%d.%d.%d", vk_driver_major, vk_driver_minor,
                 vk_driver_patch);
    }

    destroyInstance(instance, NULL);
    if (vulkan_handle)
        dlclose(vulkan_handle);

    return (env->NewStringUTF(vulkanVersion));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_winlator_cmod_core_GPUInformation_getRenderer(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    VkPhysicalDeviceProperties props = {};
    std::vector<VkPhysicalDevice> pdevices;
    char *renderer;
    VkInstance instance;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewStringUTF("Unknown");
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS) {
        printf("Failed to query physical devices");
        return env->NewStringUTF("Unknown");
    }

    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = (PFN_vkGetPhysicalDeviceProperties)gip(instance, "vkGetPhysicalDeviceProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    if (!getPhysicalDeviceProperties || !destroyInstance) {
        printf("Failed to get function pointers");
        return env->NewStringUTF("Unknown");
    }

    for (const auto &pdevice: pdevices) {
        getPhysicalDeviceProperties(pdevice, &props);
        asprintf(&renderer, "%s", props.deviceName);
    }

    destroyInstance(instance, NULL);
    if (vulkan_handle)
        dlclose(vulkan_handle);

    return (env->NewStringUTF(renderer));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_winlator_cmod_core_GPUInformation_enumerateExtensions(JNIEnv *env, jclass obj, jstring driverName, jobject context) {
    jobjectArray extensions;
    VkInstance instance;
    std::vector<VkPhysicalDevice> pdevices;
    uint32_t extensionCount;
    std::vector<VkExtensionProperties> extensionProperties;

    if  (create_instance(driverName, env, context, &instance) != VK_SUCCESS) {
        printf("Failed to create instance");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    if (get_physical_devices(instance, pdevices) != VK_SUCCESS) {
        printf("Failed to query physical devices");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }

    PFN_vkEnumerateDeviceExtensionProperties enumerateDeviceExtensionProperties = (PFN_vkEnumerateDeviceExtensionProperties)gip(instance, "vkEnumerateDeviceExtensionProperties");
    PFN_vkDestroyInstance destroyInstance = (PFN_vkDestroyInstance)gip(instance, "vkDestroyInstance");

    if (!enumerateDeviceExtensionProperties || !destroyInstance) {
        printf("Failed to get function pointers");
        return env->NewObjectArray(0, env->FindClass("java/lang/String"), env->NewStringUTF(""));
    }


    for (const auto &pdevice : pdevices) {
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, NULL);
        extensionProperties.resize(extensionCount);
        enumerateDeviceExtensionProperties(pdevice, NULL, &extensionCount, extensionProperties.data());
        extensions = (jobjectArray)env->NewObjectArray(extensionCount, env->FindClass("java/lang/String"), env->NewStringUTF(""));
        int index = 0;
        for (const auto &extensionProperty : extensionProperties) {
            env->SetObjectArrayElement(extensions, index, env->NewStringUTF(extensionProperty.extensionName));
            index++;
        }
    }

    destroyInstance(instance, NULL);
    if (vulkan_handle)
        dlclose(vulkan_handle);

    return extensions;
}