"""Build a local, evidence-linked report without treating capacity limits as playback success."""
import argparse
from collections import Counter
import json
from pathlib import Path
import statistics

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'.codex-tmp/all-device-rounds'
MEDIA=ROOT/'.codex-tmp/hls-debug-matrix'

def median(rows,key):
    values=[r[key] for r in rows if isinstance(r.get(key),(int,float))]
    return round(statistics.median(values),1) if values else None

def collect(rounds):
    rows=[];summaries=[];builds={}
    for number in rounds:
        folder=OUT/f'round{number}'
        manifest=json.loads((folder/'manifest.json').read_text(encoding='utf-8'))
        builds[str(number)]=manifest['apks']
        for device in manifest['devices']:
            serial=device['serial'];safe=serial.replace(':','_')
            status=json.loads((folder/safe/'status.json').read_text(encoding='utf-8'))
            selected=[]
            for kind,base in [('media',MEDIA/safe/f'all-round{number}'),('rtp',MEDIA/'rtp'/safe/f'all-round{number}')]:
                source=base/'results.json'
                data=json.loads(source.read_text(encoding='utf-8')) if source.exists() else []
                for row in data:
                    r=dict(row,device=device['model'],serial=serial,sdk=device['sdk'],round=number,kind=kind)
                    if r.get('status')=='running' and status['phase']=='finished':r['status']='interrupted';r['error']='Process ended before this case completed; see worker log'
                    r['evidence']=source.relative_to(ROOT/'.codex-tmp').as_posix()
                    index=r['index']
                    capture=base/(f'playlist-{index:04d}.png' if kind=='media' else f'{index:04d}.png')
                    r['capture']=capture.relative_to(ROOT/'.codex-tmp').as_posix() if capture.exists() else None
                    log=base/('player.log' if kind=='media' else f'{index-1}-logcat.txt')
                    r['log']=log.relative_to(ROOT/'.codex-tmp').as_posix() if log.exists() else None
                    r['slow']=r.get('status')=='played' and r.get('sourceFps',0)>0 and r.get('outputFps',0)<r['sourceFps']*.8
                    selected.append(r);rows.append(r)
            counts=Counter(r.get('status','missing') for r in selected)
            checks=MEDIA/safe/f'all-round{number}'/'live-delay-preferences-checks.json'
            preferences=json.loads(checks.read_text(encoding='utf-8')) if checks.exists() else {}
            summaries.append(dict(device=device,round=number,rows=len(selected),expected=77,
                complete=len(selected)==77 and status['phase']=='finished' and not status['errors'] and not counts.get('interrupted'),
                counts=dict(counts),probeFailures=sum(r.get('probeStatus')=='failed' for r in selected),
                failures=sum(value for state,value in counts.items() if state not in ('played','capabilityLimited')),
                slow=sum(r['slow'] for r in selected),firstFrameMedianMs=median([r for r in selected if r.get('status')=='played' and r['kind']=='media' and r.get('index',100)>0 and r.get('index',100)<=56],'firstFrameMs'),
                probeMedianMs=median(selected,'probeMs'),pssPeakKb=max((r.get('processPssKb',0) for r in selected),default=0),
                preferences=preferences,preferencesEvidence=checks.relative_to(ROOT/'.codex-tmp').as_posix() if checks.exists() else None,
                liveDelayModes=dict(Counter(r.get('liveDelayMode','missing') for r in selected if r['kind']=='media')),
                errors=status['errors']))
    return dict(rounds=rounds,rows=rows,summaries=summaries,builds=builds)

