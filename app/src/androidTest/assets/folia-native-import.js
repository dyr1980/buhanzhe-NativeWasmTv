// Exercise the pinned upstream's real importFolder function with the files chosen natively.
window.__upstreamImport='pending';
(async function(){
  const main=document.querySelector('script[type="module"][src]').src;
  const entry=await (await fetch(main)).text();
  const path=entry.match(/assets\/bootstrap-[\w-]+\.js/)[0];
  const text=await (await fetch(new URL(path,location.href))).text();
  const local=text.match(/importFolder:async\(\)=>\(await ([$\w]+)\(\)\)\.length/)[1];
  const name=text.match(new RegExp('(?:^|[,\\s])'+local.replace(/\$/g,'\\$')+' as ([$\\w]+)'))[1];
  const module=await import(new URL(path,location.href).href);
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
})().catch(e=>window.__upstreamImport=String(e));
