// Otimizações para xconnector_epoll.c - Performance e Scalability
#ifndef XCONNECTOR_OPTIMIZED_H
#define XCONNECTOR_OPTIMIZED_H

#include <jni.h>
#include <sys/epoll.h>
#include <pthread.h>
#include <stdatomic.h>

// Configuração dinâmica baseada no hardware
struct EpollConfig {
    int max_events;
    int max_fds;
    int io_buffer_size;
    int pool_size;
};

// Cache para Method IDs JNI
struct EpollJNICache {
    jmethodID handle_new_connection;
    jmethodID handle_existing_connection;
    jmethodID add_ancillary_fd;
    jclass connector_class;
    bool initialized;
    pthread_mutex_t mutex;
};

// Pool de buffers para I/O
struct IOBuffer {
    char *data;
    size_t size;
    atomic_bool in_use;
};

struct IOBufferPool {
    IOBuffer *buffers;
    int pool_size;
    int buffer_size;
    atomic_int next_buffer;
    pthread_mutex_t mutex;
};

// Estrutura principal do otimizador
struct EpollOptimizer {
    EpollConfig config;
    EpollJNICache jni_cache;
    IOBufferPool io_pool;
    struct epoll_event *events_buffer;
    bool initialized;
};

// Funções de inicialização e configuração
void InitializeEpollOptimizer(struct EpollOptimizer *optimizer);
void ConfigureForDevice(struct EpollOptimizer *optimizer);
void InitializeJNICache(struct EpollOptimizer *optimizer, JNIEnv *env, jobject obj);
void InitializeIOBufferPool(struct EpollOptimizer *optimizer);

// Pool de buffers I/O
IOBuffer* AcquireIOBuffer(struct EpollOptimizer *optimizer, size_t min_size);
void ReleaseIOBuffer(struct EpollOptimizer *optimizer, IOBuffer *buffer);

// Limpeza de recursos
void CleanupEpollOptimizer(struct EpollOptimizer *optimizer);

// Funções JNI otimizadas
extern "C" {
    
JNIEXPORT jint JNICALL
Java_com_winlator_cmod_xconnector_XConnectorEpoll_createEpollFdOptimized(
    JNIEnv *env, jobject obj);

JNIEXPORT jboolean JNICALL
Java_com_winlator_cmod_xconnector_XConnectorEpoll_doEpollIndefinitelyOptimized(
    JNIEnv *env, jobject obj, jint epollFd, jint serverFd, jboolean addClientToEpoll);

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_xconnector_ClientSocket_readOptimized(
    JNIEnv *env, jobject obj, jint fd, jobject data, jint offset, jint length);

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_xconnector_ClientSocket_writeOptimized(
    JNIEnv *env, jobject obj, jint fd, jobject data, jint length);

JNIEXPORT jint JNICALL
Java_com_winlator_cmod_xconnector_ClientSocket_recvAncillaryMsgOptimized(
    JNIEnv *env, jobject obj, jint clientFd, jobject data, jint offset, jint length);

}

// Utilitários para detecção de hardware
int GetOptimalMaxEvents();
int GetOptimalMaxFDs();
int GetOptimalIOBufferSize();
int GetCPUCoreCount();
long GetAvailableMemoryMB();

// Profiling e métricas
struct EpollMetrics {
    atomic_ulong total_events_processed;
    atomic_ulong total_connections_accepted;
    atomic_ulong total_bytes_read;
    atomic_ulong total_bytes_written;
    atomic_ulong cache_hits;
    atomic_ulong buffer_pool_hits;
    atomic_ulong buffer_pool_misses;
};

void InitializeMetrics(struct EpollMetrics *metrics);
void LogMetrics(const struct EpollMetrics *metrics);
void ResetMetrics(struct EpollMetrics *metrics);

// Instância global do otimizador
extern struct EpollOptimizer g_epoll_optimizer;
extern struct EpollMetrics g_epoll_metrics;

#endif // XCONNECTOR_OPTIMIZED_H