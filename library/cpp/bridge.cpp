#include <jni.h>
#include <string>
#include <thread>
#include <unistd.h>

// Stockfish's main() is renamed to stockfish_main via -Dmain=stockfish_main compile flag
extern int stockfish_main(int argc, char* argv[]);

// inputPipe[0] = Stockfish reads (stdin), inputPipe[1] = JNI writes
static int inputPipe[2];
// outputPipe[0] = JNI reads, outputPipe[1] = Stockfish writes (stdout)
static int outputPipe[2];

static void runStockfish() {
    dup2(inputPipe[0], STDIN_FILENO);
    close(inputPipe[0]);

    dup2(outputPipe[1], STDOUT_FILENO);
    close(outputPipe[1]);

    char progName[] = "stockfish";
    char* argv[] = {progName};
    stockfish_main(1, argv);
}

extern "C" {

JNIEXPORT void JNICALL
Java_io_github_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_startEngine(
        JNIEnv*, jobject) {
    pipe(inputPipe);
    pipe(outputPipe);
    std::thread t(runStockfish);
    t.detach();
}

JNIEXPORT void JNICALL
Java_io_github_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_nativeSendCommand(
        JNIEnv* env, jobject, jstring cmd) {
    const char* str = env->GetStringUTFChars(cmd, nullptr);
    std::string command(str);
    command += "\n";
    write(inputPipe[1], command.c_str(), command.size());
    env->ReleaseStringUTFChars(cmd, str);
}

JNIEXPORT jstring JNICALL
Java_io_github_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_readOutput(
        JNIEnv* env, jobject) {
    std::string line;
    char c;
    ssize_t n;
    while ((n = read(outputPipe[0], &c, 1)) > 0) {
        if (c == '\n') break;
        if (c != '\r') line += c;
    }
    return env->NewStringUTF(line.c_str());
}

} // extern "C"
