/* Read-only, cloneable file handles for Android's native multi-file chooser. */
(function(){
  'use strict';
  if(typeof window==='undefined'||typeof window.NTVFoliaExport==='undefined')return;
  var marker='ntv-selected-files-v1',pending=false,cancelPending=null;
  if(!Symbol.asyncIterator)Object.defineProperty(Symbol,'asyncIterator',{value:Symbol('Symbol.asyncIterator')});
  function iterate(children,kind){
    var index=0,iterator={next:function(){if(index>=children.length)return Promise.resolve({done:true});var child=revive(children[index++]);return Promise.resolve({done:false,value:kind==='entries'?[child.name,child]:kind==='keys'?child.name:child});}};
    iterator[Symbol.asyncIterator]=function(){return this;};return iterator;
  }
  function permission(options){return Promise.resolve(options&&options.mode==='readwrite'?'denied':'granted');}
  var common={queryPermission:permission,requestPermission:permission,isSameEntry:function(other){return Promise.resolve(!!other&&other.__ntvHandle===marker&&other.kind===this.kind&&other.identity===this.identity);}};
  var fileProto=Object.assign(Object.create(null),common,{getFile:function(){return Promise.resolve(this.file);}});
  var dirProto=Object.assign(Object.create(null),common,{
    values:function(){return iterate(this.children,'values');},
    entries:function(){return iterate(this.children,'entries');},
    keys:function(){return iterate(this.children,'keys');},
    getFileHandle:async function(name){var child=this.children.find(function(c){return c.name===name&&c.kind==='file';});if(!child)throw new DOMException('File not found','NotFoundError');return revive(child);},
    getDirectoryHandle:async function(name){var child=this.children.find(function(c){return c.name===name&&c.kind==='directory';});if(!child)throw new DOMException('Directory not found','NotFoundError');return revive(child);}
  });
  dirProto[Symbol.asyncIterator]=dirProto.entries;
  function revive(value,seen){
    if(!value||typeof value!=='object'||value instanceof Blob||value instanceof ArrayBuffer)return value;
    seen=seen||new WeakSet();if(seen.has(value))return value;seen.add(value);
    if(value.__ntvHandle===marker){Object.setPrototypeOf(value,value.kind==='file'?fileProto:dirProto);return value;}
    if(Array.isArray(value)||Object.getPrototypeOf(value)===Object.prototype)Object.keys(value).forEach(function(key){revive(value[key],seen);});
    return value;
  }
  // IndexedDB deliberately removes custom prototypes; restore only our own handle records.
  function reviveGetter(proto,key){var d=Object.getOwnPropertyDescriptor(proto,key);if(!d||!d.get||!d.configurable)return;Object.defineProperty(proto,key,{configurable:d.configurable,enumerable:d.enumerable,get:function(){return revive(d.get.call(this));}});}
  reviveGetter(IDBRequest.prototype,'result');
  if(window.IDBCursorWithValue)reviveGetter(IDBCursorWithValue.prototype,'value');
  function directory(files){
    var identity=Array.from(files).map(function(f){return [f.webkitRelativePath||f.name,f.size,f.lastModified].join(':');}).sort().join('|');
    var root={__ntvHandle:marker,kind:'directory',name:'Local Import',identity:identity,children:[]};
    Array.from(files).forEach(function(file){
      var parts=(file.webkitRelativePath||file.name).split('/').filter(function(p){return p&&p!=='.'&&p!=='..';}),dir=root;
      for(var i=0;i<parts.length-1;i++){var next=dir.children.find(function(c){return c.kind==='directory'&&c.name===parts[i];});if(!next){next={__ntvHandle:marker,kind:'directory',name:parts[i],identity:identity+'/'+parts.slice(0,i+1).join('/'),children:[]};dir.children.push(next);}dir=next;}
      dir.children.push({__ntvHandle:marker,kind:'file',name:file.name,identity:identity+'/'+parts.join('/'),file:file});
    });
    return revive(root);
  }
  function choose(){
    if(pending)return Promise.reject(new DOMException('A picker is already open','InvalidStateError'));
    pending=true;
    return new Promise(function(resolve,reject){
      var input=document.createElement('input');input.type='file';input.multiple=true;
      // Native folder marker; ordinary playlist/file inputs retain their normal chooser.
      input.accept='application/x-ntv-folia-directory';input.style.cssText='position:fixed;left:-10000px;width:1px;height:1px';document.body.appendChild(input);
      function finish(error){if(!pending)return;pending=false;cancelPending=null;input.remove();error?reject(error):resolve(directory(input.files));}
      cancelPending=function(){finish(new DOMException('Selection cancelled','AbortError'));};
      input.addEventListener('change',function(){if(input.files&&input.files.length)finish();else finish(new DOMException('Selection cancelled','AbortError'));},{once:true});
      input.addEventListener('cancel',function(){finish(new DOMException('Selection cancelled','AbortError'));},{once:true});
      try{input.click();}catch(error){finish(error);}
    });
  }
  window.__NTV_FOLIA_FILES__={supported:true,choose:choose,revive:revive,cancel:function(){if(cancelPending)cancelPending();}};
  if(typeof window.showDirectoryPicker!=='function')window.showDirectoryPicker=choose;
})();
