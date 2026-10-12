"""Build the multi-ABI native benchmark and network regression report."""
import csv
import html
import json
import pathlib
import statistics
import sys

p=pathlib.Path(sys.argv[1])
rows=[];digests=set();segment_tests=0;native_calls=0;network=[];playback=[]
for abi,flavor in [('armeabi-v7a','arm32'),('arm64-v8a','arm64'),('x86','x86')]:
    batches={}
    for variant in ['cctv_base','cctv_pool']:
        data=json.loads((p/abi/(variant+'.json')).read_text(encoding='utf-8'))
        for result in data:
            segment_tests+=len(result['samples'])
            digests.update(sample['sha256'] for sample in result['samples'])
        for threads in [1,2]:
            batches[variant,threads]=statistics.median(r['batchNs']/1e6 for r in data if r['threads']==threads)
    totals={}
    for variant in ['cmg_base','cmg_combined']:
        data=json.loads((p/abi/(variant+'.json')).read_text(encoding='utf-8'))
        native_calls+=sum(r['verifiedOutputs'] for r in data)
        totals[variant]=statistics.median(sum(sum(r['costNs'].values()) for r in data if r['repeat']==repeat)/1e6 for repeat in range(3))
    rows.append(dict(abi=abi,cctvBaseBatchMs=round(batches['cctv_base',1],2),
        cctvPoolBatchMs=round(batches['cctv_pool',1],2),cctvPoolTwoWorkersMs=round(batches['cctv_pool',2],2),
        cctvImprovementPercent=round((1-batches['cctv_pool',1]/batches['cctv_base',1])*100,2),
        cmgBaseMs=round(totals['cmg_base'],2),cmgCombinedMs=round(totals['cmg_combined'],2),
        cmgImprovementPercent=round((1-totals['cmg_combined']/totals['cmg_base'])*100,2)))
    regression=json.loads((p/('network-'+flavor+'.json')).read_text(encoding='utf-8'))
    assert regression['abi']==abi and regression['peakFd']>=1100
    assert regression['fdAfterCycles']<=regression['fdAfterWarmup']+4
    assert 'PASS' in (p/('network-'+flavor+'.log')).read_text(encoding='utf-8')
    network.append(regression)
    file=p/('playback-'+flavor+'.json')
    if file.exists():
        for result in json.loads(file.read_text(encoding='utf-8')):
            playback.append(dict(abi=abi,**result))
assert len(digests)==1,'Cross-ABI TS output mismatch'
report=dict(device='Android 9 / API 28 MEmu',nativeBridge='libnb.so',
    scope='ARM is translated, x86 is native. CCTV = same captured TS twice; CMG = ordered real-stream trace across 3 channels. 3 repeats, medians. No network or player in native measurements.',
    rows=rows,cctvVerifiedDecryptions=segment_tests,cctvSha256=list(digests)[0],cmgVerifiedNativeCalls=native_calls,
    networkRegression=network,livePlayback=playback,
    crash='Stack enters old system Conscrypt FD_SET bounds abort. High or invalid descriptor is consistent with this stack; original abort message and APK mapping were not available. API14-23 use Java-stream TLS; proxy shutdown cancels in-flight requests. No original-device crash reproduction.')
