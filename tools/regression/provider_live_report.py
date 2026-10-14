"""Render the two provider rounds without hiding failed/retried observations."""
from collections import Counter
import hashlib
import html
import json
from pathlib import Path
import re
import statistics

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.codex-tmp/provider-live-rounds'
RETIRED = '0123456789ABCDEF'

def main():
    rows, summaries, preflights = [], [], []
    for turn in (3, 4):
        for directory in sorted((OUT / f'round{turn}').iterdir()):
            if not directory.is_dir():
                continue
            state = json.loads((directory / 'status.json').read_text(encoding='utf-8'))
            cases = json.loads((directory / 'provider-live.json').read_text(encoding='utf-8'))
            preflight = json.loads((directory / 'ui-preflight.json').read_text(encoding='utf-8'))
            assert len(cases) == 10 and all(r['status'] in ('passed', 'failed') for r in cases)
            assert preflight['status'] == 'passed', str(directory)
            device = state['device']
            serial = device['serial']
            preflights.append(dict(device=device, round=turn, **preflight))
            for provider in ('央视频', '央视网'):
                group = [r for r in cases if r['provider'] == provider]
                assert len(group) == 5 and len({r['channel'] for r in group}) == 5
                frame = [r['firstFrameMs'] for r in group if 'firstFrameMs' in r]
                edge = [r['edgeDelay'] for r in group if r.get('edgeDelay', {}).get('status') == 'estimated']
                summaries.append(dict(device=device, retired=serial == RETIRED, provider=provider, round=turn,
                    counts=dict(Counter(r['status'] for r in group)), medianFirstMs=statistics.median(frame),
                    minFirstMs=min(frame), maxFirstMs=max(frame),
                    maxCloseDrawMs=max(r['panelCloseDrawMs'] for r in group),
                    estimatedEdges=len(edge), edgeMinMs=min((e['estimateLowerMs'] for e in edge), default=None),
                    edgeMaxMs=max((e['estimateUpperMs'] for e in edge), default=None)))
            for r in cases:
                folder = directory.relative_to(OUT).as_posix()
                r.update(device=device, retired=serial == RETIRED, build=state['build'],
                         evidence=folder + '/provider-live.json', log=folder + '/player.log',
                         image=folder + f"/provider-{r['index']:02d}.png")
                r['lowFps'] = 'outputFps' in r and r['outputFps'] < r.get('sourceFps', 25) * .8
                r['hasImage'] = (OUT / r['image']).is_file()
                current = ''
                events = []
                for line in (directory / 'player.log').read_text(encoding='utf-8', errors='replace').splitlines():
                    if not re.search(r'\s'+str(r['appPid'])+r'\s', line):
                        continue
                    switched = re.search(r'Channel switch begin .*channel=(.*)$', line)
                    if switched:
                        current = switched.group(1)
                    if current == r['channel'] and 'Recovering stalled playback' in line:
                        events.append(line)
                r['recoveryEvents'] = events
                rows.append(r)
    assert len(rows) == 60 and len(preflights) == 6
    active = [r for r in rows if not r['retired']]
    diagnostics = []
    notes = json.loads((OUT / 'diagnostics/interpretations.json').read_text(encoding='utf-8')) if (OUT / 'diagnostics/interpretations.json').exists() else {}
    for directory in sorted((OUT / 'diagnostics').iterdir()):
        path = directory / 'provider-live.json'
        if directory.is_dir() and path.exists():
            diagnostics.append(dict(name=directory.name, evidence=path.relative_to(OUT).as_posix(), interpretation=notes.get(directory.name),
                rows=json.loads(path.read_text(encoding='utf-8'))))
    apks = []
    for abi in ('arm32', 'arm64'):
        path = ROOT / f'app/build/outputs/apk/{abi}/release/app-{abi}-release.apk'
        if path.exists():
            apks.append(dict(abi=abi, path=str(path), sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
    payload = dict(rows=rows, summaries=summaries, preflights=preflights, diagnostics=diagnostics, apks=apks,
                   formalCounts=dict(Counter(r['status'] for r in rows)),
                   activeCounts=dict(Counter(r['status'] for r in active)),
                   releaseAssessment='Review final uninterrupted-playback checks and bounded recovery diagnostics before rollout; KT107 adaptation stopped at user request')
    (OUT / 'results.json').write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding='utf-8')
    # Keep raw time series in results.json and per-device files, not in the DOM.
    compact = [{k: v for k, v in r.items() if k != 'samples'} for r in rows]
    data = json.dumps(dict(rows=compact, summaries=summaries), ensure_ascii=False).replace('</', '<\\/')
    diagnostics_html = ''.join(f'<li><a href="{html.escape(d["evidence"])}">{html.escape(d["name"])}</a>: '
                               f'{len(d["rows"])} 次，{sum(r["status"] == "passed" for r in d["rows"])} 检查通过，'
                               f'{sum(len(r.get("recoveries", [])) for r in d["rows"])} 次恢复</li>' for d in diagnostics)
    page = '''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>央视频 / 央视网 · 修复后两轮设备回归</title><link rel="icon" href="data:,">
<style>
:root{font-family:system-ui,"Microsoft YaHei",sans-serif;color:#192333;background:#f3f5f8}body{margin:0;padding:28px;max-width:1550px;margin:auto}h1{font-size:27px;margin:0 0 12px}h2{font-size:19px}p{line-height:1.7;margin:8px 0}.card{background:white;border:1px solid #dde3ea;border-radius:12px;padding:18px;margin:16px 0}.warn{background:#fff5dd;border-color:#e8cc83}.chips{display:flex;gap:12px;flex-wrap:wrap}.chip{background:#eef4fb;border-radius:8px;padding:12px 18px}.chip b{font-size:25px;display:block}label{display:inline-flex;align-items:center;gap:7px;margin:0 18px 10px 0}select{padding:7px;border:1px solid #cad3df;border-radius:6px}table{width:100%;border-collapse:collapse;font-size:13px}th,td{text-align:left;padding:9px;border-bottom:1px solid #e6eaf0;vertical-align:top}th{background:#eef2f7;white-space:nowrap}a{color:#1769aa}small{color:#657489}details summary{cursor:pointer;color:#1769aa}pre{white-space:pre-wrap;max-width:450px;font-size:11px}td img{width:230px;max-width:100%;border-radius:4px}.passed{color:#187443}.failed{color:#b12636;font-weight:600}.retired{color:#777}.table-wrap{overflow-x:auto}.muted{color:#657489}.number{font-variant-numeric:tabular-nums;white-space:nowrap}@media(max-width:800px){body{padding:12px}h1{font-size:22px}.card{padding:12px}}
</style>
<h1>央视频 / 央视网 · 两轮设备回归</h1><p class="muted">CCTV-1、2、5、8、13 · 稳定模式 · 每次首帧后观察 30 秒 · 2026-10-14</p>
<div class="chips"><div class="chip"><b>60</b>修复后正式播放用例</div><div class="chip"><b>__PASS__ / 60</b>连续播放检查通过</div><div class="chip"><b>6 / 6</b>解密忙时 UI 检查</div><div class="chip"><b>60 / 60</b>选台关闭绘制通过</div></div>
<div class="card warn"><b>上线评估：__ASSESSMENT__</b><p>修复前 Android 9 模拟器 ARM32 发生 CCTV-1 呈现输出停止，音频时钟和解码计数继续走，换台顺序重播再次复现；相同顺序软件解码正常。已补普通 HLS 直播的视频输出监测：有过首帧后连续 10 秒无输出时走有限重试，自动模式只为当前播放回退软解，保留用户设置。暂停、缓冲、纯音频及有限视频不触发该新增检查。它修复了永远无法恢复的监测缺口，没有证明底层硬件驱动异常已消失。</p><p>KT107（0123456789ABCDEF）按用户要求停止适配，不再运行本次修复后两轮。此前 20 次数据在<a href="report-before-video-recovery.html">修复前 80 次报告</a>中保留；其时钟检查使用历史最高位置，可能混入 HLS 位置回跳，不能仅凭该断言认定持续停播。</p></div>
<div class="card"><b>本次修复：先绘制关闭，再开始频道加载</b><p>选台回调先隐藏列表、恢复焦点；在关闭画面完成当前绘制后才执行插件、播放器和服务准备。新选台、重新打开列表及退出会取消过期回调。Android 4.4 本次 20 次关闭绘制为 __UI_RANGE__ ms；另有解密忙时的 UI 检查，两轮均通过。</p><p>测试设备是本地 SM-G3608（4.4.4），遥控方向键 / OK 输入；用户实际电视未连接 ADB，因此不是实机电视测量。</p></div>
<div class="card"><h2>首帧与直播边缘延迟</h2><p>首帧从遥控 OK 到播放器首帧事件，含列表关闭；插件安装在计时外。档案第 3 轮是本次第 1 轮，清 URL 缓存；档案第 4 轮是本次第 2 轮，保留缓存并反向选台（缓存是否仍有效取决于 TTL）。全部耗时采用单调时钟。</p><p>CDN 范围仅估算“已完成分片边缘 → 播放器”的额外距离，含一个分片的不确定性，不是广播到屏幕的绝对延迟。分片已离开源列表窗口时显示不可测，不能按 0 计算。单轮只有 5 个频道，不用于推断长期 P95。</p><div class="table-wrap"><table><thead><tr><th>设备 / 版本</th><th>档案轮次</th><th>平台</th><th>通过</th><th>首帧中位数</th><th>首帧范围</th><th>关闭最大</th><th>可估算 CDN 范围</th></tr></thead><tbody id="summaryRows"></tbody></table></div></div>
<div class="card"><h2>逐次证据</h2><label>设备<select id="device"><option value="">全部</option></select></label><label>平台<select id="provider"><option value="">全部</option><option>央视频</option><option>央视网</option></select></label><label>档案轮次<select id="round"><option value="">全部</option><option value="3">3</option><option value="4">4</option></select></label><label>结果<select id="status"><option value="">全部</option><option value="passed">通过</option><option value="failed">检查失败</option><option value="low">低帧率</option></select></label><span id="count"></span><div class="table-wrap"><table><thead><tr><th>设备</th><th>轮</th><th>平台 / 频道</th><th>结果</th><th>关闭 / 首帧</th><th>调试 fps</th><th>输出最长空档</th><th>证据</th></tr></thead><tbody id="caseRows"></tbody></table></div></div>
<div class="card"><h2>补充复现（不计入 60 次）</h2><ul>__DIAGNOSTICS__</ul><p>自然异常期间两张相隔约 3 秒的纯视频截图完全一致，输出计数也停在同一值。晚故障注入在第 42 秒撤掉视频输出，自动回退软解后持续观察 30 秒，恢复约 25 fps。</p><p>旧专用诊断有一次使用错误的重启后计时基准，另一次在恢复后只剩约 17 秒（要求至少 20 秒）。原始失败均保留；最新晚故障诊断延长恢复后的观察并验证通过。<a href="diagnostics/interpretations.json">诊断说明</a></p><p><a href="results.json">完整结果与采样序列</a> · <a href="release-build.log">发布包构建记录</a></p><p>每例检查选台绘制顺序、音频轨、扫描方式、源帧率、播放时钟、输出计数和视频截图。通过不等于源帧率满速，也不能代替数小时长稳及未连接电视的验证。允许恢复的专用诊断会保留恢复次数和输出空档，不能按无卡顿用例计数。调试 fps 是观测结束时的值。</p></div>
<script>const data=__DATA__;
const esc=x=>String(x??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));const sec=x=>(x/1000).toFixed(2)+' s';
const devices=[...new Map(data.rows.map(r=>[r.device.serial,r.device])).values()];for(const d of devices){const o=document.createElement('option');o.value=d.serial;o.textContent=d.model+' · Android '+d.release;document.querySelector('#device').append(o)}
document.querySelector('#summaryRows').innerHTML=data.summaries.map(s=>`<tr class="${s.retired?'retired':''}"><td>${esc(s.device.model)} / ${esc(s.device.release)} ${s.retired?'（停止适配）':''}</td><td>${s.round}</td><td>${s.provider}</td><td>${s.counts.passed||0}/5</td><td class="number">${sec(s.medianFirstMs)}</td><td class="number">${sec(s.minFirstMs)}～${sec(s.maxFirstMs)}</td><td>${s.maxCloseDrawMs} ms</td><td>${s.estimatedEdges?sec(s.edgeMinMs)+'～'+sec(s.edgeMaxMs)+'（'+s.estimatedEdges+'/5可测）':'不可测'}</td></tr>`).join('');
function render(){const value=id=>document.querySelector('#'+id).value;const rows=data.rows.filter(r=>(!value('device')||r.device.serial===value('device'))&&(!value('provider')||r.provider===value('provider'))&&(!value('round')||String(r.round)===value('round'))&&(!value('status')||(value('status')==='low'?r.lowFps:r.status===value('status'))));document.querySelector('#count').textContent=rows.length+' 次';document.querySelector('#caseRows').innerHTML=rows.map(r=>`<tr><td>${esc(r.device.model)}${r.retired?'<br><small>停止适配</small>':''}</td><td>${r.round}</td><td>${r.provider}<br>${esc(r.channel)}</td><td class="${r.status}">${r.status==='passed'?'通过':'检查失败'}${r.lowFps?'<br><small>低帧率</small>':''}</td><td class="number">${r.panelCloseDrawMs} ms / ${sec(r.firstFrameMs)}</td><td class="number">${esc(r.fpsLabel)}<br><small>${esc(r.width)}×${esc(r.height)} / ${esc(r.scanType)}</small></td><td class="number">${r.maxNoVideoFramesMs==null?'—':r.maxNoVideoFramesMs+' ms'}</td><td><a href="${r.evidence}">JSON</a> · <a href="${r.log}">日志</a><details><summary>截图 / 诊断</summary>${r.hasImage?`<img loading="lazy" src="${r.image}" alt="${esc(r.channel)}">`:`<small>截图未采到：${esc(r.captureError||'未生成')}</small>`}<pre>${esc(r.startupTiming)}\n${esc(r.decoder)}\n${esc(r.error||'')}\n${esc(r.recoveryEvents.join('\\n'))}</pre></details></td></tr>`).join('')};document.querySelectorAll('select').forEach(s=>s.onchange=render);render();
</script></html>'''
    legacy_close = [r['panelCloseDrawMs'] for r in rows if r['device']['sdk'] == 19]
    assessment = '仍有连续播放检查失败，暂缓全量上线' if payload['activeCounts'].get('failed') else '两轮短时回归通过，可小范围灰度；底层硬件异常仍需长稳观察'
    page = (page.replace('__DATA__', data).replace('__DIAGNOSTICS__', diagnostics_html)
            .replace('__PASS__', str(payload['activeCounts'].get('passed', 0)))
            .replace('__ASSESSMENT__', assessment)
            .replace('__UI_RANGE__', str(min(legacy_close))+'～'+str(max(legacy_close))))
    (OUT / 'report.html').write_text(page, encoding='utf-8')
    print(json.dumps(dict(formal=payload['formalCounts'], active=payload['activeCounts'], cases=len(rows), preflights=len(preflights))))

if __name__ == '__main__':
    main()