TEMPLATE='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><link rel="icon" href="data:,"><title>全设备播放复测</title>
<style>body{font:15px system-ui;background:#f4f6fa;color:#182334;margin:0}main{max-width:1500px;margin:auto;padding:28px}h1{font-size:27px}p{line-height:1.7}table{border-collapse:collapse;width:100%;background:white}td,th{padding:10px;border-bottom:1px solid #e4e8ef;text-align:left;vertical-align:top}th{position:sticky;top:0;background:#eaf0f7}a{color:#076ac1}.bad{color:#ad2323}.limit,.slow{color:#875100}.good{color:#076b40}.panel{padding:16px;background:white;border-radius:12px;margin:18px 0}select,input{padding:8px;margin:6px;border:1px solid #bcc8d9;border-radius:6px}small{color:#617087}pre{white-space:pre-wrap;word-break:break-word;font-size:12px;max-height:360px;overflow:auto}.scroll{overflow:auto;max-height:70vh}button{cursor:pointer}#count{font-weight:600}</style>
<main><h1>全设备解码、探测与调试信息复测</h1><p>覆盖 H.264 / H.265、360p–4K、25/30/50 fps、1080i50、8/10bit、SDR/HLG/PQ、AAC/MP3/AC-3/E-AC-3/Atmos JOC、无音轨、不同码率、HLS/MP4、RTP MPEG-TS 与 SDP。在线用例包含洛阳三台、Apple HLS、Dolby MP4、凤凰三个脚本频道。旧设备只测试 ARM32；小米 6 测试 ARM64。</p>
<div class="panel"><p><b>结果口径：</b>“播放通过”要求首帧、时钟推进、实际视频画面与调试信息断言通过；不保证设备能达到源帧率。低于源帧率 80% 的结果另标为低帧率。低内存保护提示单独计数，不计入播放通过。节目本身、网络与芯片解码能力仍会影响结果。</p><p>修复了 MT6580 不支持场编码 AVC 时硬解无画面、EGL 高度误用宽度导致额外内存分配、低资源软解输出缓冲过大、探测创建播放解码器及 HEVC 忽略跳帧参数仍解码大画面、低内存 UHD 软解失败清理、RTP 接收缓冲不足，以及 HTTP/2 请求失败后缺少按主机的协议回退。资源探测现在在单次请求内复用清单，并仅读取默认音轨；正常播放保留完整音轨。测试改用 Android 9 PixelCopy 读取实际视频 Surface；旧系统捕获时隐藏播放器 UI。</p><p>本次统计为 round8、round9，两轮使用恢复 SP 配置后的相同播放器 APK；第二轮测试包仅增加失败时的线程采集；每台每轮 77 个播放用例，另有 9 个 SP 迁移检查。两轮共604项播放通过、10项能力限制、2项第一轮失败；第二轮无失败。探测均通过，72项SP检查均通过。恢复线上1.7.0的 <code>live_delay_mode</code> 键与“稳定”默认值；旧键优先，v2 单独存在时迁移一次并删除，错误类型与未知值回到稳定。合并 TS 边界包的计数与写出逻辑，并将探测音轨选择改为一次解析。较早轮次是独立定位记录，不混入本次统计。测试使用独立应用，原播放器频道和设置保留。小米6现有nTv已覆盖更新，冷启动CCTV-2显示25/25 fps，保留原有低延迟选择。<a href="../release-readiness/mi6-release-update.json">安装与设置保留证据</a> · <a href="../release-readiness/mi6-release-smoke.png">Release实播画面</a>。</p><p><b>发布评估：ARM32/ARM64 可灰度试用，暂不建议全量发布。</b>本页呈现实测结果，不把设备能力限制计为播放成功。ARM32/ARM64 Release 包已构建，签名与线上1.7.0一致。正式发布前必须用实际发布的三个 ABI 包重新生成更新清单；当前仓库 <code>version-lite.json</code> 的 ARM 校验值与新包不一致。<a href="../release-readiness/assessment.json">评估与包校验值</a> · <a href="../release-readiness/version-lite-arm-candidate.json">ARM 校验值参考（不是完整发布清单）</a>。本地用例采样5.5秒，在线用例采样10秒；本轮不能替代小时级直播稳定性测试。Android 4.4 第一轮有一次1080p50 HEVC HLS清单读取超时；之后的12项交替复测全部通过，但未确定根因，不能宣称已修复。<a href="../hls-debug-matrix/8ba8ceb8/diagnostic-stable-hevc50/results.json">补充复测证据</a>。</p><p>Apple 在线主清单在本次选择了 3840×2160 HEVC/PQ 视频与 AAC 立体声音轨，不是 Atmos 音轨。其前约两秒是黑色片头；KT107 软解仅约 1–2 fps，短时画面断言失败不能单独证明解码器黑屏。<a href="apple-reference/source.json">参考帧来源</a>、<a href="apple-reference/frame-01.png">首秒</a>、<a href="apple-reference/frame-03.png">第三秒</a>与<a href="../hls-debug-matrix/0123456789ABCDEF/diagnostic-apple-probe-reuse/results.json">延长播放出画记录</a>保留供复核；修复后探测另有<a href="../hls-debug-matrix/0123456789ABCDEF/diagnostic-apple-default-audio/results.json">1.5 秒通过记录</a>，该次延长播放也出现停滞，不能算作完整播放通过。</p></div>
<div class="panel"><h2>设备与轮次</h2><div style="overflow:auto"><table><thead><tr><th>设备</th><th>轮次</th><th>完整性</th><th>播放通过</th><th>能力限制</th><th>失败</th><th>探测失败</th><th>低帧率</th><th>本地首帧中位数</th><th>探测中位数</th><th>峰值 PSS</th><th>SP / 默认值</th></tr></thead><tbody id="summary"></tbody></table></div><small>首帧统计为本地基础 56 用例中实际播放成功的行，含自动软解；不能据此推断所有频道加载速度。PSS 仅为采样峰值，不是持续监控峰值。</small></div>
<div class="panel"><select id="device"><option value="">全部设备</option></select><select id="round"><option value="">全部轮次</option></select><select id="state"><option value="">全部结果</option><option value="failed">失败</option><option value="slow">低帧率</option><option value="capabilityLimited">能力限制</option><option value="probe">探测失败</option><option value="played">播放通过</option></select><input id="search" placeholder="搜索频道、格式、解码器"><span id="count"></span></div>
<div class="scroll"><table><thead><tr><th>设备 / 轮次</th><th>视频</th><th>播放 / 探测</th><th>实际 / 源帧率</th><th>扫描 / 色彩</th><th>解码器</th><th>首帧 / 探测</th><th>证据与详细信息</th></tr></thead><tbody id="rows"></tbody></table></div><p><a href="results.json">完整 JSON</a> · <a href="overlay-verification/idempotence.json">原生覆盖脚本重复应用验证</a> · <a href="native-headers/results.json">28 种 SPS 与 ffprobe 对照</a></p></main>
<script>const data=__DATA__;const esc=x=>String(x??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));const fmt=x=>x==null?'—':Math.round(x)+' ms';const fps=x=>x==null?'—':Number(x).toFixed(1);const counts=s=>s.counts||{};
document.querySelector('#summary').innerHTML=data.summaries.map(s=>`<tr><td>${esc(s.device.model)}<br><small>Android ${esc(s.device.release)} / ${esc(s.device.testAbi)}<br>${esc(s.device.serial)}</small></td><td>${s.round}</td><td class="${s.complete?'good':'bad'}">${s.rows}/${s.expected} ${s.complete?'完整':'未完成'}</td><td>${counts(s).played||0}</td><td class="limit">${counts(s).capabilityLimited||0}</td><td class="bad">${s.failures}</td><td class="bad">${s.probeFailures}</td><td class="slow">${s.slow}</td><td>${fmt(s.firstFrameMedianMs)}</td><td>${fmt(s.probeMedianMs)}</td><td>${(s.pssPeakKb/1024).toFixed(1)} MB</td><td>${s.preferencesEvidence?`<a href="../${esc(s.preferencesEvidence)}">${esc(s.preferences.status)} / ${(s.preferences.cases||[]).length}项</a>`:'缺少证据'}<br><small>稳定：${s.liveDelayModes?.stable||0}/74</small></td></tr>`).join('');
for(const [id,values] of [['device',[...new Set(data.rows.map(r=>r.serial))]],['round',data.rounds]]){const el=document.getElementById(id);for(const v of values){const o=document.createElement('option');o.value=v;o.textContent=id==='device'?data.rows.find(r=>r.serial===v).device+' '+v:v;el.append(o)}}
function render(){const d=document.querySelector('#device').value,round=document.querySelector('#round').value,state=document.querySelector('#state').value,q=document.querySelector('#search').value.toLowerCase();const rows=data.rows.filter(r=>(!d||r.serial===d)&&(!round||r.round==round)&&(!state||(state==='slow'?r.slow:state==='probe'?r.probeStatus==='failed':state==='failed'?!['played','capabilityLimited'].includes(r.status):r.status===state))&&(!q||JSON.stringify(r).toLowerCase().includes(q)));document.querySelector('#count').textContent=rows.length+' 项';document.querySelector('#rows').innerHTML=rows.map(r=>`<tr><td>${esc(r.device)}<br>round ${r.round}</td><td>${esc(r.name)}<br><small>${r.width||r.probeResult?.width||'—'}×${r.height||r.probeResult?.height||'—'}</small></td><td class="${r.status==='played'?'good':r.status==='capabilityLimited'?'limit':'bad'}">${esc(r.status)}<br><small>probe ${esc(r.probeStatus)}</small></td><td class="${r.slow?'slow':''}">${fps(r.outputFps)} / ${fps(r.sourceFps)}<br><small>${esc(r.fpsLabel)}</small></td><td>${esc(r.scanType)}<br><small>${esc(r.dynamicRange)} ${esc(r.bitDepth)}bit ${esc(r.transfer)}</small></td><td>${esc(r.decoderType)}<br><small>${esc(r.decoder)}<br>${esc(r.colorOutput)}</small></td><td>${fmt(r.firstFrameMs||r.rejectionMs)}<br><small>probe ${fmt(r.probeMs)}</small></td><td>${r.capture?`<a href="../${esc(r.capture)}" target="_blank">实际画面</a> · `:''}${r.log?`<a href="../${esc(r.log)}" target="_blank">日志</a> · `:''}<a href="../${esc(r.evidence)}" target="_blank">JSON</a><details><summary>完整字段 / 错误</summary><pre>${esc(JSON.stringify(r,null,2))}</pre></details></td></tr>`).join('')};for(const id of ['device','round','state','search'])document.getElementById(id).addEventListener('input',render);render();</script></html>'''

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--rounds',nargs='+',type=int,default=[8,9]);args=parser.parse_args()
    data=collect(args.rounds)
    (OUT/'results.json').write_text(json.dumps(data,ensure_ascii=False,indent=2),encoding='utf-8')
    (OUT/'report.html').write_text(TEMPLATE.replace('__DATA__',json.dumps(data,ensure_ascii=False).replace('</','<\\/')),encoding='utf-8')
    print(json.dumps(data['summaries'],ensure_ascii=True,indent=2))