(p/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
with (p/'report.csv').open('w',encoding='utf-8-sig',newline='') as output:
    writer=csv.DictWriter(output,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
def cells(values):return '<tr>'+''.join('<td>'+html.escape(str(x))+'</td>' for x in values)+'</tr>'
table=''.join(cells([r[k] for k in ['abi','cctvBaseBatchMs','cctvPoolBatchMs','cctvPoolTwoWorkersMs','cctvImprovementPercent','cmgBaseMs','cmgCombinedMs','cmgImprovementPercent']]) for r in rows)
live=''.join(cells([r['abi'],r['channel'],r.get('status'),r.get('firstFrameMs','—'),r.get('watchClockAdvanceMs','—')]) for r in playback)
pressure=''.join(cells([r['abi'],r['proxyCloseCycles'],r['fdBefore'],r['fdAfterWarmup'],r['fdAfterCycles'],r['peakFd'],r['highFdHttpsRequests']]) for r in network)
(p/'report.html').write_text('''<!doctype html><meta charset="utf-8"><title>Android 9 多架构 CJS 与崩溃回归</title>
<style>body{font:16px system-ui;max-width:1250px;margin:35px auto;padding:20px;color:#173047}table{border-collapse:collapse;width:100%;margin:20px 0}td,th{padding:12px;border-bottom:1px solid #ccd8df;text-align:left}th{background:#edf3f7}p{line-height:1.8}</style>
<h1>Android 9 · 两套 CJS · v7 / v8 / x86</h1>
<p>本地 MEmu / API 28。ARM 使用 libnb 翻译，x86 原生运行；结果用于比较当前模拟器内的版本，不代表 ARM 真机排名。</p>
<p>央视网：同一份真实加密 TS 解密两次，测单线程及两个独立分片工作线程。央视频：CCTV-1/5/8 的真实调用记录按原顺序重放。每项重复 3 次，表中为累计耗时中位数；时间单位 ms，排除下载和播放器。</p>
<table><tr><th>ABI</th><th>央视网原版</th><th>复用缓冲区</th><th>复用 + 两线程</th><th>复用减少 %</th><th>央视频原版</th><th>组合优化</th><th>减少 %</th></tr>'''+table+f'''</table>
<p>央视网 {segment_tests} 次解密跨架构 SHA-256 一致；央视频 {native_calls:,} 次调用返回值及 NAL 数据逐项一致。央视频改善很小，v7 本轮略慢，不能据此启用全部架构。</p>
<h2>真实播放</h2><p>各架构央视频、央视网各一频道，观察 5 秒。首帧包含网络与解析，单轮结果不用于证明优化收益；播放检查使用正式站点插件。</p>
<table><tr><th>ABI</th><th>频道</th><th>结果</th><th>首帧 ms</th><th>播放时钟推进 ms</th></tr>'''+live+'''</table>
<h2>Bugly 1040</h2><p>提供的栈在系统 TLS 的 __FD_SET_chk 中终止，符合过高或无效描述符触发的边界检查。缺少原始 abort message 和对应 APK mapping，不能据此确认具体 FD 数值或归因于描述符泄漏。Android 6 源码使用 FD_SET，Android 7 源码改为 poll。</p>
<p>通用修复：Android 4.x～6.x 使用已有 Java TCP 流传输的轻量 TLS，绕开系统 TLS 的 FD_SET；补充 ARM64 库，ARM32/x86 复用原有库。代理关闭时取消仍在进行的上游请求，完整响应读取后仍保留共享连接池复用。</p>
<table><tr><th>ABI</th><th>关闭循环</th><th>初始 FD 数</th><th>5 次后 FD 数</th><th>30 次后 FD 数</th><th>压力最大 FD</th><th>成功 HTTPS 次数</th></tr>'''+pressure+'''</table>
<p>压力测试在 Android 9 上明确调用同一轻量 TLS 传输，本地 TLS 1.2 服务排除外网干扰。没有在原崩溃设备上重现或验证。测试期间一次模拟器内核等待通过重启恢复，异常轮次未作为通过结果。未修改版本号，未发布插件。</p>
<p><a href="https://android.googlesource.com/platform/external/conscrypt/+/android-6.0.1_r81/src/main/native/org_conscrypt_NativeCrypto.cpp">Android 6 TLS 源码</a> · <a href="https://android.googlesource.com/platform/external/conscrypt/+/android-7.0.0_r1/src/main/native/org_conscrypt_NativeCrypto.cpp">Android 7 TLS 源码</a></p>''',encoding='utf-8')
print(json.dumps(report,ensure_ascii=True))
