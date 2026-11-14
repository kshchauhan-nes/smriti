#include <jni.h>
#include "llama.h"

#include <algorithm>
#include <memory>
#include <string>
#include <vector>

// Access is serialized by HybridAi.
// Every request owns fresh state, including on cancellation.
struct Runtime {
    llama_model *model = nullptr;
    llama_context *context = nullptr;
    llama_sampler *sampler = nullptr;

    std::vector <llama_token> prompt;

    int position = 0;
    int generated = 0;

    void reset() {
        if (sampler) {
            llama_sampler_free(sampler);
        }

        if (context) {
            llama_free(context);
        }

        sampler = nullptr;
        context = nullptr;

        prompt.clear();

        position = 0;
        generated = 0;
    }

    ~Runtime() {
        reset();

        if (model) {
            llama_model_free(model);
        }
    }
};

static void fail(
        JNIEnv *env,
        const char *message
) {
    env->ThrowNew(
            env->FindClass("java/lang/IllegalStateException"),
            message
    );
}

static std::string bytes(
        JNIEnv *env,
        jbyteArray input
) {
    std::string result(
            env->GetArrayLength(input),
            '\0'
    );

    env->GetByteArrayRegion(
            input,
            0,
            static_cast<jsize>(result.size()),
            reinterpret_cast<jbyte *>(result.data())
    );

    return result;
}

extern "C"
JNIEXPORT jlong

JNICALL
Java_com_example_myapplication_chat_LfmRuntime_load(
        JNIEnv *env,
        jobject,
        jbyteArray path
) {
    llama_backend_init();

    auto runtime =
            std::make_unique<Runtime>();

    auto params =
            llama_model_default_params();

    params.n_gpu_layers = 0;

    const std::string modelPath =
            bytes(env, path);

    runtime->model =
            llama_model_load_from_file(
                    modelPath.c_str(),
                    params
            );

    if (!runtime->model) {
        fail(
                env,
                "Could not load the offline model. Please retry setup."
        );

        return 0;
    }

    return reinterpret_cast<jlong>(
            runtime.release()
    );
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_myapplication_chat_LfmRuntime_begin(
        JNIEnv
*env,
jobject,
jlong handle,
        jbyteArray
promptBytes,
jint threads,
        jint
reserve
) {
auto *runtime =
        reinterpret_cast<Runtime *>(handle);

runtime->

reset();

const std::string text =
        bytes(env, promptBytes);

const llama_vocab *vocab =
        llama_model_get_vocab(
                runtime->model
        );

int count =
        -llama_tokenize(
                vocab,
                text.data(),
                text.size(),
                nullptr,
                0,
                false,
                true
        );

if (
count <= 0 ||
count + reserve > 4096
) {
fail(
        env,
"Too much context. Please ask about a specific person or task."
);

return;
}

runtime->prompt.
resize(count);

const int tokenized =
        llama_tokenize(
                vocab,
                text.data(),
                text.size(),
                runtime->prompt.data(),
                count,
                false,
                true
        );

if (tokenized < 0) {
fail(
        env,
"Could not prepare your message."
);

return;
}

auto params =
        llama_context_default_params();

params.
n_ctx = 4096;
params.
n_batch = 128;
params.
n_ubatch = 128;

params.
n_threads = threads;
params.
n_threads_batch = threads;

runtime->
context =
llama_init_from_model(
        runtime->model,
        params
);

if (!runtime->context) {
runtime->

reset();

fail(
        env,
"Not enough memory for offline AI. Close other apps and retry."
);

return;
}

runtime->
sampler =
llama_sampler_chain_init(
        llama_sampler_chain_default_params()
);

llama_sampler_chain_add(
        runtime
->sampler,
llama_sampler_init_top_k(50)
);

llama_sampler_chain_add(
        runtime
->sampler,
llama_sampler_init_temp(0.1f)
);

llama_sampler_chain_add(
        runtime
->sampler,
llama_sampler_init_dist(
        LLAMA_DEFAULT_SEED
)
);
}

extern "C"
JNIEXPORT jboolean
JNICALL
        Java_com_example_myapplication_chat_LfmRuntime_prefill(
        JNIEnv * env,
        jobject,
        jlong
handle
) {
auto *runtime =
        reinterpret_cast<Runtime *>(handle);

const int remaining =
        static_cast<int>(
                runtime->prompt.size()
        ) - runtime->position;

if (remaining <= 0) {
return
JNI_TRUE;
}

const int count =
        std::min(
                128,
                remaining
        );

auto batch =
        llama_batch_get_one(
                runtime->prompt.data() +
                runtime->position,
                count
        );

if (
llama_decode(
        runtime
->context,
batch
) != 0
) {
fail(
        env,
"Offline AI could not read the message."
);

return
JNI_FALSE;
}

runtime->position +=
count;

return runtime->position ==
static_cast
<int>(
        runtime
->prompt.

size()

);
}

extern "C"
JNIEXPORT jbyteArray
JNICALL
        Java_com_example_myapplication_chat_LfmRuntime_next(
        JNIEnv * env,
        jobject,
        jlong
handle
) {
auto *runtime =
        reinterpret_cast<Runtime *>(handle);

const llama_vocab *vocab =
        llama_model_get_vocab(
                runtime->model
        );

llama_token token =
        llama_sampler_sample(
                runtime->sampler,
                runtime->context,
                -1
        );

if (
llama_vocab_is_eog(
        vocab,
        token
)
) {
return
nullptr;
}

std::vector<char> piece(256);

int count =
        llama_token_to_piece(
                vocab,
                token,
                piece.data(),
                piece.size(),
                0,
                false
        );

if (count < 0) {
piece.resize(
static_cast<size_t>(-count)
);

count =
llama_token_to_piece(
        vocab,
        token,
        piece.data(),
        piece.size(),
        0,
        false
);
}

if (count < 0) {
fail(
        env,
"Offline AI returned an invalid token."
);

return
nullptr;
}

auto batch =
        llama_batch_get_one(
                &token,
                1
        );

if (
llama_decode(
        runtime
->context,
batch
) != 0
) {
fail(
        env,
"Offline AI could not complete the answer."
);

return
nullptr;
}

runtime->generated++;

auto result =
        env->NewByteArray(count);

if (count > 0) {
env->
SetByteArrayRegion(
        result,
0,
count,
reinterpret_cast<jbyte *>(
piece.

data()

)
);
}

return
result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_example_myapplication_chat_LfmRuntime_reset(
        JNIEnv
*,
jobject,
jlong handle
) {
auto *runtime =
        reinterpret_cast<Runtime *>(handle);

runtime->

reset();

}