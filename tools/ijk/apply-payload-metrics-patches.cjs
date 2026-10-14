const fs = require('fs');
const path = require('path');

function apply(root) {
  const file = path.join(root, 'ijkmedia/ijkplayer/ff_ffplay.c');
  let source = fs.readFileSync(file, 'utf8');
  if (source.includes('case 22013: /* encoded video payload')) return;
  const anchor = '        case FFP_PROP_INT64_SELECTED_VIDEO_STREAM:';
  if (!source.includes(anchor)) throw Error('Missing IJK long-property switch');
  source = source.replace(anchor, `        case 22013: /* encoded video payload, excluding queue-node memory */
        case 22014: { /* encoded audio payload; keep upstream memory counters intact */
            if (!ffp || !ffp->is) return default_value;
            FFTrackCacheStatistic *cache = id == 22013
                ? &ffp->stat.video_cache : &ffp->stat.audio_cache;
            int64_t payload = cache->bytes - (int64_t)cache->packets * (int64_t)sizeof(MyAVPacketList);
            return payload > 0 ? payload : 0;
        }
${anchor}`);
  fs.writeFileSync(file, source);
}
module.exports = apply;
if (require.main === module) apply(path.resolve(process.argv[2] || '.codex-tmp/ijkplayer-0.8.8'));
