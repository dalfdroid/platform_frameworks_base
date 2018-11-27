#ifndef FRAMEWORKS_BASE_CORE_JNI_STORAGE_TRACER_H_
#define FRAMEWORKS_BASE_CORE_JNI_STORAGE_TRACER_H_

#include <semaphore.h>

#include <sys/types.h>
#include <nativehelper/JNIHelp.h>

#define EXTERNAL_STORAGE_PATH_SDCARD "/mnt/sdcard/"
#define EXTERNAL_STORAGE_PATH_STORAGE "/storage/self/primary/"
#define EXTERNAL_STORAGE_PATH_MEDIA "/data/media/0/"
#define EXTERNAL_STORAGE_PATH_EMULATED "/storage/emulated/0/"
#define EXTERNAL_STORAGE_PATH_MNT "/mnt/user/0/primary/"

extern "C" {

  /**
   * Sets up the global associated with the tracer. Returns 0 if the setup was
   * successful and -1 otherwise.
   */
  int tracer_prefork_setup();

  /**
   * Returns the semaphore the app process needs to post on after its
   * initialized.
   */
  sem_t *tracer_get_app_sem();

  /**
   * Returns the tracer's semaphore. The app process needs to wait on this
   * semaphore after posting on its own semaphore, to wait for the tracer to be
   * initialized.
   */
  sem_t *tracer_get_tracer_sem();

  /**
   * The main zygote process should call this after forking the app and the
   * tracer, to clean up all the global state created by the tracer before the
   * fork.
   */
  void tracer_postfork_zygote_cleanup();

  /**
   * The app process should call this after waiting on the tracer's semaphore,
   * to clean up all the global state created by the tracer before the fork.
   */
  void tracer_postfork_app_cleanup();

  /**
   * This should be called in the tracer process after it has forked
   * successfully, to finish setting up the tracer.
   */
  int tracer_postfork_setup(pid_t appPid, jclass zygoteClass, jmethodID callExternalStoragePlugin);

  /**
   * Runs the main tracer loop to interpose on each syscall.
   */
  void tracer_run_loop();

  __attribute__((unused))
  inline bool starts_with(const char *str, const char *prefix) {
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
  inline bool is_on_external_storage(const char *path) {
    return (starts_with(path, EXTERNAL_STORAGE_PATH_SDCARD) ||
            starts_with(path, EXTERNAL_STORAGE_PATH_STORAGE) ||
            starts_with(path, EXTERNAL_STORAGE_PATH_MEDIA) ||
            starts_with(path, EXTERNAL_STORAGE_PATH_EMULATED) ||
            starts_with(path, EXTERNAL_STORAGE_PATH_MNT));
  }
} // extern "C"

#endif // FRAMEWORKS_BASE_CORE_JNI_STORAGE_TRACER_H_
