#include <jni.h>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <optional>
#include <queue>
#include <sstream>
#include <string>

#include "bitboard.h"
#include "engine.h"
#include "misc.h"
#include "position.h"
#include "search.h"
#include "uci.h"
#include "ucioption.h"

using namespace Stockfish;

static Stockfish::Engine*        g_engine   = nullptr;
static std::queue<std::string>   g_queue;
static std::mutex                g_mutex;
static std::condition_variable   g_cv;
static std::atomic<bool>         g_shutdown{false};
static std::once_flag            g_initFlag;

static void pushLine(const std::string& line) {
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        g_queue.push(line);
    }
    g_cv.notify_one();
}

static void registerCallbacks() {
    g_engine->set_on_update_full([](const Engine::InfoFull& info) {
        std::stringstream ss;
        ss << "info";
        ss << " depth " << info.depth
           << " seldepth " << info.selDepth
           << " multipv " << info.multiPV
           << " score " << UCIEngine::format_score(info.score);

        if (!info.wdl.empty())
            ss << " wdl " << info.wdl;

        if (!info.bound.empty())
            ss << " " << info.bound;

        ss << " nodes " << info.nodes
           << " nps " << info.nps
           << " hashfull " << info.hashfull
           << " tbhits " << info.tbHits
           << " time " << info.timeMs
           << " pv " << info.pv;

        pushLine(ss.str());
    });

    g_engine->set_on_bestmove([](std::string_view bestmove, std::string_view ponder) {
        std::string line = "bestmove " + std::string(bestmove);
        if (!ponder.empty())
            line += " ponder " + std::string(ponder);
        pushLine(line);
    });

    g_engine->set_on_update_no_moves([](const Engine::InfoShort& info) {
        std::string line = "info depth " + std::to_string(info.depth)
                         + " score " + UCIEngine::format_score(info.score);
        pushLine(line);
    });

    g_engine->set_on_iter([](const Engine::InfoIter& info) {
        std::stringstream ss;
        ss << "info"
           << " depth " << info.depth
           << " currmove " << info.currmove
           << " currmovenumber " << info.currmovenumber;
        pushLine(ss.str());
    });

    g_engine->set_on_verify_networks([](std::string_view s) {
        pushLine("info string " + std::string(s));
    });

    g_engine->get_options().add_info_listener([](const std::optional<std::string>& str) {
        if (str.has_value())
            pushLine("info string " + *str);
    });
}

static void handleUci() {
    std::stringstream ss;
    ss << "id name Stockfish\nid author the Stockfish developers\n\n";
    ss << g_engine->get_options();
    pushLine(ss.str());
    pushLine("uciok");
}

static void handleIsReady() {
    g_engine->wait_for_search_finished();
    pushLine("readyok");
}

static void handlePosition(std::istringstream& is) {
    std::string token, fen;

    is >> token;

    if (token == "startpos") {
        fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
        is >> token; // consume "moves" if present
    } else if (token == "fen") {
        while (is >> token && token != "moves")
            fen += token + " ";
    } else {
        return;
    }

    std::vector<std::string> moves;
    while (is >> token)
        moves.push_back(token);

    g_engine->wait_for_search_finished();
    g_engine->set_position(fen, moves);
}

static void handleGo(std::istringstream& is) {
    g_engine->wait_for_search_finished();
    Search::LimitsType limits = UCIEngine::parse_limits(is);
    g_engine->go(limits);
}

static void handleSetOption(std::istringstream& is) {
    g_engine->wait_for_search_finished();
    g_engine->get_options().setoption(is);
}

extern "C" {

JNIEXPORT void JNICALL
Java_fr_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_startEngine(
        JNIEnv* env, jobject, jstring nnuePath) {
    std::call_once(g_initFlag, []() {
        Bitboards::init();
        Position::init();
    });

    g_shutdown = false;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        std::queue<std::string>().swap(g_queue);
    }

    std::optional<std::string> path = std::nullopt;
    if (nnuePath != nullptr) {
        const char* str = env->GetStringUTFChars(nnuePath, nullptr);
        // Engine expects argv[0]-style path; get_binary_directory extracts the directory part
        path = std::string(str) + "/stockfish";
        env->ReleaseStringUTFChars(nnuePath, str);
    }

    g_engine = new Engine(path);
    registerCallbacks();
}

JNIEXPORT void JNICALL
Java_fr_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_nativeSendCommand(
        JNIEnv* env, jobject, jstring cmd) {
    const char* str = env->GetStringUTFChars(cmd, nullptr);
    std::string command(str);
    env->ReleaseStringUTFChars(cmd, str);

    std::istringstream is(command);
    std::string token;
    is >> std::skipws >> token;

    if (token == "uci")
        handleUci();
    else if (token == "isready")
        handleIsReady();
    else if (token == "position")
        handlePosition(is);
    else if (token == "go")
        handleGo(is);
    else if (token == "stop")
        g_engine->stop();
    else if (token == "setoption")
        handleSetOption(is);
    else if (token == "ucinewgame")
        g_engine->search_clear();
    else if (token == "quit")
        g_engine->stop();
    else if (token == "ponderhit")
        g_engine->set_ponderhit(false);
}

JNIEXPORT jstring JNICALL
Java_fr_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_readOutput(
        JNIEnv* env, jobject) {
    std::unique_lock<std::mutex> lock(g_mutex);
    g_cv.wait(lock, [] { return !g_queue.empty() || g_shutdown; });

    if (g_queue.empty())
        return env->NewStringUTF("");

    std::string line = std::move(g_queue.front());
    g_queue.pop();
    return env->NewStringUTF(line.c_str());
}

JNIEXPORT void JNICALL
Java_fr_axl_1lvy_stockfish_1multiplatform_JniStockfishEngine_destroyEngine(
        JNIEnv*, jobject) {
    if (g_engine != nullptr) {
        g_engine->stop();
        g_engine->wait_for_search_finished();
        delete g_engine;
        g_engine = nullptr;
    }
    g_shutdown = true;
    g_cv.notify_all();
}

} // extern "C"
