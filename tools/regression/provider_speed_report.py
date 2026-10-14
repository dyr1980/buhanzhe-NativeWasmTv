"""Summarize measured live first frames; never mix different URLs in paired results."""
import hashlib
import json
import math
from pathlib import Path
import statistics

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.codex-tmp/android9-v16-speed'
rows = [json.loads(line) for line in (OUT / 'results.jsonl').read_text(encoding='utf-8').splitlines()]
samples = [row for row in rows if row.get('type') == 'sample']


def summarize(values):
    ready = [row for row in values if row['status'] == 'FIRST_FRAME']
    times = sorted(row['firstFrameMs'] for row in ready)
    if not times:
        return {'samples': len(values), 'successes': 0}
    return {'samples': len(values), 'successes': len(ready),
        'medianMs': statistics.median(times), 'meanMs': round(statistics.mean(times), 1),
        'p90Ms': times[math.ceil(len(times) * .9) - 1], 'minMs': times[0], 'maxMs': times[-1],
        'resolutions': sorted({str(row['width']) + 'x' + str(row['height']) for row in ready}),
        'audioVerified': all(row.get('audioTrackPresent', True) for row in ready),
        'allAdvanced': all(row.get('advancedMs', 0) > 1000 for row in ready)}


initial = {version: {provider: summarize([row for row in samples
    if row['version'] == version and row['provider'] == provider and row['batch'] <= 3])
    for provider in ['央视频', '央视网']} for version in ['baseline', 'current']}
final = {version: {provider: summarize([row for row in samples
    if row['version'] == version and row['provider'] == provider and 27 <= row['batch'] <= 30])
    for provider in ['央视频', '央视网']} for version in ['current', 'fixed']}
if any(value['samples'] != 12 for providers in final.values() for value in providers.values()):
    raise RuntimeError('Final comparison is incomplete; refusing to publish a partial result')
lookup = {(row['version'], row['batch'], row['turn']): row for row in samples
    if 27 <= row['batch'] <= 30 and row['status'] == 'FIRST_FRAME'}
pairs = []
for (version, batch, turn), before in lookup.items():
    after = lookup.get(('fixed', batch, turn))
    if version == 'current' and after and before['activeStreamUrl'] == after['activeStreamUrl']:
        pairs.append({'provider': before['provider'], 'channel': before['channel'], 'batch': batch,
            'beforeMs': before['firstFrameMs'], 'afterMs': after['firstFrameMs'],
            'deltaMs': after['firstFrameMs'] - before['firstFrameMs'],
            'urlSha256': hashlib.sha256(before['activeStreamUrl'].encode()).hexdigest()})
matched = {provider: {'pairs': len(group),
    'beforeMedianMs': statistics.median(row['beforeMs'] for row in group),
    'afterMedianMs': statistics.median(row['afterMs'] for row in group),
    'pairedDeltaMedianMs': statistics.median(row['deltaMs'] for row in group)}
    for provider in ['央视频', '央视网']
    if (group := [row for row in pairs if row['provider'] == provider])}
same_stream = {version: summarize([row for row in samples if row['version'] == version
    and row['batch'] == 34 and row.get('fixedCctv')]) for version in ['baseline', 'fixed']}
long_runs = [row for row in samples if row.get('observeSeconds', 0) >= 60]
mode_checks = {mode: summarize([row for row in samples if row['version'] == 'fixed'
    and row['batch'] == batch]) for mode, batch in [('auto', 35), ('balanced', 37)]}
summary = {'initial': initial, 'final': final, 'matched': matched, 'pairs': pairs,
    'sameCctvStream': same_stream, 'longRuns': long_runs, 'modeChecks': mode_checks}
(OUT / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding='utf-8')
lines = ['# Android 9 ARM32 央视频 / 央视网首帧回归', '',
    '- 日期：2026-10-13；设备：本地 MEmu NX809J，Android 9 / API 28。',
    '- 安装 ABI：armeabi-v7a，APK 中也只包含 ARM32 原生库。设备使用 ARM 翻译运行，结果代表这台本地设备。',
    '- 1.6.0 基线：648254cec3f1a673bb51a35e306f1ecb33b7f26c，源码和发布清单均为 1.6.0。v1.6.0 标签实际仍是 1.5.0，因此未使用该标签。',
    '- 修复前：4b8e571 的工作副本，包含本轮之前的未提交修复。修复后：同一副本加本轮两处启动策略修复。',
    '- 独立测试包 speed16 / speed18 / speed18fixed；没有替换或清空原 nTv。',
    '- CCTV-1、CCTV-5、CCTV-13；相同高清、自动解码、稳定缓冲设置；两版顺序交替。',
    '- 从主线程发起选台计时，到当前播放请求收到实际首帧。25 ms 轮询；另核实分辨率、音轨和播放进度。',
    '- 插件安装不计入选台耗时。当前及修复版使用相同央视频 v2、央视网 v3、armv7-perf 插件。',
    '- 每个进程启动后先完成 Activity 初始化。因此“coldProcessSample”表示该进程的首个手动选台，不能解读为启动 APK 到首帧。', '',
    '## 初始对比 1.6.0', '', '| 来源 | 1.6.0 中位数 | 修复前中位数 | 每版次数 |',
    '|---|---:|---:|---:|']
for provider in ['央视频', '央视网']:
    old, new = initial['baseline'][provider], initial['current'][provider]
    lines.append(f"| {provider} | {old['medianMs']/1000:.3f} 秒 | {new['medianMs']/1000:.3f} 秒 | {old['samples']} |")
