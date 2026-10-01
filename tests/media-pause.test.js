const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const source=fs.readFileSync('app/src/main/java/xiao/bu/tv/WebSourceView.java','utf8');
const method=source.slice(source.indexOf('void setMultimediaPaused'),source.indexOf('private void ensureWebView'));
const scripts=[...method.matchAll(/(?:\?|:)\s*("(?:[^"\\]|\\.)*");?/g)].map(m=>JSON.parse(m[1]));
let tick;const make=(muted,paused)=>({muted,paused,pause(){this.paused=true;},play(){this.paused=false;return {catch(){}};}});
const playing=make(false,false),paused=make(true,true),nested=make(false,false);
const child={querySelectorAll:s=>s==='iframe'?[]:[nested]};
const elements=[playing,paused];
const c={window:{},document:{querySelectorAll:s=>s==='iframe'?[{contentDocument:child}]:elements},setInterval:f=>(tick=f,1),clearInterval:()=>{tick=null;}};
vm.runInNewContext(scripts[0],c);assert.equal(playing.muted,true);assert.equal(nested.paused,true);
const added=make(false,false);elements.push(added);tick();assert.equal(added.muted,true);
vm.runInNewContext(scripts[1],c);assert.equal(playing.muted,false);assert.equal(playing.paused,false);assert.equal(paused.muted,true);assert.equal(paused.paused,true);assert.equal(nested.paused,false);assert.equal(added.paused,false);assert.equal(tick,null);
console.log('PASS page media state, same-origin frames, new media, and restoration');

// A player created after sniffing must not start or acquire audio focus in the hidden page.
function Media(){this.muted=false;this.paused=true;this.starts=0;}
Media.prototype.play=function(){this.starts++;this.paused=false;};
Media.prototype.pause=function(){this.paused=true;};
const original=Media.prototype.play;
const lateContext={window:{HTMLMediaElement:Media},HTMLMediaElement:Media,
 document:{querySelectorAll:()=>[]},setInterval:()=>1,clearInterval:()=>{}};
vm.runInNewContext(scripts[0],lateContext);
const late=new Media();late.play();late.play();
assert.equal(late.starts,0);assert.equal(late.muted,true);
vm.runInNewContext(scripts[1],lateContext);
assert.equal(Media.prototype.play,original);assert.equal(late.starts,1);
assert.equal(late.muted,false);assert.equal(late.paused,false);
console.log('PASS hidden late autoplay blocked without starting audio, original play restored once');

const queued=new Media();
lateContext.document.querySelectorAll=s=>s==='iframe'?[]:[queued];
vm.runInNewContext(scripts[0],lateContext);queued.play();
assert.equal(queued.starts,0);
vm.runInNewContext(scripts[1],lateContext);assert.equal(queued.starts,1);
console.log('PASS pending autoplay intent retained for an already-paused element');

let writes=0,stops=0,ticks;
const idle={paused:false,_muted:false,get muted(){return this._muted;},set muted(v){writes++;this._muted=v;},
 pause(){stops++;this.paused=true;},play(){this.paused=false;}};
const quiet={window:{},document:{querySelectorAll:s=>s==='iframe'?[]:[idle]},setInterval:f=>(ticks=f,1),clearInterval(){}};
vm.runInNewContext(scripts[0],quiet);
for(let i=0;i<1000;i++)ticks();
assert.equal(writes,1);assert.equal(stops,1,'Already stopped players must not receive repeated pause calls');
idle._muted=false;idle.paused=false;ticks();
assert.equal(writes,2);assert.equal(stops,2,'A page restarting/unmuting still gets stopped at the same interval');
vm.runInNewContext(scripts[1],quiet);assert.equal(idle.paused,false);assert.equal(idle.muted,false);
console.log('PASS 1000 idle pause checks do zero redundant media writes, resumed media still suppressed/restored');
