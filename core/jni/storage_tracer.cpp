#define LOG_TAG "Dalf"

#include "storage_tracer.h"

#include <elf.h>
#include <errno.h>
#include <limits.h>
#include <string.h>
#include <unistd.h>
#include <unordered_map>

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

static std::unordered_map<int, bool> syscall_tracker;

#define AARCH64_REG_X0 0
#define AARCH64_REG_X1 1
#define AARCH64_REG_X8 8

#define EXTERNAL_STORAGE_PATH_SDCARD "/mnt/sdcard/"
#define EXTERNAL_STORAGE_PATH_STORAGE "/storage/self/primary/"
#define EXTERNAL_STORAGE_PATH_MEDIA "/data/media/0/"
#define EXTERNAL_STORAGE_PATH_EMULATED "/storage/emulated/0/"
#define EXTERNAL_STORAGE_PATH_MNT "/mnt/user/0/primary/"

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

    __attribute__((unused))
    static int ReadStringFromPid(int pid, char* src, char *dest) {

        unsigned int i = 0;
        int ret = 0;
        unsigned int MAX_LENGTH_PER_READ = sizeof (long);

        do {
            long val = 0;
            val = ptrace(PTRACE_PEEKTEXT, pid, src, NULL);

            if (val == -1) {
                ret = -1;
                break;
            }
            src += MAX_LENGTH_PER_READ;

            char *stringRead = (char *) &val;
            for (i = 0; i < MAX_LENGTH_PER_READ; ++i, ++stringRead, ++dest) {
                *dest = *stringRead;
                if (*dest == '\0') break;
            }
        } while (i == MAX_LENGTH_PER_READ);

        return ret;
    }

    __attribute__((unused))
    static inline bool starts_with(const char *str, const char *prefix) {
        unsigned int i = 0;
        unsigned int minimal = strlen(prefix);
        if (strlen(str) < minimal) {
            return false;
        }

        for (i = 0; i < minimal; i++) {
            if (str[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    __attribute__((unused))
    static inline bool is_on_external_storage(const char *path) {
        return (starts_with(path, EXTERNAL_STORAGE_PATH_SDCARD) ||
                starts_with(path, EXTERNAL_STORAGE_PATH_STORAGE) ||
                starts_with(path, EXTERNAL_STORAGE_PATH_MEDIA) ||
                starts_with(path, EXTERNAL_STORAGE_PATH_EMULATED) ||
                starts_with(path, EXTERNAL_STORAGE_PATH_MNT));
    }

    __attribute__((unused))
    static int tracer_interpose_on_open(int pid) {
#if defined (__aarch64__)

        /**
         * In aarch64 (ARMv8, 64-bit mode), the syscall convention is to place
         * the arguments in the low-numbered registers (x0, x1, ...) and place
         * the syscall number in x8. Note also that in aarch64, the openat()
         * syscall is used to open files; open() is deprecated.
         */
        int ret = 0;

        user_pt_regs regs;
        struct iovec io;
        io.iov_base = &regs;
        io.iov_len = sizeof(regs);
        ret = ptrace(PTRACE_GETREGSET, pid, NT_PRSTATUS, &io);

        uint64_t syscall_number = regs.regs[AARCH64_REG_X8];
        if (syscall_number == SYS_openat) {
            // in openat() syscall
            LOG_DEBUG_DALF("[aarch64] Storage tracer found openat() syscall for %d.", pid);

            char* src = (char*)regs.regs[AARCH64_REG_X1];
            ret = ReadStringFromPid(pid, src, gPathBuf);
            if (ret != 0) {
                LOG_ERROR_DALF("[aarch64] Storage tracer could not read filename string from %d.", pid);
                return -1;
            }

            LOG_DEBUG_DALF("[aarch64] Storage tracer discovered access to %s by %d.", gPathBuf, pid);
            if (gPathBuf[0] != '/') {
                LOG_ERROR_DALF("[aarch64] Storage tracer does not support relative access to %s by %d.", gPathBuf, pid);
                return 0;
            }

            if (!is_on_external_storage(gPathBuf)) {
                return 0;
            }

            LOG_DEBUG_DALF("[aarch64] Storage tracer will interpose on %s by %d.", gPathBuf, pid);
            // TODO(ali): Call the plugin to interpose on the string and pass in the new string.
        }

        return 0;
#endif
        return -1;
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

        syscall_tracker[gAppPid] = false;
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
                    syscall_tracker[gAppPid] = !syscall_tracker[gAppPid];
#if defined(__arm__)
                    LOG_ERROR_DALF("syscall interposition not supported on the arm architecture for %d", pausedPid);
#elif defined(__aarch64__)

                    if (syscall_tracker[gAppPid]) {
                        ret = tracer_interpose_on_open(pausedPid);
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
                    syscall_tracker[newPid] = false;
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

        munmap(gPathBuf, PATH_MAX);
        LOG_DEBUG_DALF("Storage tracer is all done for process %d!", gAppPid);
    }

} // extern "C"
