"""Use bounded JNI transfers for CMG's existing in-place NAL decoder.

Run against a TvWasm/cjs checkout. No algorithm, state order, or wasm bounds
checks are changed. The host APK does not bundle this plugin library.
"""
import argparse
from pathlib import Path


def patch(source: str) -> str:
    signature = "Java_com_bu_cc_tv_NativeCmgDecryptor_decodeNalRangeInPlace("
    start = source.index(signature)
    end = source.index("JNIEXPORT jbyteArray JNICALL", start)
    block = source[start:end]
    if "GetByteArrayRegion(env, input, offset, length" in block:
        return source
    replacements = {
        "  jbyte* input_bytes;\n": "",
        "  input_bytes = (*env)->GetByteArrayElements(env, input, NULL);\n  if (input_bytes == NULL) {\n    return -1;\n  }\n": "",
        "    (*env)->ReleaseByteArrayElements(env, input, input_bytes, JNI_ABORT);\n": "",
        "  memcpy(data_pointer, input_bytes + offset, (size_t) length);\n":
        "  (*env)->GetByteArrayRegion(env, input, offset, length, (jbyte*) data_pointer);\n"
        "  if ((*env)->ExceptionCheck(env)) {\n"
        "    pthread_mutex_unlock(&g_lock);\n    return -1;\n  }\n",
        "      memcpy(input_bytes + offset, data_pointer, (size_t) output_length);\n      result = (int) output_length;":
        "      (*env)->SetByteArrayRegion(env, input, offset, (jsize) output_length,\n"
        "          (const jbyte*) data_pointer);\n"
        "      result = (*env)->ExceptionCheck(env) ? -1 : (int) output_length;",
        "  (*env)->ReleaseByteArrayElements(env, input, input_bytes,\n      result >= 0 ? 0 : JNI_ABORT);\n": "",
    }
    for old, new in replacements.items():
        if old not in block:
            raise ValueError("CMG native source changed; refusing partial patch: " + old)
        block = block.replace(old, new)
    return source[:start] + block + source[end:]


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path)
    args = parser.parse_args()
    path = args.checkout / "sites/yangshipin.cn/native/cmg_decryptor.c"
    original = path.read_text(encoding="utf-8")
    updated = patch(original)
    if updated != original:
        path.write_text(updated, encoding="utf-8", newline="\n")
    print("CMG bounded JNI copy patch ready:", path)
