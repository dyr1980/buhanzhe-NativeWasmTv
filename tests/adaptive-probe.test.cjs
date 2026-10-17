const fs=require('fs'),os=require('os'),path=require('path'),assert=require('assert');
const apply=require('../tools/ijk/apply-adaptive-probe-patches.cjs');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'ntv-adaptive-probe-'));
try {
 const dir=path.join(root,'libavformat');fs.mkdirSync(dir);
 const file=path.join(dir,'utils.c');
 fs.writeFileSync(file,'    int eof_reached = 0;\n            if (st->disposition & AV_DISPOSITION_ATTACHED_PIC)\n                fps_analyze_framecount = 0;');
 apply(root);const once=fs.readFileSync(file,'utf8');apply(root);
 assert.strictEqual(fs.readFileSync(file,'utf8'),once);
 assert.strictEqual(once.split('ntv_adaptive_probe =').length,2);
 assert(once.includes('ic->fps_probe_size < 0 && st->parser'));
 assert(once.includes('st->internal->avctx->framerate'));
 assert.strictEqual(fs.readFileSync(path.join(dir,'ntv_adaptive_probe.h'),'utf8'),
  fs.readFileSync(path.join(__dirname,'../tools/ijk/ntv_adaptive_probe.h'),'utf8'));
 fs.writeFileSync(file,'unsupported revision');assert.throws(()=>apply(root),/Unexpected FFmpeg FPS gate/);
 console.log('PASS adaptive probe overlay: idempotent, explicit FPS override preserved, unknown upstream rejected');
} finally {fs.rmSync(root,{recursive:true,force:true});}
