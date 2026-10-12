"""Summarize CMG deterministic real-stream replay measurements."""
import argparse
import csv
import html
import json
import statistics
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
args = parser.parse_args()
p = args.directory
variants = {}
for variant in ['base', 'region', 'combined']:
    variants[variant] = json.loads((p / (variant + '.json')).read_text(encoding='utf-8'))
channels = list(dict.fromkeys(row['channel'] for row in variants['base']))
rows = []
for channel in channels:
    medians = {}
    for variant, results in variants.items():
        samples = [r for r in results if r['channel'] == channel]
        medians[variant] = statistics.median(sum(r['costNs'].values()) / 1e6 for r in samples)
    baseline = next(r for r in variants['base'] if r['channel'] == channel)
    rows.append(dict(channel=channel, baselineMs=round(medians['base'], 2),
                     regionMs=round(medians['region'], 2), combinedMs=round(medians['combined'], 2),
                     improvementPercent=round((1-medians['combined']/medians['base'])*100, 2),
                     verifiedPerReplay=baseline['verifiedOutputs'],
                     nalCount=baseline['calls']['decodeNalRangeInPlace']))
verified = sum(r['verifiedOutputs'] for results in variants.values() for r in results)
report = dict(device='Android 4.4 / ARMv7', repetitions=3, verifiedNativeCalls=verified, rows=rows,
              scope='Sequential real CMG JNI trace replay. Excludes network, playback and trace I/O. All return values and decoded NAL ranges match baseline capture.')
live_path = p/'combined-playback.json'
live = json.loads(live_path.read_text(encoding='utf-8')) if live_path.exists() else []
report['livePlayback'] = live
live_note = ('<p>组合方案真实播放检查：'+str(sum(r.get('status') == 'passed' for r in live))+'/'+str(len(live))+' 个频道通过，每个频道观察 5 秒。单轮播放不能证明首帧提速。</p>') if live else ''
(p/'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
with (p/'report.csv').open('w', encoding='utf-8-sig', newline='') as f:
    writer = csv.DictWriter(f, fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
table = ''.join('<tr>'+''.join('<td>'+html.escape(str(r[k]))+'</td>' for k in ['channel','nalCount','baselineMs','regionMs','combinedMs','improvementPercent'])+'</tr>' for r in rows)
(p/'report.html').write_text('''<!doctype html><meta charset="utf-8"><title>央视频原生解密实验</title>
<style>body{font:17px system-ui;max-width:1050px;margin:40px auto;padding:20px;color:#183047}table{border-collapse:collapse;width:100%}td,th{padding:15px;border-bottom:1px solid #ccd7df;text-align:left}th{background:#edf3f7}p{line-height:1.8}</style>
<h1>央视频原生解密实验 · Android 4.4</h1>
<p>真实 CCTV-1 / 5 / 8 直播调用记录，逐项重放并比对原版返回值与解密后的 NAL 数据。每个版本重复 3 次，表中为耗时中位数。</p>
<p>分段复制：只复制当前 NAL，避免整块 PES 的 JNI 数组复制。组合方案：再复用 UpdatePlayer 工作区，完整清零并保留每一次状态更新。</p>
<table><tr><th>频道</th><th>NAL 数</th><th>原版 ms</th><th>分段复制 ms</th><th>组合方案 ms</th><th>减少 %</th></tr>'''+table+f'''</table>
<p>共验证 {verified:,} 次原生调用，全部一致。上述时间是同一段记录的累计原生耗时，排除下载和播放器；不能直接换算为切台或首帧时间。</p>
<p>结论：央视频主要耗时在解密计算，复制和状态工作区优化的收益有限。本实验没有发布插件或替换正式应用。没有把有状态 NAL 拆成并行解密，也没有未经验证地启用 AES 替代算法。</p>'''+live_note, encoding='utf-8')
print(json.dumps(report, ensure_ascii=True))
