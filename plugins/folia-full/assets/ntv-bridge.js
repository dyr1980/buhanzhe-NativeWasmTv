/* Native transport adaptation for the unmodified, bundled Folia web application. */
(function(){
  'use strict';
  var upstream='__NTV_UPSTREAM__', route='/__ntv_proxy/__NTV_TOKEN__?url=';
  function resolve(value){var url=new URL(String(value),location.href);if(url.protocol!=='http:'&&url.protocol!=='https:')return null;
    if(url.origin===location.origin){if(url.pathname.indexOf('/__ntv_proxy/')===0||url.pathname.indexOf('/assets/')===0||url.pathname==='/runtime-config.js'||url.pathname==='/ntv-bridge.js')return null;var path=url.pathname; if(path.indexOf('/kugou-api')===0)return 'https://kugouapi.aikun-bili.top'+path.slice(10)+url.search; if(path.indexOf('/qq-api')===0)return 'https://qqmusicapi.aikun-bili.top'+path.slice(7)+url.search;return upstream+path+url.search;}
    return url.href;
  }
  function proxy(url){var target=resolve(url);return target?route+encodeURIComponent(target):url;}
  function unproxy(url){try{var value=new URL(url,location.href);if(value.origin===location.origin&&value.pathname.indexOf('/__ntv_proxy/')===0)return value.searchParams.get('url')||url;}catch(e){}return url;}
  var originalFetch=window.fetch.bind(window);
  window.fetch=async function(input,options){
    var request=new Request(input,options),target=resolve(request.url);
    if(!target)return originalFetch(request);
    var config={method:request.method,headers:request.headers,credentials:'include',signal:request.signal,redirect:request.redirect};
    if(request.method!=='GET'&&request.method!=='HEAD')config.body=await request.arrayBuffer();
    var response=await originalFetch(route+encodeURIComponent(target),config);
    try{Object.defineProperty(response,'url',{value:target});}catch(e){}
    return response;
  };
  var open=XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open=function(method,url){var args=Array.prototype.slice.call(arguments);args[1]=proxy(url);return open.apply(this,args);};
  var src=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'src');
  if(src&&src.set)Object.defineProperty(HTMLMediaElement.prototype,'src',{configurable:true,enumerable:src.enumerable,get:function(){return unproxy(src.get.call(this));},set:function(url){src.set.call(this,proxy(url));}});
  var current=Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype,'currentSrc');
  if(current&&current.configurable)Object.defineProperty(HTMLMediaElement.prototype,'currentSrc',{configurable:true,enumerable:current.enumerable,get:function(){return unproxy(current.get.call(this));}});
  var getAttribute=Element.prototype.getAttribute;
  Element.prototype.getAttribute=function(name){var value=getAttribute.apply(this,arguments);return this instanceof HTMLMediaElement&&String(name).toLowerCase()==='src'&&value!==null?unproxy(value):value;};
  var attribute=Element.prototype.setAttribute;
  Element.prototype.setAttribute=function(name,value){if(this instanceof HTMLMediaElement&&String(name).toLowerCase()==='src')value=proxy(value);return attribute.call(this,name,value);};
  document.addEventListener('click',function(event){
    var a=event.target&&event.target.closest&&event.target.closest('a[download]');
    if(!a||!window.NTVFoliaExport||(!a.href.startsWith('blob:')&&!a.href.startsWith('data:')))return;
    event.preventDefault();event.stopPropagation();
    originalFetch(a.href).then(function(response){return response.blob();}).then(function(blob){
      var reader=new FileReader();reader.onload=function(){
        var message=window.NTVFoliaExport.save(a.download||'folia-export',String(reader.result).split(',')[1],'__NTV_TOKEN__');
        var notice=document.createElement('div');notice.textContent=message;notice.setAttribute('role','status');
        notice.style.cssText='position:fixed;bottom:24px;left:24px;right:24px;padding:16px;background:#17202e;color:white;z-index:2147483647;border-radius:12px;word-break:break-all';
        document.body.appendChild(notice);setTimeout(function(){notice.remove();},7000);
      };reader.readAsDataURL(blob);
    }).catch(function(error){console.error('[nTv export]',error);});
  },true);
  // The packaged build is immutable. Never let a worker replace it with a cached web deployment.
  if(navigator.serviceWorker){navigator.serviceWorker.getRegistrations().then(function(all){all.forEach(function(reg){var worker=reg.active||reg.installing||reg.waiting;if(worker&&worker.scriptURL.indexOf('/folia-cover-sw.js')<0)reg.unregister();});});}
  var mediaActions={};
  if(navigator.mediaSession&&navigator.mediaSession.setActionHandler){
    var register=navigator.mediaSession.setActionHandler.bind(navigator.mediaSession);
    navigator.mediaSession.setActionHandler=function(action,handler){mediaActions[action]=handler;return register(action,handler);};
  }
  window.__NTV_FOLIA_FULL__={packaged:true,serviceOrigin:upstream,media:function(action){
    var audio=Array.from(document.querySelectorAll('audio')).find(function(a){return !!a.currentSrc;});
    if(action==='toggle')action=audio&&!audio.paused?'pause':'play';
    if(mediaActions[action]){mediaActions[action]({action:action});return;}
    if(!audio)return;
    if(action==='play')audio.play().catch(function(){});else if(action==='pause'||action==='stop')audio.pause();
  }};
})();
