#define AARCH64_REG_X0 0
#define AARCH64_REG_X1 1
#define AARCH64_REG_X2 2
#define AARCH64_REG_X8 8

#define RED_ZONE 128

extern "C" {

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

  static int WriteStringToPid(int pid, const char* str, char *dest) {

    unsigned int i = 0;
    char c = '\0';

    unsigned int MAX_LENGTH_PER_WRITE = sizeof (long);
    char val[MAX_LENGTH_PER_WRITE];

    do {
      for (i = 0; i < sizeof(long); ++i, ++str) {
        c = *str;
        val[i] = c;
        if (c == '\0') break;
      }

      int ret = ptrace(PTRACE_POKETEXT, pid, dest, *(long *)val);
      if (ret < 0) {
        return -1;
      }

      dest += MAX_LENGTH_PER_WRITE;
    } while (c);

    return 0;
  }

  int aarch64_tracer_interpose_on_open(int pid) {
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
      JNIEnv* env = android::AndroidRuntime::getJNIEnv();
      jstring javaPath = env->NewStringUTF(gPathBuf);
      if (javaPath == NULL) {
        LOG_ERROR_DALF("[aarch64] Could not construct string path from %s for %d.", gPathBuf, pid);
        return -1;
      }

      uint64_t openMode = regs.regs[AARCH64_REG_X2];
      jint javaOpenMode = (int) openMode;

      jstring javaPathFromPlugin = (jstring) env->CallStaticObjectMethod(
                                                                         gZygoteClass, gCallExternalStoragePlugin, javaPath, javaOpenMode);

      if (javaPathFromPlugin == NULL) {
        LOG_DEBUG_DALF("[aarch64] Storage tracer will not perturb access to %s for %d, javaPath is %p", gPathBuf, pid, javaPathFromPlugin);
        return 0;
      }

      const char *pathFromPlugin = env->GetStringUTFChars(javaPathFromPlugin, NULL);
      unsigned int newPathLen = strlen(pathFromPlugin) + 1; // 1 extra for terminating character.
      if (newPathLen > PATH_MAX) {
        LOG_ERROR_DALF("[aarch64] Path (%s) from plugin exceeds maximum length;"
                       " Storage tracer cannot use it for %d", pathFromPlugin, pid);
        return -1;
      }

      uint64_t sp = regs.sp;
      uint64_t newStringLocation = sp - newPathLen - RED_ZONE;
      ret = WriteStringToPid(pid, pathFromPlugin, (char*) newStringLocation);
      if (ret < 0) {
        LOG_ERROR_DALF("[aarch64] Could not copy new path %s to %d; failed with %s",
                       pathFromPlugin, pid, strerror(errno));
        return -1;
      }

      regs.regs[AARCH64_REG_X1] = newStringLocation;
      ret = ptrace(PTRACE_SETREGSET, pid, NT_PRSTATUS, &io);
      if (ret < 0) {
        LOG_ERROR_DALF("[aarch64] Could not update arguments to openat() for %d", pid);
        return -1;
      }

      env->ReleaseStringUTFChars(javaPathFromPlugin, pathFromPlugin);
      env->DeleteLocalRef(javaPathFromPlugin);
      env->DeleteLocalRef(javaPath);
    }

    return 0;
  }

} // extern "C"
