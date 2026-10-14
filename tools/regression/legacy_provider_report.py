"""Summarize API19 ARM32 measurements, requiring identical URLs and resolutions."""
import argparse
import hashlib
import json
from pathlib import Path
import statistics

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
out = args.output.resolve()
rows = [json.loads(line) for line in (out / 'results.jsonl').read_text(encoding='utf-8').splitlines()]
samples = [row for row in rows if row.get('type') == 'sample']
lookup = {(row['version'], row['batch'], row['turn']): row for row in samples}
pairs = []
for batch in [4, 5, 6, 7]:
    for turn in range(3 if batch < 6 else 6):
        before = lookup.get(('baseline', batch, turn))
        after = lookup.get(('fixed', batch, turn))
        if not before or not after:
            raise RuntimeError('Incomplete final comparison')
        if batch >= 6 and before['provider'] != '央视频':
            continue
        if any(row['status'] != 'FIRST_FRAME' for row in [before, after]):
            raise RuntimeError('Failed final sample: report failures before publishing timings')
        if before['activeStreamUrl'] != after['activeStreamUrl'] or (before['width'], before['height']) != (after['width'], after['height']):
            raise RuntimeError('Final samples have different URLs or resolutions')
        pairs.append({'provider': before['provider'], 'channel': before['channel'],
            'batch': batch, 'beforeMs': before['firstFrameMs'], 'afterMs': after['firstFrameMs'],
            'deltaMs': after['firstFrameMs'] - before['firstFrameMs'],
            'urlSha256': hashlib.sha256(before['activeStreamUrl'].encode()).hexdigest(),
            'width': before['width'], 'height': before['height'],
            'audioVerified': all(row['audioTrackPresent'] for row in [before, after]),
            'allAdvanced': all(row['advancedMs'] > 1000 for row in [before, after])})
summary = {}
for provider in ['央视频', '央视网']:
    group = [row for row in pairs if row['provider'] == provider]
    summary[provider] = {'pairs': len(group),
        'beforeMedianMs': statistics.median(row['beforeMs'] for row in group),
        'afterMedianMs': statistics.median(row['afterMs'] for row in group),
        'pairedDeltaMedianMs': statistics.median(row['deltaMs'] for row in group),
        'resolutions': sorted({str(row['width']) + 'x' + str(row['height']) for row in group}),
        'audioVerified': all(row['audioVerified'] for row in group),
        'allAdvanced': all(row['allAdvanced'] for row in group)}
failures = [{k: row.get(k) for k in ['version', 'batch', 'provider', 'channel', 'status', 'statusText']}
    for row in samples if row['status'] != 'FIRST_FRAME']
continuous = [{k: row.get(k) for k in ['provider', 'channel', 'observeSeconds', 'advancedMs',
    'bufferingObservedMs', 'outputFrameRate', 'outputFrameCount', 'pausedOutputFps', 'debugFpsLabel']}
    for row in samples if row.get('observeSeconds', 0) >= 60]
out.joinpath('summary.json').write_text(json.dumps({'summary': summary, 'pairs': pairs,
    'failures': failures, 'continuous': continuous}, ensure_ascii=False, indent=2), encoding='utf-8')
lines = ['# Android 4.4 / ARM32 回归', '',
    '设备：`8ba8ceb8`，Samsung SM_G3608，API19，原生 armeabi-v7a。',
    '基线：实际 1.6.0 发布代码 `648254cec3f1a673bb51a35e306f1ecb33b7f26c`。',
    '当前：1.8.0 工作区修复版，包含首帧缓冲修复与实时 FPS 窗口修复。', '',
    '用独立测试包，自动选择 CCTV-1、CCTV-5、CCTV-13，自动解码、高画质、稳定延迟模式。',
    '首帧从切台调用计时，要求同一次请求收到真实出帧与播放就绪信号。不是安装或 APK 冷启动耗时。',
    '插件安装另行完成；每个样本随后确认音轨和播放时钟推进。', '',
    '| 来源 | 同地址、同分辨率对照数 | 1.6.0 首帧中位数 | 修复版首帧中位数 |',
    '|---|---:|---:|---:|']
for name, value in summary.items():
    lines.append(f"| {name} | {value['pairs']} | {value['beforeMedianMs']/1000:.3f} 秒 | {value['afterMedianMs']/1000:.3f} 秒 |")
lines += ['', '央视频采用相同仍可播放的签名 URL，测试期间刷新测试包内 URL 缓存时间；',
    '不代表过期链接或首次联网解析的表现。央视网使用基线解析得到的相同 CDN 地址，',
    'CCTV-5 保持 1080p，CCTV-1、CCTV-13 保持 720p，以免不同码率影响判断。', '',
    '批次 4、5 为央视网同地址对照；批次 6、7 为央视频同地址对照；两轮交换执行顺序。',
    '早期批次 2、3 未成功保持央视频 URL 一致：设备时钟与电脑相差约 4 小时，',
    '用电脑时间刷新缓存会使缓存时间落在设备的未来，播放器正确拒绝使用。',
    '改用设备时间后重新测量，早期数据保留，排除在最终配对统计外。',
    '直播片段会随时间推进，同一地址也会返回不同片段；六组配对仅说明本轮结果，',
    '央视频约 48ms 的中位数差不能视为显著提升。',
    '三星 API19 的 run-as 不可用，缓存由测试 instrumentation 自身导出/导入；',
    '压缩传输避免旧 adbd 的 4096 字节命令限制。原 nTv 的数据未清除。', '',
    '所有原始样本（包括失败）在 results.jsonl，配对 URL 哈希、耗时及音轨检查在 summary.json。']
for row in continuous:
    lines += ['', f"连续播放：{row['provider']} {row['channel']} {row['observeSeconds']} 秒，"
        f"时钟推进 {row['advancedMs']}ms，观察到缓冲 {row['bufferingObservedMs']}ms；"
        f"调试信息：{row['debugFpsLabel']}，暂停输出 {row['pausedOutputFps']}fps。"]
if failures:
    lines += ['', '早期失败记录：']
    for failure in failures:
        lines.append(f"- {failure['version']} / 批次 {failure['batch']} / {failure['provider']} {failure['channel']}：{failure['statusText']}")
out.joinpath('REPORT.md').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False, indent=2))
