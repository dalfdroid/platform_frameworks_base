/**
 * This is the storage tracer. It uses ptrace to identify when apps try to use
 * syscalls used to open files in the external storage partition. For each such
 * file, it calls into the plugin that interposes on the app's accesses to the
 * external storage.
 *
 * A storage tracer is created during zygote fork time only if the app has a
 * storage interposing plugin. If the user decides to apply a storage plugin on
 * the app after it has been launched, the app needs to be terminated and
 * restarted.
 *
 * At the moment, the storage tracer works only in aarch64 mode. This is simply
 * a matter of implementation.
 *
 * The code here has been inspired by several ptrace examples on the
 * internet. ReadStringFromPid() and WriteStringToPid(), and the technique of
 * using space in the unused portion of the thread's stack, were inspired by
 * https://www.alfonsobeato.net/c/filter-and-modify-system-calls-with-seccomp-and-ptrace/.
 */
#define LOG_TAG "Dalf"

#include "storage_tracer.h"

#include <elf.h>
#include <errno.h>
#include <limits.h>
#include <string.h>
#include <unistd.h>
#include <unordered_map>

#include <android_runtime/AndroidRuntime.h>

#include <asm/ptrace.h>
#include <sys/mman.h>
#include <sys/ptrace.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <utils/Log.h>

#ifdef DEBUG_DALF
#define LOG_DEBUG_DALF(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#else
#define LOG_DEBUG_DALF(...) ((void)0)
#endif
#define LOG_ERROR_DALF(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void* gSharedMemory = NULL;
static sem_t *gAppSem = NULL;
static sem_t *gTracerSem = NULL;
static pid_t gAppPid = -1;
static char* gPathBuf = NULL;

static jclass gZygoteClass;
static jmethodID gCallExternalStoragePlugin;

/**
 * This is used to differentiate between syscall-enter-stop and
 * syscall-exit-stop for each syscall in each tracked thread.
 */
static std::unordered_map<int, bool> syscall_tracker;

#ifdef __aarch64__
#include "storage_tracer_aarch64.h"
#endif

