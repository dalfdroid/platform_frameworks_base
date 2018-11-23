#define LOG_TAG "Dalf"

#include "storage_tracer.h"

#include <errno.h>
#include <string.h>
#include <unistd.h>

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

    int tracer_postfork_setup(pid_t appPid) {
        LOG_DEBUG_DALF("%s: setting up for %d!", __func__, appPid);

        if (gSharedMemory == NULL) {
            return -1;
        }

        int setup_success = 0;

        gAppPid = appPid;
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
                    LOG_DEBUG_DALF("Storage tracer detected a syscall for %d", pausedPid);
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
                    continue;
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

        LOG_DEBUG_DALF("Storage tracer is all done for process %d!", gAppPid);
    }

} // extern "C"
