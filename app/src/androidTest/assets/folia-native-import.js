// Exercise the pinned upstream's real importFolder function with the files chosen natively.
window.__upstreamImport='pending';
(async function(){
  const main=new URL(document.querySelector('[data-ntv-entry]').getAttribute('data-ntv-entry'),location.href).href;
  const entry=await (await fetch(main)).text();
  const path=entry.match(/assets\/bootstrap-[\w-]+\.js/)[0];
  const text=await (await fetch(new URL(path,location.href))).text();
  const workerOwner=entry.match(/assets\/noLyricLines-[\w-]+\.js/)[0];
  const workerCode=await (await fetch(new URL(workerOwner,location.href))).text();
  const workerPath=workerCode.match(/assets\/metadataParser\.worker-[\w-]+\.js/)[0];
  const worker=new Worker(new URL(workerPath,location.href).href);
  try {
    const file=await (await window.__restoredDirectory.getFileHandle('NativeSelected.wav')).getFile();
    const metadata=await new Promise((resolve,reject)=>{
      const timer=setTimeout(()=>reject(Error('Metadata worker timed out')),10000);
      worker.onerror=e=>{clearTimeout(timer);reject(Error(e.message));};
      worker.onmessage=e=>{clearTimeout(timer);e.data.type==='result'?resolve(e.data.data):reject(Error(e.data.message));};
      worker.postMessage({type:'parse-metadata',file,includeCover:false,requestId:'ntv-fixture'});
    });
    if(!(metadata.duration>0))throw Error('Metadata worker did not parse WAV duration');
  } finally {worker.terminate();}
  // Accept native async and the Chrome 68 generator transform of the same upstream callback.
  const local=text.match(/importFolder:[\s\S]{0,150}?(?:await|yield)\s+([$\w]+)\(\)\)\.length/)[1];
  const name=text.match(new RegExp('(?:[,{])([$\\w]+):'+local.replace(/\$/g,'\\$')+'(?:[,}])'))[1];
  const module=await System.import(new URL(path,location.href).href);
  const original=window.showDirectoryPicker;
  try{
    window.showDirectoryPicker=async()=>window.__restoredDirectory;
    const songs=await module[name]();
    if(songs.length!==2)throw Error('Expected 2 imported songs, got '+songs.length);
    if(!songs.some(s=>s.hasLocalLyrics&&s.localLyricsContent.includes('Native selection fixture')))throw Error('Sidecar lyrics were not imported');
    for(const song of songs)if((await song.fileHandle.getFile()).size===0)throw Error('Empty imported audio');
    const url=URL.createObjectURL(await songs[0].fileHandle.getFile());
    const audio=new Audio(url);
    try{
      await audio.play();
      await new Promise(resolve=>setTimeout(resolve,500));
      if(audio.paused||audio.currentTime<=0)throw Error('Selected local audio did not play');
    }finally{audio.pause();URL.revokeObjectURL(url);}
    window.__upstreamImport=JSON.stringify({count:songs.length,lyrics:true,readable:true,localPlayback:true});
  }finally{window.showDirectoryPicker=original;}
})().catch(e=>window.__upstreamImport=String(e.stack||e));