extern "C" {

    int tracer_prefork_setup() {
        LOG_DEBUG_DALF("%s: invoked!", __func__);


        LOG_DEBUG_DALF("%s: Setting up shared memory for semaphores", __func__);
        gSharedMemory = mmap(NULL, PAGE_SIZE,
                             PROT_READ | PROT_WRITE,
                             MAP_SHARED | MAP_ANONYMOUS, -1, 0);

        if (gSharedMemory == MAP_FAILED) {
            LOG_ERROR_DALF("%s: unable to allocate shared memory: %s.", __func__, strerror(errno));
            return -1;
        }

        LOG_DEBUG_DALF("%s: Setting up semaphores", __func__);

        gAppSem = (sem_t*) gSharedMemory;
        gTracerSem = (gAppSem + 1);

        int ret = 0;

        ret = sem_init(gAppSem, /** pshared */ 1, /** value */ 0);
        if (ret == -1) {
            LOG_ERROR_DALF("%s: could not initialize app semaphore: %s.", __func__, strerror(errno));
            return -1;
        }

        ret = sem_init(gTracerSem, /** pshared */ 1, /** value */ 0);
        if (ret == -1) {
            LOG_ERROR_DALF("%s: could not initialize tracer semaphore: %s.", __func__, strerror(errno));
            return -1;
        }

        LOG_DEBUG_DALF("%s: All done", __func__);
        return 0;
    }

    sem_t *tracer_get_app_sem() {
        LOG_DEBUG_DALF("%s: invoked; returning %p", __func__, gAppSem);
        return gAppSem;
    }

    sem_t *tracer_get_tracer_sem() {
        LOG_DEBUG_DALF("%s: invoked; returning %p", __func__, gTracerSem);
        return gTracerSem;
    }

    void tracer_postfork_zygote_cleanup() {
        LOG_DEBUG_DALF("%s: invoked!", __func__);

        if (gSharedMemory != NULL) {
            munmap(gSharedMemory, PAGE_SIZE);
        }
    }

    void tracer_postfork_app_cleanup() {
        LOG_DEBUG_DALF("%s: invoked!", __func__);

        if (gSharedMemory != NULL) {
            munmap(gSharedMemory, PAGE_SIZE);
        }
    }

    int tracer_postfork_setup(pid_t appPid, jclass zygoteClass, jmethodID callExternalStoragePlugin) {
        LOG_DEBUG_DALF("%s: setting up for %d!", __func__, appPid);

        if (gSharedMemory == NULL) {
            return -1;
        }

        int setup_success = 0;

        gAppPid = appPid;
        gZygoteClass = zygoteClass;
        gCallExternalStoragePlugin = callExternalStoragePlugin;

        sem_wait(gAppSem);

        int status = 0;
        int ret = 0;

        ret = ptrace(PTRACE_ATTACH, gAppPid, 0, 0);
        if (ret < 0) {
            LOG_ERROR_DALF("Storage tracer is unable to attach to app %d; failed with %s.",
                           gAppPid, strerror(errno));
            setup_success = -1;
            goto bail;
        }

        ret = waitpid(gAppPid, &status, 0);
        if (ret < 0) {
            LOG_ERROR_DALF("Storage tracer is unable to wait for %d after PTRACE_ATTACH; failed with %s.",
                           gAppPid, strerror(errno));
            // TODO(?): Detach ptrace?
            setup_success = -1;
            goto bail;
        }

        ret = ptrace(PTRACE_SETOPTIONS, gAppPid, 0,
                     PTRACE_O_TRACEEXIT | PTRACE_O_TRACECLONE | PTRACE_O_TRACESYSGOOD);
        if (ret < 0) {
            LOG_ERROR_DALF("Storage tracer is unable to set options for %d; failed with %s.",
                           gAppPid, strerror(errno));
            // TODO(?): Detach ptrace?
            setup_success = -1;
            goto bail;
        }

        gPathBuf = (char*) mmap(NULL, PATH_MAX,
                                PROT_READ | PROT_WRITE,
                                MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        if (gPathBuf == MAP_FAILED) {
            // TODO(?): Detach ptrace?
            LOG_ERROR_DALF("%s: unable to allocate path buffer memory: %s.", __func__, strerror(errno));
            setup_success = -1;
            goto bail;
        }
        memset(gPathBuf, 0, PATH_MAX);

        sem_post(gTracerSem);

bail:
        munmap(gSharedMemory, PAGE_SIZE);
        return setup_success;
    }

    void tracer_run_loop() {
        LOG_DEBUG_DALF("Storage tracer starting loop for process %d!", gAppPid);

        if (gAppPid == -1) {
            return;
        }

        int pausedPid = 0;
        int status = 0;
        int ret = 0;
        int numThreads = 1;

        ret = ptrace(PTRACE_SYSCALL, gAppPid, 0, 0);

        while (numThreads > 0) {
            ret = waitpid(-1, &status, __WALL);
            if (ret < 0) {
                LOG_ERROR_DALF("Tracer could not wait for child; failed with %s.", strerror(errno));
                break;
            }
            pausedPid = ret;

            if (WIFSTOPPED(status)) {
                int statusType = (status >> 8);

                if (WSTOPSIG(status) & 0x80) {

                    if (syscall_tracker.find(pausedPid) == syscall_tracker.end()) {
                        syscall_tracker[pausedPid] = false;
                    }

                    syscall_tracker[pausedPid] = !syscall_tracker[pausedPid];
#if defined(__arm__)
                    LOG_ERROR_DALF("syscall interposition not supported on the arm architecture for %d", pausedPid);
#elif defined(__aarch64__)

                    if (syscall_tracker[pausedPid]) {
                        ret = aarch64_tracer_interpose_on_open(pausedPid);
                    } else {
                        ret = 0;
                    }

                    if (ret < 0) {
                        LOG_ERROR_DALF("Storage tracer had an unexpected error interposing on open() for %d", pausedPid);
                        break;
                    }
#else
                    LOG_ERROR_DALF("syscall interposition not supported on unknown architecture for %d", pausedPid);
#endif
                } else if (statusType == (SIGTRAP | PTRACE_EVENT_CLONE << 8)) {
                    int newPid = -1;

                    ret = ptrace(PTRACE_GETEVENTMSG, pausedPid, NULL, (long) &newPid);
                    if (ret < 0) {
                        LOG_ERROR_DALF("Storage tracer could not get pid of newly started thread while tracing %d; failed with %s.",
                                       pausedPid, strerror(errno));
                        break;
                    }

                    LOG_DEBUG_DALF("Storage tracer detected new thread %d for process %d.", newPid, gAppPid);
                    numThreads++;
                } else if (statusType == (SIGTRAP | PTRACE_EVENT_EXIT << 8)) {
                    numThreads--;
                }

                ret = ptrace(PTRACE_SYSCALL, pausedPid, 0, 0);

            } else if (WIFEXITED(status)) {
                LOG_DEBUG_DALF("Storage tracer received WIFEXITED from %d", pausedPid);
                continue;
            } else if (WIFSIGNALED(status)) {
                // The process has been terminated by a signal; that's all for
                // this tracer then.
                LOG_DEBUG_DALF("Storage tracer received WIFSIGNALED from %d", pausedPid);
                break;
            } else {
                LOG_ERROR_DALF(
                    "Received unexpected status from %d;"
                    "Status is %08x"
                    ", WIFSTOPPED(status) is %d"
                    ", WIFEXITED: %d"
                    ", WEXITSTATUS: %d"
                    ", WIFSIGNALED: %d"
                    ", WTERMSIG: %d"
                    ", WCOREDUMP: %d"
                    ", WIFSTOPPED: %d"
                    ", WSTOPSIG: %d"
                    ", WIFCONTINUED: %d"
                    , pausedPid,
                    status,
                    WIFSTOPPED(status),
                    WIFEXITED(status),
                    WEXITSTATUS(status),
                    WIFSIGNALED(status),
                    WTERMSIG(status),
                    WCOREDUMP(status),
                    WIFSTOPPED(status),
                    WSTOPSIG(status),
                    WIFCONTINUED(status));
                break;
            }
        }

        munmap(gPathBuf, PATH_MAX);
        LOG_DEBUG_DALF("Storage tracer is all done for process %d!", gAppPid);
    }

} // extern "C"
