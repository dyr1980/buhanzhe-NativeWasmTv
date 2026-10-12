"""Generate a test-only deterministic CMG JNI recorder and replay bridge.

No generated wasm instructions are changed. Trace captures preserve native
call order and only store the addressed NAL range for in-place operations.
"""
import argparse
import json
import re
from pathlib import Path


def generate(source: str):
    methods = re.findall(r"JNIEXPORT\s+(\w+)\s+JNICALL\s+Java_com_bu_cc_tv_NativeCmgDecryptor_(\w+)\s*\((.*?)\)\s*\{", source, re.S)
    if len(methods) != 16:
        raise ValueError("Unexpected CMG JNI interface: " + str(len(methods)))
    before, after, metadata, java = [], [], [], []
    for opcode, (returns, name, params) in enumerate(methods, 1):
        args = [re.fullmatch(r"\s*(\w+)(\*?)\s+(\w+)\s*", a).groups() for a in params.split(",")]
        args = [(t + star, n) for t, star, n in args]
        call = ", ".join(n for _, n in args)
        original = "Java_com_bu_cc_tv_NativeCmgDecryptor_" + name
        impl = "cmg_trace_impl_" + name
        before.append(f"#define {original} {impl}")
        after.append(f"#undef {original}")
        clock_control = name in {"setClockForProbe", "clearClockForProbe", "replayOfficialTraceForProbe"}
        body = ["pthread_mutex_lock(&trial_trace_lock);", "uint64_t old_clock=g_forced_clock_ms, clock=cmg_now_ms64();",
                f"int frozen=trial_trace_file && old_clock==0 && {0 if clock_control else 1}; if(frozen)g_forced_clock_ms=clock;",
                f"trial_u32({opcode}); trial_u64(clock);"]
        for t, n in args[2:]:
            if t == "jstring":
                body.append(f"trial_string(env,{n});")
            elif t == "jbyteArray":
                if name == "decodeNalRangeInPlace":
                    body.append(f"trial_range(env,{n},offset,length);")
                else:
                    body.append(f"trial_bytes(env,{n},0,-1);")
            elif t == "jlong":
                body.append(f"trial_u64((uint64_t){n});")
            elif t in ("jint", "jboolean"):
                body.append(f"trial_u32((uint32_t){n});")
            else:
                raise ValueError(t)
        body.append("uint64_t started=trial_ns();")
        body.append((f"{returns} result=" if returns != "void" else "") + f"{impl}({call});")
        body.append("uint64_t elapsed=trial_ns()-started;")
        if returns == "jbyteArray":
            body.append("trial_bytes(env,result,0,-1);")
        elif returns == "jstring":
            body.append("trial_string(env,result);")
        elif returns != "void":
            body.append("trial_u32((uint32_t)result);")
        if name == "decodeNalRangeInPlace":
            body.append("trial_bytes(env,input,offset,length);")
        body += ["trial_u64(elapsed);", "if(frozen)g_forced_clock_ms=old_clock;", "pthread_mutex_unlock(&trial_trace_lock);"]
        if returns != "void":
            body.append("return result;")
        after.append(f"JNIEXPORT {returns} JNICALL {original}({params}) {{\n" + "\n".join(body) + "\n}")
        replay = "Java_xiao_bu_cryptotrial_CmgReplayBridge_" + name
        after.append(f"JNIEXPORT {returns} JNICALL {replay}({params}) {{ " + ("return " if returns != "void" else "") + f"{impl}({call}); }}")
        metadata.append(dict(opcode=opcode, name=name, returns=returns, args=[t for t, _ in args[2:]]))
        types = {"jstring": "String", "jbyteArray": "byte[]", "jlong": "long", "jint": "int", "jboolean": "boolean", "void": "void"}
        java.append("static native " + types[returns] + " " + name + "(" + ",".join(types[t] + " " + n for t, n in args[2:]) + ");")
    helpers = r'''
#include <time.h>
static pthread_mutex_t trial_trace_lock=PTHREAD_MUTEX_INITIALIZER;
static FILE* trial_trace_file;
static uint64_t trial_ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t); return (uint64_t)t.tv_sec*1000000000ULL+t.tv_nsec; }
static void trial_u32(uint32_t n) { if(trial_trace_file)fwrite(&n,4,1,trial_trace_file); }
static void trial_u64(uint64_t n) { if(trial_trace_file)fwrite(&n,8,1,trial_trace_file); }
static void trial_string(JNIEnv* env,jstring s) {
 if(!trial_trace_file)return;
 if(!s){trial_u32(UINT32_MAX);return;}
 const char* b=(*env)->GetStringUTFChars(env,s,NULL);if(!b){trial_u32(UINT32_MAX);return;}
 uint32_t len=(uint32_t)strlen(b);trial_u32(len);fwrite(b,1,len,trial_trace_file);(*env)->ReleaseStringUTFChars(env,s,b);
}
static void trial_bytes(JNIEnv* env,jbyteArray a,jint off,jint len) {
 if(!trial_trace_file)return;
 if(!a){trial_u32(UINT32_MAX);return;}
 jint size=(*env)->GetArrayLength(env,a);if(len<0)len=size;
 if(off<0||len<0||off>size||len>size-off){trial_u32(UINT32_MAX);return;}
 trial_u32(len);jbyte scratch[8192];
 while(len>0){jint n=len>8192?8192:len;(*env)->GetByteArrayRegion(env,a,off,n,scratch);fwrite(scratch,1,n,trial_trace_file);off+=n;len-=n;}
}
static void trial_range(JNIEnv* env,jbyteArray a,jint off,jint len) {
 if(!trial_trace_file)return;
 trial_u32(a ? (uint32_t)(*env)->GetArrayLength(env,a) : UINT32_MAX);trial_u32(off);trial_bytes(env,a,off,len);
}
JNIEXPORT jboolean JNICALL Java_xiao_bu_tv_CmgTraceBridge_enable(JNIEnv* env,jclass c,jstring path) {
 pthread_mutex_lock(&trial_trace_lock);if(trial_trace_file)fclose(trial_trace_file);
 const char* s=(*env)->GetStringUTFChars(env,path,NULL);trial_trace_file=s ? fopen(s,"wb") : NULL;
 if(s)(*env)->ReleaseStringUTFChars(env,path,s);trial_u32(0x31474d43);pthread_mutex_unlock(&trial_trace_lock);return trial_trace_file ? JNI_TRUE : JNI_FALSE;
}
JNIEXPORT void JNICALL Java_xiao_bu_tv_CmgTraceBridge_mark(JNIEnv* env,jclass c,jstring channel) {
 pthread_mutex_lock(&trial_trace_lock);trial_u32(0);trial_u64(0);trial_string(env,channel);trial_u64(0);pthread_mutex_unlock(&trial_trace_lock);
}
JNIEXPORT void JNICALL Java_xiao_bu_tv_CmgTraceBridge_stop(JNIEnv* env,jclass c) {
 pthread_mutex_lock(&trial_trace_lock);if(trial_trace_file){trial_u32(UINT32_MAX);fclose(trial_trace_file);trial_trace_file=NULL;}pthread_mutex_unlock(&trial_trace_lock);
}
'''
    result = "\n".join(before) + "\n" + source + "\n" + helpers + "\n" + "\n".join(after)
    bridge = "package xiao.bu.cryptotrial;\nfinal class CmgReplayBridge {\n" + "\n".join(java) + "\n}\n"
    return result, metadata, bridge


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    source, metadata, bridge = generate(args.source.read_text(encoding="utf-8"))
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "cmg_decryptor.c").write_text(source, encoding="utf-8")
    (args.output / "methods.json").write_text(json.dumps(metadata, indent=2))
    (args.output / "CmgReplayBridge.java").write_text(bridge, encoding="utf-8")