lines += ['', '央视频两版均为 1920×1080；最初中位数约 7% 的差异较小，后续对照没有稳定复现同样幅度，不能把整个差异都归因于代码。',
    '央视网 CCTV-1 / CCTV-13 两版均为 720p，最初中位数 1.244 → 0.922 秒；CCTV-5 为 1.6.0 的 1080p 对当前 720p，不能直接用于相同画质的速度结论。', '',
    '## 确认的问题与修复', '',
    '1. 新增央视频清单级同步解密等待，阻止 IJK 在分片解密期间开始处理清单。Android 4.1 及以后返回清单不再等解密；TS 请求本身仍等待完整解密分片。Android 4.0 框架播放器保留原启动窗口。',
    '2. 已有完整分片代理的央视频 / 央视网在稳定模式下仍启用 IJK 启动缓冲和较长流探测。统一复用代理预取储备；普通 HLS、VOD 的策略保留。',
    '3. 后续实测发现央视网准备完成后偶发等待约 4 秒才出帧，日志为 Buffering recovered；新的代理控制策略消除了本轮最终样本中的该长等待。', '',
    '## 修复前后最终对照', '', '| 来源 | 修复前中位数 / P90 | 修复后中位数 / P90 | 每版次数 |',
    '|---|---:|---:|---:|']
for provider in ['央视频', '央视网']:
    old, new = final['current'][provider], final['fixed'][provider]
    lines.append(f"| {provider} | {old['medianMs']/1000:.3f} / {old['p90Ms']/1000:.3f} 秒 | "
        f"{new['medianMs']/1000:.3f} / {new['p90Ms']/1000:.3f} 秒 | {old['samples']} |")
lines += ['', '所有最终样本均验证音轨及播放进度。对照通过共享仍可播放的签名地址，并刷新测试包缓存时间，防止 10 分钟缓存过期重新选择 CDN；这不是缓存失效测试。',
    '只有实际播放地址完全相同的配对才进入下面的严格对照；未配对样本仍保留在原始数据与整体统计中。', '',
    '| 来源 | 相同地址配对数 | 修复前中位数 | 修复后中位数 | 配对差值中位数 |',
    '|---|---:|---:|---:|---:|']
for provider, value in matched.items():
    lines.append(f"| {provider} | {value['pairs']} | {value['beforeMedianMs']:.0f} ms | "
        f"{value['afterMedianMs']:.0f} ms | {value['pairedDeltaMedianMs']:+.0f} ms |")
lines += ['', '央视频收益应按这些实测数值理解，不能宣传为固定大幅提速。分片长度、下载抖动及硬解出帧仍影响耗时。', '',
    '## 相同央视网码流的 1.6.0 对照', '',
    '固定使用本轮 VDN 返回的有效 CDN 地址及 b=200-4000，对相同三个码流比较；这一组不包含网页/API 地址解析。旧静态 CDN 的失败尝试属于过期线路，单独保留，不纳入性能统计。', '',
    '| 版本 | 成功 / 次数 | 中位数 | P90 |', '|---|---:|---:|---:|']
for version, value in same_stream.items():
    lines.append(f"| {'1.6.0' if version=='baseline' else '修复版'} | {value['successes']} / {value['samples']} | "
        + (f"{value['medianMs']:.0f} ms | {value['p90Ms']} ms |" if value['successes'] else '-- | -- |'))
lines += ['', '## 稳定性与回归', '',
    '- 阻塞解密任务的确定性回归：修复前超时；修复后清单先返回，未解密 TS 仍阻塞，解密就绪后可读取。',
    '- Android 9 启动窗口五项检查通过：低延迟、首段就绪而第二段阻塞、双段等待、CMG 有序预取/合并/取消、清单与 TS 就绪分离。',
    '- 主机 HLS 磁盘缓存、热切换/时钟、流探测错误和加载计时回归通过。',
    '- 实际直播长播放记录：']
for row in long_runs:
    lines.append(f"  - batch {row['batch']} {row['provider']} {row['channel']}："
        f"{row['observeSeconds']} 秒，推进 {row['advancedMs']} ms，再缓冲观察 {row['bufferingObservedMs']} ms，音轨 {row.get('audioTrackPresent')}。")
for mode, value in mode_checks.items():
    lines.append(f"- {mode} 模式：{value['successes']} / {value['samples']} 次选台成功。")
lines += ['', '## 原始记录', '',
    '- results.jsonl：每次选台原始 JSON，含状态、耗时、实际地址、分辨率、音轨、观察进度及后期测试的生产源码摘要。',
    '- logs/：instrumentation 输出和设备分阶段日志。部分早期 logcat 读取遇到 EOF 或混入后续进程；首帧统计来自各次 instrumentation JSON，不依赖混杂日志。',
    '- summary.json：分组统计和严格配对结果。',
    '- checks/：确定性回归记录。',
    '- baseline/、current/、fixed/：可复现的隔离源码和 ARM32 APK。',
    '- batch 21：设备短暂断连导致新包构建未执行后的中止尝试，按 productionHash 可识别为上一候选，未纳入最终结果。',
    '- batch 36：被测试编排的并发 instrumentation 中断；batch 37 隔离重跑，原中断日志保留。',
    '- 默认 auto 与 balanced 模式补测见 results.jsonl。稳定模式用于与 1.6.0 保持相同设置。', '']
(OUT / 'REPORT.md').write_text('\n'.join(lines), encoding='utf-8')
print(json.dumps({'final': final, 'matched': matched, 'sameCctvStream': same_stream}, ensure_ascii=False))
