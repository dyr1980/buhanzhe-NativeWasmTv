var e=(e,t)=>()=>(t||(e((t={exports:{}}).exports,t),e=null),t.exports),t=/* @__PURE__ */ e(((e,t)=>{var n=function(e){return e&&e.Math===Math&&e};t.exports=n(typeof globalThis==`object`&&globalThis)||n(typeof window==`object`&&window)||n(typeof self==`object`&&self)||n(typeof global==`object`&&global)||n(typeof e==`object`&&e)||(function(){return this})()||Function(`return this`)()})),n=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e){try{return!!e()}catch{return!0}}})),r=/* @__PURE__ */ e(((e,t)=>{t.exports=!n()(function(){return Object.defineProperty({},1,{get:function(){return 7}})[1]!==7})})),i=/* @__PURE__ */ e(((e,t)=>{t.exports=!n()(function(){var e=function(){}.bind();return typeof e!=`function`||e.hasOwnProperty(`prototype`)})})),a=/* @__PURE__ */ e(((e,t)=>{var n=i(),r=Function.prototype.call;t.exports=n?r.bind(r):function(){return r.apply(r,arguments)}})),o=/* @__PURE__ */ e((e=>{var t={}.propertyIsEnumerable,n=Object.getOwnPropertyDescriptor;e.f=n&&!t.call({1:2},1)?function(e){var t=n(this,e);return!!t&&t.enumerable}:t})),s=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e,t){return{enumerable:!(e&1),configurable:!(e&2),writable:!(e&4),value:t}}})),c=/* @__PURE__ */ e(((e,t)=>{var n=i(),r=Function.prototype,a=r.call,o=n&&r.bind.bind(a,a);t.exports=n?o:function(e){return function(){return a.apply(e,arguments)}}})),l=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=n({}.toString),i=n(``.slice);t.exports=function(e){return i(r(e),8,-1)}})),u=/* @__PURE__ */ e(((e,t)=>{var r=c(),i=n(),a=l(),o=Object,s=r(``.split);t.exports=i(function(){return!o(`z`).propertyIsEnumerable(0)})?function(e){return a(e)===`String`?s(e,``):o(e)}:o})),d=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e){return e==null}})),f=/* @__PURE__ */ e(((e,t)=>{var n=d(),r=TypeError;t.exports=function(e){if(n(e))throw new r(`Can't call method on `+e);return e}})),p=/* @__PURE__ */ e(((e,t)=>{var n=u(),r=f();t.exports=function(e){return n(r(e))}})),m=/* @__PURE__ */ e(((e,t)=>{var n=typeof document==`object`&&document.all;t.exports=n===void 0&&n!==void 0?function(e){return typeof e==`function`||e===n}:function(e){return typeof e==`function`}})),h=/* @__PURE__ */ e(((e,t)=>{var n=m();t.exports=function(e){return typeof e==`object`?e!==null:n(e)}})),g=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=m(),a=function(e){return i(e)?e:void 0};n.exports=function(e,t){return arguments.length<2?a(r[e]):r[e]&&r[e][t]}})),_=/* @__PURE__ */ e(((e,t)=>{t.exports=c()({}.isPrototypeOf)})),v=/* @__PURE__ */ e(((e,n)=>{var r=t().navigator,i=r&&r.userAgent;n.exports=i?String(i):``})),y=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=v(),a=r.process,o=r.Deno,s=a&&a.versions||o&&o.version,c=s&&s.v8,l,u;c&&(l=c.split(`.`),u=l[0]>0&&l[0]<4?1:+(l[0]+l[1])),!u&&i&&(l=i.match(/Edge\/(\d+)/),(!l||l[1]>=74)&&(l=i.match(/Chrome\/(\d+)/),l&&(u=+l[1]))),n.exports=u})),b=/* @__PURE__ */ e(((e,r)=>{var i=y(),a=n(),o=t().String;r.exports=!!Object.getOwnPropertySymbols&&!a(function(){var e=Symbol(`symbol detection`);return!o(e)||!(Object(e)instanceof Symbol)||!Symbol.sham&&i&&i<41})})),x=/* @__PURE__ */ e(((e,t)=>{t.exports=b()&&!Symbol.sham&&typeof Symbol.iterator==`symbol`})),S=/* @__PURE__ */ e(((e,t)=>{var n=g(),r=m(),i=_(),a=x(),o=Object;t.exports=a?function(e){return typeof e==`symbol`}:function(e){var t=n(`Symbol`);return r(t)&&i(t.prototype,o(e))}})),C=/* @__PURE__ */ e(((e,t)=>{var n=String;t.exports=function(e){try{return n(e)}catch{return`Object`}}})),w=/* @__PURE__ */ e(((e,t)=>{var n=m(),r=C(),i=TypeError;t.exports=function(e){if(n(e))return e;throw new i(r(e)+` is not a function`)}})),T=/* @__PURE__ */ e(((e,t)=>{var n=w(),r=d();t.exports=function(e,t){var i=e[t];return r(i)?void 0:n(i)}})),E=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=m(),i=h(),o=TypeError;t.exports=function(e,t){var a,s;if(t===`string`&&r(a=e.toString)&&!i(s=n(a,e))||r(a=e.valueOf)&&!i(s=n(a,e))||t!==`string`&&r(a=e.toString)&&!i(s=n(a,e)))return s;throw new o(`Can't convert object to primitive value`)}})),D=/* @__PURE__ */ e(((e,t)=>{t.exports=!1})),O=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=Object.defineProperty;n.exports=function(e,t){try{i(r,e,{value:t,configurable:!0,writable:!0})}catch{r[e]=t}return t}})),k=/* @__PURE__ */ e(((e,n)=>{var r=D(),i=t(),a=O(),o=`__core-js_shared__`,s=n.exports=i[o]||a(o,{});(s.versions||=[]).push({version:`3.51.0`,mode:r?`pure`:`global`,copyright:`© 2013–2025 Denis Pushkarev (zloirock.ru), 2025–2026 CoreJS Company (core-js.io). All rights reserved.`,license:`https://github.com/zloirock/core-js/blob/v3.51.0/LICENSE`,source:`https://github.com/zloirock/core-js`})})),A=/* @__PURE__ */ e(((e,t)=>{var n=k(),r=Object.create||Object;t.exports=function(e,t){return n[e]||(n[e]=t||r(null))}})),j=/* @__PURE__ */ e(((e,t)=>{var n=f(),r=Object;t.exports=function(e){return r(n(e))}})),M=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=j(),i=n({}.hasOwnProperty);t.exports=Object.hasOwn||function(e,t){return i(r(e),t)}})),N=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=0,i=Math.random(),a=n(1.1.toString);t.exports=function(e){return`Symbol(`+(e===void 0?``:e)+`)_`+a(++r+i,36)}})),P=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=A(),a=M(),o=N(),s=b(),c=x(),l=r.Symbol,u=i(`wks`),d=c?l.for||l:l&&l.withoutSetter||o;n.exports=function(e){return a(u,e)||(u[e]=s&&a(l,e)?l[e]:d(`Symbol.`+e)),u[e]}})),ee=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=h(),i=S(),o=T(),s=E(),c=P(),l=TypeError,u=c(`toPrimitive`);t.exports=function(e,t){if(!r(e)||i(e))return e;var a=o(e,u),c;if(a){if(t===void 0&&(t=`default`),c=n(a,e,t),!r(c)||i(c))return c;throw new l(`Can't convert object to primitive value`)}return t===void 0&&(t=`number`),s(e,t)}})),F=/* @__PURE__ */ e(((e,t)=>{var n=ee(),r=S();t.exports=function(e){var t=n(e,`string`);return r(t)?t:t+``}})),I=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=h(),a=r.document,o=i(a)&&i(a.createElement);n.exports=function(e){return o?a.createElement(e):{}}})),L=/* @__PURE__ */ e(((e,t)=>{var i=r(),a=n(),o=I();t.exports=!i&&!a(function(){return Object.defineProperty(o(`div`),"a",{get:function(){return 7}}).a!==7})})),R=/* @__PURE__ */ e((e=>{var t=r(),n=a(),i=o(),c=s(),l=p(),u=F(),d=M(),f=L(),m=Object.getOwnPropertyDescriptor;e.f=t?m:function(e,t){if(e=l(e),t=u(t),f)try{return m(e,t)}catch{}if(d(e,t))return c(!n(i.f,e,t),e[t])}})),z=/* @__PURE__ */ e(((e,t)=>{var i=r(),a=n();t.exports=i&&a(function(){return Object.defineProperty(function(){},"prototype",{value:42,writable:!1}).prototype!==42})})),B=/* @__PURE__ */ e(((e,t)=>{var n=h(),r=String,i=TypeError;t.exports=function(e){if(n(e))return e;throw new i(r(e)+` is not an object`)}})),V=/* @__PURE__ */ e((e=>{var t=r(),n=L(),i=z(),a=B(),o=F(),s=TypeError,c=Object.defineProperty,l=Object.getOwnPropertyDescriptor,u=`enumerable`,d=`configurable`,f=`writable`;e.f=t?i?function(e,t,n){if(a(e),t=o(t),a(n),typeof e==`function`&&t===`prototype`&&`value`in n&&f in n&&!n[f]){var r=l(e,t);r&&r[f]&&(e[t]=n.value,n={configurable:d in n?n[d]:r[d],enumerable:u in n?n[u]:r[u],writable:!1})}return c(e,t,n)}:c:function(e,t,r){if(a(e),t=o(t),a(r),n)try{return c(e,t,r)}catch{}if(`get`in r||`set`in r)throw new s(`Accessors not supported`);return`value`in r&&(e[t]=r.value),e}})),te=/* @__PURE__ */ e(((e,t)=>{var n=r(),i=V(),a=s();t.exports=n?function(e,t,n){return i.f(e,t,a(1,n))}:function(e,t,n){return e[t]=n,e}})),ne=/* @__PURE__ */ e(((e,t)=>{var n=r(),i=M(),a=Function.prototype,o=n&&Object.getOwnPropertyDescriptor,s=i(a,`name`);t.exports={EXISTS:s,PROPER:s&&function(){}.name===`something`,CONFIGURABLE:s&&(!n||n&&o(a,`name`).configurable)}})),re=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=m(),i=k(),a=n(Function.toString);r(i.inspectSource)||(i.inspectSource=function(e){return a(e)}),t.exports=i.inspectSource})),ie=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=m(),a=r.WeakMap;n.exports=i(a)&&/native code/.test(String(a))})),ae=/* @__PURE__ */ e(((e,t)=>{var n=A(),r=N(),i=n(`keys`);t.exports=function(e){return i[e]||(i[e]=r(e))}})),oe=/* @__PURE__ */ e(((e,t)=>{t.exports={}})),se=/* @__PURE__ */ e(((e,n)=>{var r=ie(),i=t(),a=h(),o=te(),s=M(),c=k(),l=ae(),u=oe(),d=`Object already initialized`,f=i.TypeError,p=i.WeakMap,m,g,_,v=function(e){return _(e)?g(e):m(e,{})},y=function(e){return function(t){var n;if(!a(t)||(n=g(t)).type!==e)throw new f(`Incompatible receiver, `+e+` required`);return n}};if(r||c.state){var b=c.state||=new p;b.get=b.get,b.has=b.has,b.set=b.set,m=function(e,t){if(b.has(e))throw new f(d);return t.facade=e,b.set(e,t),t},g=function(e){return b.get(e)||{}},_=function(e){return b.has(e)}}else{var x=l(`state`);u[x]=!0,m=function(e,t){if(s(e,x))throw new f(d);return t.facade=e,o(e,x,t),t},g=function(e){return s(e,x)?e[x]:{}},_=function(e){return s(e,x)}}n.exports={set:m,get:g,has:_,enforce:v,getterFor:y}})),ce=/* @__PURE__ */ e(((e,t)=>{var i=c(),a=n(),o=m(),s=M(),l=r(),u=ne().CONFIGURABLE,d=re(),f=se(),p=f.enforce,h=f.get,g=String,_=Object.defineProperty,v=i(``.slice),y=i(``.replace),b=i([].join),x=l&&!a(function(){return _(function(){},`length`,{value:8}).length!==8}),S=String(String).split(`String`),C=t.exports=function(e,t,n){v(g(t),0,7)===`Symbol(`&&(t=`[`+y(g(t),/^Symbol\(([^)]*)\).*$/,`$1`)+`]`),n&&n.getter&&(t=`get `+t),n&&n.setter&&(t=`set `+t),(!s(e,`name`)||u&&e.name!==t)&&(l?_(e,`name`,{value:t,configurable:!0}):e.name=t),x&&n&&s(n,`arity`)&&e.length!==n.arity&&_(e,`length`,{value:n.arity});try{n&&s(n,`constructor`)&&n.constructor?l&&_(e,`prototype`,{writable:!1}):e.prototype&&=void 0}catch{}var r=p(e);return s(r,`source`)||(r.source=b(S,typeof t==`string`?t:``)),e};Function.prototype.toString=C(function(){return o(this)&&h(this).source||d(this)},`toString`)})),le=/* @__PURE__ */ e(((e,t)=>{var n=m(),r=V(),i=ce(),a=O();t.exports=function(e,t,o,s){s||={};var c=s.enumerable,l=s.name===void 0?t:s.name;if(n(o)&&i(o,l,s),s.global)c?e[t]=o:a(t,o);else{try{s.unsafe?e[t]&&(c=!0):delete e[t]}catch{}c?e[t]=o:r.f(e,t,{value:o,enumerable:!1,configurable:!s.nonConfigurable,writable:!s.nonWritable})}return e}})),ue=/* @__PURE__ */ e(((e,t)=>{var n=Math.ceil,r=Math.floor;t.exports=Math.trunc||function(e){var t=+e;return(t>0?r:n)(t)}})),de=/* @__PURE__ */ e(((e,t)=>{var n=ue();t.exports=function(e){var t=+e;return t!==t||t===0?0:n(t)}})),fe=/* @__PURE__ */ e(((e,t)=>{var n=de(),r=Math.max,i=Math.min;t.exports=function(e,t){var a=n(e);return a<0?r(a+t,0):i(a,t)}})),pe=/* @__PURE__ */ e(((e,t)=>{var n=de(),r=Math.min;t.exports=function(e){var t=n(e);return t>0?r(t,9007199254740991):0}})),H=/* @__PURE__ */ e(((e,t)=>{var n=pe();t.exports=function(e){return n(e.length)}})),U=/* @__PURE__ */ e(((e,t)=>{var n=p(),r=fe(),i=H(),a=function(e){return function(t,a,o){var s=n(t),c=i(s);if(c===0)return!e&&-1;var l=r(o,c),u;if(e&&a!==a){for(;c>l;)if(u=s[l++],u!==u)return!0}else for(;c>l;l++)if((e||l in s)&&s[l]===a)return e||l||0;return!e&&-1}};t.exports={includes:a(!0),indexOf:a(!1)}})),me=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=M(),i=p(),a=U().indexOf,o=oe(),s=n([].push);t.exports=function(e,t){var n=i(e),c=0,l=[],u;for(u in n)!r(o,u)&&r(n,u)&&s(l,u);for(;t.length>c;)r(n,u=t[c++])&&(~a(l,u)||s(l,u));return l}})),W=/* @__PURE__ */ e(((e,t)=>{t.exports=[`constructor`,`hasOwnProperty`,`isPrototypeOf`,`propertyIsEnumerable`,`toLocaleString`,`toString`,`valueOf`]})),G=/* @__PURE__ */ e((e=>{var t=me(),n=W().concat(`length`,`prototype`);e.f=Object.getOwnPropertyNames||function(e){return t(e,n)}})),K=/* @__PURE__ */ e((e=>{e.f=Object.getOwnPropertySymbols})),he=/* @__PURE__ */ e(((e,t)=>{var n=g(),r=c(),i=G(),a=K(),o=B(),s=r([].concat);t.exports=n(`Reflect`,`ownKeys`)||function(e){var t=i.f(o(e)),n=a.f;return n?s(t,n(e)):t}})),ge=/* @__PURE__ */ e(((e,t)=>{var n=M(),r=he(),i=R(),a=V();t.exports=function(e,t,o){for(var s=r(t),c=a.f,l=i.f,u=0;u<s.length;u++){var d=s[u];!n(e,d)&&!(o&&n(o,d))&&c(e,d,l(t,d))}}})),q=/* @__PURE__ */ e(((e,t)=>{var r=n(),i=m(),a=/#|\.prototype\./,o=function(e,t){var n=c[s(e)];return n===u?!0:n===l?!1:i(t)?r(t):!!t},s=o.normalize=function(e){return String(e).replace(a,`.`).toLowerCase()},c=o.data={},l=o.NATIVE=`N`,u=o.POLYFILL=`P`;t.exports=o})),J=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=R().f,a=te(),o=le(),s=O(),c=ge(),l=q();n.exports=function(e,t){var n=e.target,u=e.global,d=e.stat,f,p=u?r:d?r[n]||s(n,{}):r[n]&&r[n].prototype,m,h,g,_;if(p)for(m in t){if(g=t[m],e.dontCallGetSet?(_=i(p,m),h=_&&_.value):h=p[m],f=l(u?m:n+(d?`.`:`#`)+m,e.forced),!f&&h!==void 0){if(typeof g==typeof h)continue;c(g,h)}(e.sham||h&&h.sham)&&a(g,`sham`,!0),o(p,m,g,e)}}})),Y=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=v(),a=l(),o=function(e){return i.slice(0,e.length)===e};n.exports=(function(){return o(`Bun/`)?`BUN`:o(`Cloudflare-Workers`)?`CLOUDFLARE`:o(`Deno/`)?`DENO`:o(`Node.js/`)?`NODE`:r.Bun&&typeof Bun.version==`string`?`BUN`:r.Deno&&typeof Deno.version==`object`?`DENO`:a(r.process)===`process`?`NODE`:r.window&&r.document?`BROWSER`:`REST`})()})),_e=/* @__PURE__ */ e(((e,t)=>{t.exports=Y()===`NODE`})),ve=/* @__PURE__ */ e(((e,n)=>{n.exports=t()})),ye=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=w();t.exports=function(e,t,i){try{return n(r(Object.getOwnPropertyDescriptor(e,t)[i]))}catch{}}})),X=/* @__PURE__ */ e(((e,t)=>{var n=h();t.exports=function(e){return n(e)||e===null}})),Z=/* @__PURE__ */ e(((e,t)=>{var n=X(),r=String,i=TypeError;t.exports=function(e){if(n(e))return e;throw new i(`Can't set `+r(e)+` as a prototype`)}})),be=/* @__PURE__ */ e(((e,t)=>{var n=ye(),r=h(),i=f(),a=Z();t.exports=Object.setPrototypeOf||(`__proto__`in{}?function(){var e=!1,t={},o;try{o=n(Object.prototype,`__proto__`,`set`),o(t,[]),e=t instanceof Array}catch{}return function(t,n){return i(t),a(n),r(t)&&(e?o(t,n):t.__proto__=n),t}}():void 0)})),Q=/* @__PURE__ */ e(((e,t)=>{var n=V().f,r=M(),i=P()(`toStringTag`);t.exports=function(e,t,a){e&&!a&&(e=e.prototype),e&&!r(e,i)&&n(e,i,{configurable:!0,value:t})}})),xe=/* @__PURE__ */ e(((e,t)=>{var n=ce(),r=V();t.exports=function(e,t,i){return i.get&&n(i.get,t,{getter:!0}),i.set&&n(i.set,t,{setter:!0}),r.f(e,t,i)}})),Se=/* @__PURE__ */ e(((e,t)=>{var n=g(),i=xe(),a=P(),o=r(),s=a(`species`);t.exports=function(e){var t=n(e);o&&t&&!t[s]&&i(t,s,{configurable:!0,get:function(){return this}})}})),Ce=/* @__PURE__ */ e(((e,t)=>{var n=_(),r=TypeError;t.exports=function(e,t){if(n(t,e))return e;throw new r(`Incorrect invocation`)}})),we=/* @__PURE__ */ e(((e,t)=>{var n=P()(`toStringTag`),r={};r[n]=`z`,t.exports=String(r)===`[object z]`})),Te=/* @__PURE__ */ e(((e,t)=>{var n=we(),r=m(),i=l(),a=P()(`toStringTag`),o=Object,s=i(function(){return arguments}())===`Arguments`,c=function(e,t){try{return e[t]}catch{}};t.exports=n?i:function(e){var t,n,l;return e===void 0?`Undefined`:e===null?`Null`:typeof(n=c(t=o(e),a))==`string`?n:s?i(t):(l=i(t))===`Object`&&r(t.callee)?`Arguments`:l}})),Ee=/* @__PURE__ */ e(((e,t)=>{var r=c(),i=n(),a=m(),o=Te(),s=g(),l=re(),u=function(){},d=s(`Reflect`,`construct`),f=/^\s*(?:class|function)\b/,p=r(f.exec),h=!f.test(u),_=function(e){if(!a(e))return!1;try{return d(u,[],e),!0}catch{return!1}},v=function(e){if(!a(e))return!1;switch(o(e)){case`AsyncFunction`:case`GeneratorFunction`:case`AsyncGeneratorFunction`:return!1}try{return h||!!p(f,l(e))}catch{return!0}};v.sham=!0,t.exports=!d||i(function(){var e;return _(_.call)||!_(Object)||!_(function(){e=!0})||e})?v:_})),De=/* @__PURE__ */ e(((e,t)=>{var n=Ee(),r=C(),i=TypeError;t.exports=function(e){if(n(e))return e;throw new i(r(e)+` is not a constructor`)}})),Oe=/* @__PURE__ */ e(((e,t)=>{var n=B(),r=De(),i=d(),a=P()(`species`);t.exports=function(e,t){var o=n(e).constructor,s;return o===void 0||i(s=n(o)[a])?t:r(s)}})),ke=/* @__PURE__ */ e(((e,t)=>{var n=i(),r=Function.prototype,a=r.apply,o=r.call;t.exports=typeof Reflect==`object`&&Reflect.apply||(n?o.bind(a):function(){return o.apply(a,arguments)})})),Ae=/* @__PURE__ */ e(((e,t)=>{var n=l(),r=c();t.exports=function(e){if(n(e)===`Function`)return r(e)}})),je=/* @__PURE__ */ e(((e,t)=>{var n=Ae(),r=w(),a=i(),o=n(n.bind);t.exports=function(e,t){return r(e),t===void 0?e:a?o(e,t):function(){return e.apply(t,arguments)}}})),Me=/* @__PURE__ */ e(((e,t)=>{t.exports=g()(`document`,`documentElement`)})),Ne=/* @__PURE__ */ e(((e,t)=>{t.exports=c()([].slice)})),Pe=/* @__PURE__ */ e(((e,t)=>{var n=TypeError;t.exports=function(e,t){if(e<t)throw new n(`Not enough arguments`);return e}})),Fe=/* @__PURE__ */ e(((e,t)=>{var n=v();t.exports=/ipad|iphone|ipod/i.test(n)&&/applewebkit/i.test(n)})),Ie=/* @__PURE__ */ e(((e,r)=>{var i=t(),a=ke(),o=je(),s=m(),c=M(),l=n(),u=Me(),d=Ne(),f=I(),p=Pe(),h=Fe(),g=_e(),_=i.setImmediate,v=i.clearImmediate,y=i.process,b=i.Dispatch,x=i.Function,S=i.MessageChannel,C=i.String,w=0,T={},E=`onreadystatechange`,D,O,k,A;l(function(){D=i.location});var j=function(e){if(c(T,e)){var t=T[e];delete T[e],t()}},N=function(e){return function(){j(e)}},P=function(e){j(e.data)},ee=function(e){i.postMessage(C(e),D.protocol+`//`+D.host)};(!_||!v)&&(_=function(e){p(arguments.length,1);var t=s(e)?e:x(e),n=d(arguments,1);return T[++w]=function(){a(t,void 0,n)},O(w),w},v=function(e){delete T[e]},g?O=function(e){y.nextTick(N(e))}:b&&b.now?O=function(e){b.now(N(e))}:S&&!h?(k=new S,A=k.port2,k.port1.onmessage=P,O=o(A.postMessage,A)):i.addEventListener&&s(i.postMessage)&&!i.importScripts&&D&&D.protocol!==`file:`&&!l(ee)?(O=ee,i.addEventListener(`message`,P,!1)):O=E in f(`script`)?function(e){u.appendChild(f(`script`))[E]=function(){u.removeChild(this),j(e)}}:function(e){setTimeout(N(e),0)}),r.exports={set:_,clear:v}})),Le=/* @__PURE__ */ e(((e,n)=>{var i=t(),a=r(),o=Object.getOwnPropertyDescriptor;n.exports=function(e){if(!a)return i[e];var t=o(i,e);return t&&t.value}})),Re=/* @__PURE__ */ e(((e,t)=>{var n=function(){this.head=null,this.tail=null};n.prototype={add:function(e){var t={item:e,next:null},n=this.tail;n?n.next=t:this.head=t,this.tail=t},get:function(){var e=this.head;if(e)return(this.head=e.next)===null&&(this.tail=null),e.item}},t.exports=n})),ze=/* @__PURE__ */ e(((e,t)=>{var n=v();t.exports=/ipad|iphone|ipod/i.test(n)&&typeof Pebble<`u`})),Be=/* @__PURE__ */ e(((e,t)=>{var n=v();t.exports=/web0s(?!.*chrome)/i.test(n)})),Ve=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=Le(),a=je(),o=Ie().set,s=Re(),c=Fe(),l=ze(),u=Be(),d=_e(),f=r.MutationObserver||r.WebKitMutationObserver,p=r.document,m=r.process,h=r.Promise,g=i(`queueMicrotask`),_,v,y,b,x;if(!g){var S=new s,C=function(){var e,t;for(d&&(e=m.domain)&&e.exit();t=S.get();)try{t()}catch(e){throw S.head&&_(),e}e&&e.enter()};!c&&!d&&!u&&f&&p?(v=!0,y=p.createTextNode(``),new f(C).observe(y,{characterData:!0}),_=function(){y.data=v=!v}):!l&&h&&h.resolve?(b=h.resolve(void 0),b.constructor=h,x=a(b.then,b),_=function(){x(C)}):d?_=function(){m.nextTick(C)}:(o=a(o,r),_=function(){o(C)}),g=function(e){S.head||_(),S.add(e)}}n.exports=g})),He=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e,t){try{arguments.length===1?console.error(e):console.error(e,t)}catch{}}})),Ue=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e){try{return{error:!1,value:e()}}catch(e){return{error:!0,value:e}}}})),We=/* @__PURE__ */ e(((e,n)=>{n.exports=t().Promise})),Ge=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=We(),a=m(),o=q(),s=re(),c=P(),l=Y(),u=D(),d=y(),f=i&&i.prototype,p=c(`species`),h=!1,g=a(r.PromiseRejectionEvent);n.exports={CONSTRUCTOR:o(`Promise`,function(){var e=s(i),t=e!==String(i);if(!t&&d===66||u&&!(f.catch&&f.finally))return!0;if(!d||d<51||!/native code/.test(e)){var n=new i(function(e){e(1)}),r=function(e){e(function(){},function(){})},a=n.constructor={};if(a[p]=r,h=n.then(function(){})instanceof r,!h)return!0}return!t&&(l===`BROWSER`||l===`DENO`)&&!g}),REJECTION_EVENT:g,SUBCLASSING:h}})),Ke=/* @__PURE__ */ e(((e,t)=>{var n=w(),r=TypeError,i=function(e){var t,i;this.promise=new e(function(e,n){if(t!==void 0||i!==void 0)throw new r(`Bad Promise constructor`);t=e,i=n}),this.resolve=n(t),this.reject=n(i)};t.exports.f=function(e){return new i(e)}})),qe=/* @__PURE__ */ e((()=>{var e=J(),n=D(),r=_e(),i=t(),o=ve(),s=a(),c=le(),l=be(),u=Q(),d=Se(),f=w(),p=m(),g=h(),_=Ce(),v=Oe(),y=Ie().set,b=Ve(),x=He(),S=Ue(),C=Re(),T=se(),E=We(),O=Ge(),k=Ke(),A=`Promise`,j=O.CONSTRUCTOR,M=O.REJECTION_EVENT,N=O.SUBCLASSING,P=T.getterFor(A),ee=T.set,F=E&&E.prototype,I=E,L=F,R=i.TypeError,z=i.document,B=i.process,V=k.f,te=V,ne=!!(z&&z.createEvent&&i.dispatchEvent),re=`unhandledrejection`,ie=`rejectionhandled`,ae=0,oe=1,ce=2,ue=1,de=2,fe,pe,H,U,me=function(e){var t;return g(e)&&p(t=e.then)?t:!1},W=function(e,t){var n=t.value,r=t.state===oe,i=r?e.ok:e.fail,a=e.resolve,o=e.reject,c=e.domain,l,u,d;try{i?(r||(t.rejection===de&&q(t),t.rejection=ue),i===!0?l=n:(c&&c.enter(),l=i(n),c&&(c.exit(),d=!0)),l===e.promise?o(new R(`Promise-chain cycle`)):(u=me(l))?s(u,l,a,o):a(l)):o(n)}catch(e){c&&!d&&c.exit(),o(e)}},G=function(e,t){e.notified||(e.notified=!0,b(function(){for(var n=e.reactions,r;r=n.get();)W(r,e);e.notified=!1,t&&!e.rejection&&he(e)}))},K=function(e,t,n){var r,a;ne?(r=z.createEvent(`Event`),r.promise=t,r.reason=n,r.initEvent(e,!1,!0),i.dispatchEvent(r)):r={promise:t,reason:n},!M&&(a=i[`on`+e])?a(r):e===re&&x(`Unhandled promise rejection`,n)},he=function(e){s(y,i,function(){var t=e.facade,n=e.value,i=ge(e),a;if(i&&(a=S(function(){r?B.emit(`unhandledRejection`,n,t):K(re,t,n)}),e.rejection=r||ge(e)?de:ue,a.error))throw a.value})},ge=function(e){return e.rejection!==ue&&!e.parent},q=function(e){s(y,i,function(){var t=e.facade;r?B.emit(`rejectionHandled`,t):K(ie,t,e.value)})},Y=function(e,t,n){return function(r){e(t,r,n)}},ye=function(e,t,n){e.done||(e.done=!0,n&&(e=n),e.value=t,e.state=ce,G(e,!0))},X=function(e,t,n){if(!e.done){e.done=!0,n&&(e=n);try{if(e.facade===t)throw new R(`Promise can't be resolved itself`);var r=me(t);r?b(function(){var n={done:!1};try{s(r,t,Y(X,n,e),Y(ye,n,e))}catch(t){ye(n,t,e)}}):(e.value=t,e.state=oe,G(e,!1))}catch(t){ye({done:!1},t,e)}}};if(j&&(I=function(e){_(this,L),f(e),s(fe,this);var t=P(this);try{e(Y(X,t),Y(ye,t))}catch(e){ye(t,e)}},L=I.prototype,fe=function(e){ee(this,{type:A,done:!1,notified:!1,parent:!1,reactions:new C,rejection:!1,state:ae,value:null})},fe.prototype=c(L,`then`,function(e,t){var n=P(this),i=V(v(this,I));return n.parent=!0,i.ok=!p(e)||e,i.fail=p(t)&&t,i.domain=r?B.domain:void 0,n.state===ae?n.reactions.add(i):b(function(){W(i,n)}),i.promise}),pe=function(){var e=new fe,t=P(e);this.promise=e,this.resolve=Y(X,t),this.reject=Y(ye,t)},k.f=V=function(e){return e===I||e===H?new pe(e):te(e)},!n&&p(E)&&F!==Object.prototype)){U=F.then,N||c(F,`then`,function(e,t){var n=this;return new I(function(e,t){s(U,n,e,t)}).then(e,t)},{unsafe:!0});try{delete F.constructor}catch{}l&&l(F,L)}e({global:!0,constructor:!0,wrap:!0,forced:j},{Promise:I}),H=o.Promise,u(I,A,!1,!0),d(A)})),Je=/* @__PURE__ */ e(((e,t)=>{t.exports=Object.create?Object.create(null):{}})),Ye=/* @__PURE__ */ e(((e,t)=>{var n=P(),r=Je(),i=n(`iterator`),a=Array.prototype;t.exports=function(e){return e!==void 0&&(r.Array===e||a[i]===e)}})),Xe=/* @__PURE__ */ e(((e,t)=>{var n=l(),r=d(),i=T(),a=P()(`iterator`),o=Array.prototype;t.exports=function(e){if(!r(e))return i(e,a)||i(e,`@@iterator`)||(n(e)===`Arguments`?o[a]:void 0)}})),Ze=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=m(),i=B(),o=C(),s=Xe(),c=TypeError;t.exports=function(e,t){var a=arguments.length<2?s(e):t;if(r(a))return i(n(a,e));throw new c(o(e)+` is not iterable`)}})),Qe=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=B(),i=T();t.exports=function(e,t,a){var o,s;r(e);try{if(o=i(e,`return`),!o){if(t===`throw`)throw a;return a}o=n(o,e)}catch(e){s=!0,o=e}if(t===`throw`)throw a;if(s)throw o;return r(o),a}})),$e=/* @__PURE__ */ e(((e,t)=>{var n=je(),r=a(),i=B(),o=C(),s=Ye(),c=H(),l=_(),u=Ze(),d=Xe(),f=Qe(),p=TypeError,m=function(e,t){this.stopped=e,this.result=t},h=m.prototype;t.exports=function(e,t,a){var g=a&&a.that,_=!!(a&&a.AS_ENTRIES),v=!!(a&&a.IS_RECORD),y=!!(a&&a.IS_ITERATOR),b=!!(a&&a.INTERRUPTED),x=n(t,g),S,C,w,T,E,D,O,k=function(e){var t=S;return S=void 0,t&&f(t,`normal`),new m(!0,e)},A=function(e){return _?(i(e),b?x(e[0],e[1],k):x(e[0],e[1])):b?x(e,k):x(e)};if(v)S=e.iterator;else if(y)S=e;else{if(C=d(e),!C)throw new p(o(e)+` is not iterable`);if(s(C)){for(w=0,T=c(e);T>w;w++)if(E=A(e[w]),E&&l(h,E))return E;return new m(!1)}S=u(e,C)}for(D=v?e.next:S.next;!(O=r(D,S)).done;){var j=O.value;try{E=A(j)}catch(e){if(S)f(S,`throw`,e);else throw e}if(typeof E==`object`&&E&&l(h,E))return E}return new m(!1)}})),et=/* @__PURE__ */ e(((e,t)=>{var n=P()(`iterator`),r=!1;try{var i=0,a={next:function(){return{done:!!i++}},return:function(){r=!0}};a[n]=function(){return this},Array.from(a,function(){throw 2})}catch{}t.exports=function(e,t){try{if(!t&&!r)return!1}catch{return!1}var i=!1;try{var a={};a[n]=function(){return{next:function(){return{done:i=!0}}}},e(a)}catch{}return i}})),tt=/* @__PURE__ */ e(((e,t)=>{var n=We(),r=et();t.exports=Ge().CONSTRUCTOR||!r(function(e){n.all(e).then(void 0,function(){})})})),nt=/* @__PURE__ */ e((()=>{var e=J(),t=a(),n=w(),r=Ke(),i=Ue(),o=$e();e({target:`Promise`,stat:!0,forced:tt()},{all:function(e){var a=this,s=r.f(a),c=s.resolve,l=s.reject,u=i(function(){var r=n(a.resolve),i=[],s=0,u=1;o(e,function(e){var n=s++,o=!1;u++,t(r,a,e).then(function(e){o||(o=!0,i[n]=e,--u||c(i))},l)}),--u||c(i)});return u.error&&l(u.value),s.promise}})})),rt=/* @__PURE__ */ e((()=>{var e=J(),t=D(),n=Ge().CONSTRUCTOR,r=We(),i=g(),a=m(),o=le(),s=r&&r.prototype;if(e({target:`Promise`,proto:!0,forced:n,real:!0},{catch:function(e){return this.then(void 0,e)}}),!t&&a(r)){var c=i(`Promise`).prototype.catch;s.catch!==c&&o(s,`catch`,c,{unsafe:!0})}})),it=/* @__PURE__ */ e((()=>{var e=J(),t=a(),n=w(),r=Ke(),i=Ue(),o=$e();e({target:`Promise`,stat:!0,forced:tt()},{race:function(e){var a=this,s=r.f(a),c=s.reject,l=i(function(){var r=n(a.resolve);o(e,function(e){t(r,a,e).then(s.resolve,c)})});return l.error&&c(l.value),s.promise}})})),at=/* @__PURE__ */ e((()=>{var e=J(),t=Ke(),n=Ge().CONSTRUCTOR;e({target:`Promise`,stat:!0,forced:n},{reject:function(e){var n=t.f(this),r=n.reject;return r(e),n.promise}})})),ot=/* @__PURE__ */ e(((e,t)=>{var n=B(),r=h(),i=Ke();t.exports=function(e,t){if(n(e),r(t)&&t.constructor===e)return t;var a=i.f(e),o=a.resolve;return o(t),a.promise}})),st=/* @__PURE__ */ e((()=>{var e=J(),t=g(),n=D(),r=We(),i=Ge().CONSTRUCTOR,a=ot(),o=t(`Promise`),s=n&&!i;e({target:`Promise`,stat:!0,forced:n||i},{resolve:function(e){return a(s&&this===o?r:this,e)}})})),ct=/* @__PURE__ */ e((()=>{qe(),nt(),rt(),it(),at(),st()})),lt=/* @__PURE__ */ e((()=>{var e=J(),t=Ke();e({target:`Promise`,stat:!0},{withResolvers:function(){var e=t.f(this);return{promise:e.promise,resolve:e.resolve,reject:e.reject}}})})),ut=/* @__PURE__ */ e(((e,t)=>{ct(),lt();var n=a(),r=m(),i=ve().Promise,o=i.withResolvers;t.exports=function(){return n(o,r(this)?this:i)}})),dt=/* @__PURE__ */ e(((e,t)=>{t.exports=ut()})),ft=/* @__PURE__ */ e((()=>{lt()})),pt=/* @__PURE__ */ e(((e,t)=>{var n=dt();ft(),t.exports=n})),mt=/* @__PURE__ */ e(((e,t)=>{var n=me(),r=W();t.exports=Object.keys||function(e){return n(e,r)}})),ht=/* @__PURE__ */ e((e=>{var t=r(),n=z(),i=V(),a=B(),o=p(),s=mt();e.f=t&&!n?Object.defineProperties:function(e,t){a(e);for(var n=o(t),r=s(t),c=r.length,l=0,u;c>l;)i.f(e,u=r[l++],n[u]);return e}})),gt=/* @__PURE__ */ e(((e,t)=>{var n=B(),r=ht(),i=W(),a=oe(),o=Me(),s=I(),c=ae(),l=`>`,u=`<`,d=`prototype`,f=`script`,p=c(`IE_PROTO`),m=function(){},h=function(e){return u+f+l+e+u+`/`+f+l},g=function(e){e.write(h(``)),e.close();var t=e.parentWindow.Object;return e=null,t},_=function(){var e=s(`iframe`),t=`java`+f+`:`,n;return e.style.display=`none`,o.appendChild(e),e.src=String(t),n=e.contentWindow.document,n.open(),n.write(h(`document.F=Object`)),n.close(),n.F},v,y=function(){try{v=new ActiveXObject(`htmlfile`)}catch{}y=typeof document<`u`?document.domain&&v?g(v):_():g(v);for(var e=i.length;e--;)delete y[d][i[e]];return y()};a[p]=!0,t.exports=Object.create||function(e,t){var i;return e===null?i=y():(m[d]=n(e),i=new m,m[d]=null,i[p]=e),t===void 0?i:r.f(i,t)}})),_t=/* @__PURE__ */ e((()=>{var e=J(),t=r(),n=gt();e({target:`Object`,stat:!0,sham:!t},{create:n})})),vt=/* @__PURE__ */ e(((e,t)=>{var n=r(),i=V(),a=s();t.exports=function(e,t,r){n?i.f(e,t,a(0,r)):e[t]=r}})),yt=/* @__PURE__ */ e(((e,t)=>{var n=TypeError,r=9007199254740991;t.exports=function(e){if(e>r)throw new n(`Maximum allowed index exceeded`);return e}})),bt=/* @__PURE__ */ e((()=>{var e=J(),t=vt(),r=g(),i=c(),a=w(),o=f(),s=F(),l=$e(),u=yt(),d=n(),p=Object.groupBy,m=r(`Object`,`create`),h=i([].push);e({target:`Object`,stat:!0,forced:!p||d(function(){return p(`ab`,function(e){return e}).a.length!==1})},{groupBy:function(e,n){o(e),a(n);var r=m(null),i=0;return l(e,function(e){u(i);var a=s(n(e,i++));a in r?h(r[a],e):t(r,a,[e])}),r}})})),xt=/* @__PURE__ */ e(((e,t)=>{_t(),bt(),t.exports=ve().Object.groupBy})),St=/* @__PURE__ */ e(((e,t)=>{t.exports=xt()})),Ct=/* @__PURE__ */ e((()=>{bt()})),wt=/* @__PURE__ */ e(((e,t)=>{var n=St();Ct(),t.exports=n})),Tt=/* @__PURE__ */ e(((e,t)=>{var n=we(),r=Te();t.exports=n?{}.toString:function(){return`[object `+r(this)+`]`}})),Et=/* @__PURE__ */ e((()=>{var e=we(),t=le(),n=Tt();e||t(Object.prototype,`toString`,n,{unsafe:!0})})),Dt=/* @__PURE__ */ e(((e,t)=>{var n=l(),r=p(),i=G().f,a=Ne(),o=typeof window==`object`&&window&&Object.getOwnPropertyNames?Object.getOwnPropertyNames(window):[],s=function(e){try{return i(e)}catch{return a(o)}};t.exports.f=function(e){return o&&n(e)===`Window`?s(e):i(r(e))}})),Ot=/* @__PURE__ */ e(((e,t)=>{t.exports=n()(function(){if(typeof ArrayBuffer==`function`){var e=/* @__PURE__ */ new ArrayBuffer(8);Object.isExtensible(e)&&Object.defineProperty(e,"a",{value:8})}})})),kt=/* @__PURE__ */ e(((e,t)=>{var r=n(),i=h(),a=l(),o=Ot(),s=Object.isExtensible;t.exports=r(function(){s(1)})||o?function(e){return!i(e)||o&&a(e)===`ArrayBuffer`?!1:!s||s(e)}:s})),At=/* @__PURE__ */ e(((e,t)=>{t.exports=!n()(function(){return Object.isExtensible(Object.preventExtensions({}))})})),jt=/* @__PURE__ */ e(((e,t)=>{var n=J(),r=c(),i=oe(),a=h(),o=M(),s=V().f,l=G(),u=Dt(),d=kt(),f=N(),p=At(),m=!1,g=f(`meta`),_=0,v=function(e){s(e,g,{value:{objectID:`O`+_++,weakData:{}}})},y=t.exports={enable:function(){y.enable=function(){},m=!0;var e=l.f,t=r([].splice),i={};i[g]=1,e(i).length&&(l.f=function(n){for(var r=e(n),i=0,a=r.length;i<a;i++)if(r[i]===g){t(r,i,1);break}return r},n({target:`Object`,stat:!0,forced:!0},{getOwnPropertyNames:u.f}))},fastKey:function(e,t){if(!a(e))return typeof e==`symbol`?e:(typeof e==`string`?`S`:`P`)+e;if(!o(e,g)){if(!d(e))return`F`;if(!t)return`E`;v(e)}return e[g].objectID},getWeakData:function(e,t){if(!o(e,g)){if(!d(e))return!0;if(!t)return!1;v(e)}return e[g].weakData},onFreeze:function(e){return p&&m&&d(e)&&!o(e,g)&&v(e),e}};i[g]=!0})),Mt=/* @__PURE__ */ e(((e,t)=>{var n=m(),r=h(),i=be();t.exports=function(e,t,a){var o,s;return i&&n(o=t.constructor)&&o!==a&&r(s=o.prototype)&&s!==a.prototype&&i(e,s),e}})),Nt=/* @__PURE__ */ e(((e,r)=>{var i=J(),a=t(),o=c(),s=q(),l=le(),u=jt(),f=$e(),p=Ce(),g=m(),_=d(),v=h(),y=n(),b=et(),x=Q(),S=Mt();r.exports=function(e,t,n){var r=e.indexOf(`Map`)!==-1,c=e.indexOf(`Weak`)!==-1,d=r?`set`:`add`,m=a[e],h=m&&m.prototype,C=m,w={},T=function(e){var t=o(h[e]);l(h,e,e===`add`?function(e){return t(this,e===0?0:e),this}:e===`delete`?function(e){return c&&!v(e)?!1:t(this,e===0?0:e)}:e===`get`?function(e){return c&&!v(e)?void 0:t(this,e===0?0:e)}:e===`has`?function(e){return c&&!v(e)?!1:t(this,e===0?0:e)}:function(e,n){return t(this,e===0?0:e,n),this})};if(s(e,!g(m)||!(c||h.forEach&&!y(function(){new m().entries().next()}))))C=n.getConstructor(t,e,r,d),u.enable();else if(s(e,!0)){var E=new C,D=E[d](c?{}:-0,1)!==E,O=y(function(){E.has(1)}),k=b(function(e){new m(e)}),A=!c&&y(function(){for(var e=new m,t=5;t--;)e[d](t,t);return!e.has(-0)});k||(C=t(function(e,t){p(e,h);var n=S(new m,e,C);return _(t)||f(t,n[d],{that:n,AS_ENTRIES:r}),n}),C.prototype=h,h.constructor=C),(O||A)&&(T(`delete`),T(`has`),r&&T(`get`)),(A||D)&&T(d),c&&h.clear&&delete h.clear}return w[e]=C,i({global:!0,constructor:!0,forced:C!==m},w),x(C,e),c||n.setStrong(C,e,r),C}})),Pt=/* @__PURE__ */ e(((e,t)=>{var n=le();t.exports=function(e,t,r){for(var i in t)n(e,i,t[i],r);return e}})),Ft=/* @__PURE__ */ e(((e,t)=>{t.exports=!n()(function(){function e(){}return e.prototype.constructor=null,Object.getPrototypeOf(new e)!==e.prototype})})),It=/* @__PURE__ */ e(((e,t)=>{var n=M(),r=m(),i=j(),a=ae(),o=Ft(),s=a(`IE_PROTO`),c=Object,l=c.prototype;t.exports=o?c.getPrototypeOf:function(e){var t=i(e);if(n(t,s))return t[s];var a=t.constructor;return r(a)&&t instanceof a?a.prototype:t instanceof c?l:null}})),Lt=/* @__PURE__ */ e(((e,t)=>{var r=n(),i=m(),a=h(),o=gt(),s=It(),c=le(),l=P(),u=D(),d=l(`iterator`),f=!1,p,g,_;[].keys&&(_=[].keys(),`next`in _?(g=s(s(_)),g!==Object.prototype&&(p=g)):f=!0),!a(p)||r(function(){var e={};return p[d].call(e)!==e})?p={}:u&&(p=o(p)),i(p[d])||c(p,d,function(){return this}),t.exports={IteratorPrototype:p,BUGGY_SAFARI_ITERATORS:f}})),Rt=/* @__PURE__ */ e(((e,t)=>{var n=Lt().IteratorPrototype,r=gt(),i=s(),a=Q(),o=Je(),c=function(){return this};t.exports=function(e,t,s,l){var u=t+` Iterator`;return e.prototype=r(n,{next:i(+!l,s)}),a(e,u,!1,!0),o[u]=c,e}})),zt=/* @__PURE__ */ e(((e,t)=>{var n=J(),r=a(),i=D(),o=ne(),s=m(),c=Rt(),l=It(),u=be(),d=Q(),f=te(),p=le(),h=P(),g=Je(),_=Lt(),v=o.PROPER,y=o.CONFIGURABLE,b=_.IteratorPrototype,x=_.BUGGY_SAFARI_ITERATORS,S=h(`iterator`),C=`keys`,w=`values`,T=`entries`,E=function(){return this};t.exports=function(e,t,a,o,m,h,_){c(a,t,o);var D=function(e){if(e===m&&M)return M;if(!x&&e&&e in A)return A[e];switch(e){case C:return function(){return new a(this,e)};case w:return function(){return new a(this,e)};case T:return function(){return new a(this,e)}}return function(){return new a(this)}},O=t+` Iterator`,k=!1,A=e.prototype,j=A[S]||A[`@@iterator`]||m&&A[m],M=!x&&j||D(m),N=t===`Array`&&A.entries||j,P,ee,F;if(N&&(P=l(N.call(new e)),P!==Object.prototype&&P.next&&(!i&&l(P)!==b&&(u?u(P,b):s(P[S])||p(P,S,E)),d(P,O,!0,!0),i&&(g[O]=E))),v&&m===w&&j&&j.name!==w&&(!i&&y?f(A,`name`,w):(k=!0,M=function(){return r(j,this)})),m){if(ee={values:D(w),keys:h?M:D(C),entries:D(T)},_)for(F in ee)(x||k||!(F in A))&&p(A,F,ee[F]);else n({target:t,proto:!0,forced:x||k},ee)}return(!i||_)&&A[S]!==M&&p(A,S,M,{name:m}),g[t]=M,ee}})),Bt=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e,t){return{value:e,done:t}}})),Vt=/* @__PURE__ */ e(((e,t)=>{var n=gt(),i=xe(),a=Pt(),o=je(),s=Ce(),c=d(),l=$e(),u=zt(),f=Bt(),p=Se(),m=r(),h=jt().fastKey,g=se(),_=g.set,v=g.getterFor;t.exports={getConstructor:function(e,t,r,u){var d=e(function(e,i){s(e,f),_(e,{type:t,index:n(null),first:null,last:null,size:0}),m||(e.size=0),c(i)||l(i,e[u],{that:e,AS_ENTRIES:r})}),f=d.prototype,p=v(t),g=function(e,t,n){var r=p(e),i=y(e,t),a,o;return i?i.value=n:(r.last=i={index:o=h(t,!0),key:t,value:n,previous:a=r.last,next:null,removed:!1},r.first||=i,a&&(a.next=i),m?r.size++:e.size++,o!==`F`&&(r.index[o]=i)),e},y=function(e,t){var n=p(e),r=h(t),i;if(r!==`F`)return n.index[r];for(i=n.first;i;i=i.next)if(i.key===t)return i};return a(f,{clear:function(){for(var e=this,t=p(e),r=t.first;r;)r.removed=!0,r.previous&&(r.previous=r.previous.next=null),r=r.next;t.first=t.last=null,t.index=n(null),m?t.size=0:e.size=0},delete:function(e){var t=this,n=p(t),r=y(t,e);if(r){var i=r.next,a=r.previous;delete n.index[r.index],r.removed=!0,a&&(a.next=i),i&&(i.previous=a),n.first===r&&(n.first=i),n.last===r&&(n.last=a),m?n.size--:t.size--}return!!r},forEach:function(e){for(var t=p(this),n=o(e,arguments.length>1?arguments[1]:void 0),r;r=r?r.next:t.first;)for(n(r.value,r.key,this);r&&r.removed;)r=r.previous},has:function(e){return!!y(this,e)}}),a(f,r?{get:function(e){var t=y(this,e);return t&&t.value},set:function(e,t){return g(this,e===0?0:e,t)}}:{add:function(e){return g(this,e=e===0?0:e,e)}}),m&&i(f,`size`,{configurable:!0,get:function(){return p(this).size}}),d},setStrong:function(e,t,n){var r=t+` Iterator`,i=v(t),a=v(r);u(e,t,function(e,t){_(this,{type:r,target:e,state:i(e),kind:t,last:null})},function(){for(var e=a(this),t=e.kind,n=e.last;n&&n.removed;)n=n.previous;return!e.target||!(e.last=n=n?n.next:e.state.first)?(e.target=null,f(void 0,!0)):f(t===`keys`?n.key:t===`values`?n.value:[n.key,n.value],!1)},n?`entries`:`values`,!n,!0),p(t)}}})),Ht=/* @__PURE__ */ e((()=>{Nt()(`Map`,function(e){return function(){return e(this,arguments.length?arguments[0]:void 0)}},Vt())})),Ut=/* @__PURE__ */ e((()=>{Ht()})),Wt=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=Map.prototype;t.exports={Map,set:n(r.set),get:n(r.get),has:n(r.has),remove:n(r.delete),proto:r}})),Gt=/* @__PURE__ */ e((()=>{var e=J(),t=c(),r=w(),i=f(),a=$e(),o=yt(),s=Wt(),l=D(),u=n(),d=s.Map,p=s.has,m=s.get,h=s.set,g=t([].push),_=l||u(function(){return d.groupBy(`ab`,function(e){return e}).get(`a`).length!==1});e({target:`Map`,stat:!0,forced:l||_},{groupBy:function(e,t){i(e),r(t);var n=new d,s=0;return a(e,function(e){o(s);var r=t(e,s++);p(n,r)?g(m(n,r),e):h(n,r,[e])}),n}})})),Kt=/* @__PURE__ */ e((()=>{var e=J(),t=Wt(),n=D(),r=t.get,i=t.has,a=t.set;e({target:`Map`,proto:!0,real:!0,forced:n},{getOrInsert:function(e,t){return i(this,e)?r(this,e):(a(this,e,t),t)}})})),qt=/* @__PURE__ */ e((()=>{var e=J(),t=w(),n=Wt(),r=D(),i=n.get,a=n.has,o=n.set;e({target:`Map`,proto:!0,real:!0,forced:r},{getOrInsertComputed:function(e,n){var r=a(this,e);if(t(n),r)return i(this,e);e===0&&1/e==-1/0&&(e=0);var s=n(e);return o(this,e,s),s}})})),Jt=/* @__PURE__ */ e(((e,t)=>{Et(),Ut(),Gt(),Kt(),qt(),t.exports=ve().Map.groupBy})),Yt=/* @__PURE__ */ e(((e,t)=>{t.exports=Jt()})),Xt=/* @__PURE__ */ e((()=>{Gt()})),Zt=/* @__PURE__ */ e((()=>{Kt()})),Qt=/* @__PURE__ */ e((()=>{qt()})),$t=/* @__PURE__ */ e(((e,t)=>{var n=Yt();Xt(),Zt(),Qt(),t.exports=n})),en=/* @__PURE__ */ e(((e,t)=>{var n=P(),r=gt(),i=V().f,a=n(`unscopables`),o=Array.prototype;o[a]===void 0&&i(o,a,{configurable:!0,value:r(null)}),t.exports=function(e){o[a][e]=!0}})),tn=/* @__PURE__ */ e(((e,t)=>{var n=p(),i=en(),a=Je(),o=se(),s=V().f,c=zt(),l=Bt(),u=D(),d=r(),f=`Array Iterator`,m=o.set,h=o.getterFor(f);t.exports=c(Array,`Array`,function(e,t){m(this,{type:f,target:n(e),index:0,kind:t})},function(){var e=h(this),t=e.target,n=e.index++;if(!t||n>=t.length)return e.target=null,l(void 0,!0);switch(e.kind){case`keys`:return l(n,!1);case`values`:return l(t[n],!1)}return l([n,t[n]],!1)},`values`);var g=a.Arguments=a.Array;if(i(`keys`),i(`values`),i(`entries`),!u&&d&&g.name!==`values`)try{s(g,`name`,{value:`values`})}catch{}})),nn=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=k(),a=m(),o=gt(),s=It(),c=le(),l=P(),u=D(),d=`USE_FUNCTION_CONSTRUCTOR`,f=l(`asyncIterator`),p=r.AsyncIterator,h=i.AsyncIteratorPrototype,g,_;if(h)g=h;else if(a(p))g=p.prototype;else if(i[d]||r[d])try{_=s(s(s(Function(`return async function*(){}()`)()))),s(_)===Object.prototype&&(g=_)}catch{}g?u&&(g=o(g)):g={},a(g[f])||c(g,f,function(){return this}),n.exports=g})),rn=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=B(),i=gt(),o=T(),s=Pt(),c=se(),l=Qe(),u=g(),d=nn(),f=Bt(),p=u(`Promise`),m=`AsyncFromSyncIterator`,h=c.set,_=c.getterFor(m),v=function(e,t,n,r,i){var a=e.done;p.resolve(e.value).then(function(e){t(f(e,a))},function(e){if(!a&&i)try{l(r,`throw`,e)}catch(t){e=t}n(e)})},y=function(e){e.type=m,h(this,e)};y.prototype=s(i(d),{next:function(){var e=_(this),t=arguments.length>0,i=t?arguments[0]:void 0;return new p(function(a,o){v(r(t?n(e.next,e.iterator,i):n(e.next,e.iterator)),a,o,e.iterator,!0)})},return:function(){var e=_(this).iterator,t=arguments.length>0,i=t?arguments[0]:void 0;return new p(function(a,s){var c=o(e,`return`);if(c===void 0)return a(f(i,!0));v(r(t?n(c,e,i):n(c,e)),a,s,e)})},throw:function(){var e=_(this).iterator,t=arguments.length>0,i=t?arguments[0]:void 0;return new p(function(a,s){var c=o(e,`throw`);if(c===void 0){try{l(e,`normal`)}catch(e){return s(e)}return s(/* @__PURE__ */ TypeError(`The iterator does not provide a throw method`))}v(r(t?n(c,e,i):n(c,e)),a,s,e,!0)})}}),t.exports=y})),an=/* @__PURE__ */ e(((e,t)=>{t.exports=function(e){return{iterator:e,next:e.next,done:!1}}})),on=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=rn(),i=B(),o=Ze(),s=an(),c=T(),l=P()(`asyncIterator`);t.exports=function(e,t){var a=arguments.length<2?c(e,l):t;return a?i(n(a,e)):new r(s(o(e)))}})),sn=/* @__PURE__ */ e(((e,n)=>{var r=t();n.exports=function(e,t){var n=r[e],i=n&&n.prototype;return i&&i[t]}})),cn=/* @__PURE__ */ e(((e,t)=>{var n=l();t.exports=Array.isArray||function(e){return n(e)===`Array`}})),ln=/* @__PURE__ */ e(((e,t)=>{var n=r(),i=cn(),a=TypeError,o=Object.getOwnPropertyDescriptor;t.exports=n&&!function(){if(this!==void 0)return!0;try{Object.defineProperty([],"length",{writable:!1}).length=1}catch(e){return e instanceof TypeError}}()?function(e,t){if(i(e)&&!o(e,`length`).writable)throw new a(`Cannot set read only .length`);return e.length=t}:function(e,t){return e.length=t}})),un=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=B(),i=g(),o=T();t.exports=function(e,t,a,s){try{var c=o(e,`return`);if(c)return i(`Promise`).resolve(n(c,e)).then(function(e){try{t!==s&&r(e)}catch(e){s(e);return}t(a)},function(e){t===s?t(a):s(e)})}catch(e){return s(t===s?a:e)}t(a)}})),dn=/* @__PURE__ */ e(((e,t)=>{var n=a(),r=w(),i=B(),o=h(),s=yt(),c=g(),l=vt(),u=ln(),d=an(),f=un(),p=function(e){var t=e===0,a=e===1,p=e===2,m=e===3;return function(e,h,g){i(e);var _=h!==void 0;(_||!t)&&r(h);var v=d(e),y=c(`Promise`),b=v.iterator,x=v.next,S=0;return new y(function(e,r){var c=function(e){f(b,r,e,r)},d=function(){try{try{s(S)}catch(e){return c(e)}y.resolve(i(n(x,b))).then(function(n){try{if(i(n).done)t?(u(g,S),e(g)):e(m?!1:p||void 0);else{var s=n.value;try{if(_){var v=S++,x=h(s,v),C=function(n){if(a)d();else if(p)n?d():f(b,e,!1,r);else if(t)try{l(g,v,n),d()}catch(e){c(e)}else n?f(b,e,m||s,r):d()};o(x)?y.resolve(x).then(C,c):C(x)}else l(g,S++,s),d()}catch(e){c(e)}}}catch(e){r(e)}},r)}catch(e){r(e)}};d()})}};t.exports={toArray:p(0),forEach:p(1),every:p(2),some:p(3),find:p(4)}})),fn=/* @__PURE__ */ e(((e,t)=>{var n=je(),r=c(),i=Ee(),a=on(),o=Ze(),s=an(),l=Xe(),u=T(),d=g(),f=sn(),p=P(),m=rn(),h=dn().toArray,_=p(`asyncIterator`),v=r(f(`Array`,`values`)),y=r(v([]).next),b=function(){return new x(this)},x=function(e){this.iterator=v(e)};x.prototype.next=function(){return y(this.iterator)},t.exports=function(e){var t=this,r=arguments.length,c=r>1?arguments[1]:void 0,f=r>2?arguments[2]:void 0;return new(d(`Promise`))(function(r){c!==void 0&&(c=n(c,f));var d=u(e,_),p=d?void 0:l(e)||b,g=i(t)?new t:[];r(h(d?a(e,d):new m(s(o(e,p))),c,g))})}})),pn=/* @__PURE__ */ e((()=>{var e=J(),t=fn(),r=n(),i=Array.fromAsync;e({target:`Array`,stat:!0,forced:!i||r(function(){var e=0;return i.call(function(){return e++,[]},{length:0}),e!==1})},{fromAsync:t})})),mn=/* @__PURE__ */ e(((e,t)=>{var n=Te(),r=String;t.exports=function(e){if(n(e)===`Symbol`)throw TypeError(`Cannot convert a Symbol value to a string`);return r(e)}})),hn=/* @__PURE__ */ e(((e,t)=>{var n=c(),r=de(),i=mn(),a=f(),o=n(``.charAt),s=n(``.charCodeAt),l=n(``.slice),u=function(e){return function(t,n){var c=i(a(t)),u=r(n),d=c.length,f,p;return u<0||u>=d?e?``:void 0:(f=s(c,u),f<55296||f>56319||u+1===d||(p=s(c,u+1))<56320||p>57343?e?o(c,u):f:e?l(c,u,u+2):(f-55296<<10)+(p-56320)+65536)}};t.exports={codeAt:u(!1),charAt:u(!0)}})),gn=/* @__PURE__ */ e((()=>{var e=hn().charAt,t=mn(),n=se(),r=zt(),i=Bt(),a=`String Iterator`,o=n.set,s=n.getterFor(a);r(String,`String`,function(e){o(this,{type:a,string:t(e),index:0})},function(){var t=s(this),n=t.string,r=t.index,a;return r>=n.length?i(void 0,!0):(a=e(n,r),t.index+=a.length,i(a,!1))})})),_n=/* @__PURE__ */ e(((e,t)=>{tn(),pn(),Et(),ct(),gn(),t.exports=ve().Array.fromAsync})),vn=/* @__PURE__ */ e(((e,t)=>{t.exports=_n()})),yn=/* @__PURE__ */ e((()=>{pn()})),bn=/* @__PURE__ */ e(((e,t)=>{var n=vn();yn(),t.exports=n})),xn=/* @__PURE__ */ e(((e,t)=>{var n=C(),r=TypeError;t.exports=function(e,t){if(!delete e[t])throw new r(`Cannot delete property `+n(t)+` of `+n(e))}})),Sn=/* @__PURE__ */ e(((e,t)=>{var n=Ne(),r=Math.floor,i=function(e,t){var a=e.length;if(a<8)for(var o=1,s,c;o<a;){for(c=o,s=e[o];c&&t(e[c-1],s)>0;)e[c]=e[--c];c!==o++&&(e[c]=s)}else for(var l=r(a/2),u=i(n(e,0,l),t),d=i(n(e,l),t),f=u.length,p=d.length,m=0,h=0;m<f||h<p;)e[m+h]=m<f&&h<p?t(u[m],d[h])<=0?u[m++]:d[h++]:m<f?u[m++]:d[h++];return e};t.exports=i})),Cn=/* @__PURE__ */ e(((e,t)=>{var r=n();t.exports=function(e,t){var n=[][e];return!!n&&r(function(){n.call(null,t||function(){return 1},1)})}})),wn=/* @__PURE__ */ e(((e,t)=>{var n=v().match(/firefox\/(\d+)/i);t.exports=!!n&&+n[1]})),Tn=/* @__PURE__ */ e(((e,t)=>{var n=v();t.exports=/MSIE|Trident/.test(n)})),En=/* @__PURE__ */ e(((e,t)=>{var n=v().match(/AppleWebKit\/(\d+)\./);t.exports=!!n&&+n[1]})),Dn=/* @__PURE__ */ e((()=>{var e=J(),t=c(),r=w(),i=j(),a=H(),o=xn(),s=mn(),l=n(),u=Sn(),d=Cn(),f=wn(),p=Tn(),m=y(),h=En(),g=[],_=t(g.sort),v=t(g.push),b=l(function(){g.sort(void 0)}),x=l(function(){g.sort(null)}),S=d(`sort`),C=!l(function(){if(m)return m<70;if(!(f&&f>3)){if(p)return!0;if(h)return h<603;for(var e=``,t=65,n,r,i;t<76;t++){switch(n=String.fromCharCode(t),t){case 66:case 69:case 70:case 72:r=3;break;case 68:case 71:r=4;break;default:r=2}for(i=0;i<47;i++)g.push({k:n+i,v:r})}for(g.sort(function(e,t){return t.v-e.v}),i=0;i<g.length;i++)n=g[i].k.charAt(0),e.charAt(e.length-1)!==n&&(e+=n);return e!==`DGBEFHACIJK`}}),T=b||!x||!S||!C,E=function(e){return function(t,n){if(n===void 0)return-1;if(t===void 0)return 1;if(e!==void 0)return+e(t,n)||0;var r=s(t),i=s(n);return r===i?0:r>i?1:-1}};e({target:`Array`,proto:!0,forced:T},{sort:function(e){e!==void 0&&r(e);var t=i(this);if(C)return e===void 0?_(t):_(t,e);for(var n=[],s=a(t),c,l=0;l<s;l++)l in t&&v(n,t[l]);for(u(n,E(e)),c=a(n),l=0;l<c;)t[l]=n[l++];for(;l<s;)o(t,l++);return t}})})),On=/* @__PURE__ */ e(((e,t)=>{var n=H();t.exports=function(e,t,r){for(var i=0,a=arguments.length>2?r:n(t),o=new e(a);a>i;)o[i]=t[i++];return o}})),kn=/* @__PURE__ */ e((()=>{var e=J(),t=c(),n=w(),r=p(),i=On(),a=sn(),o=en(),s=Array,l=t(a(`Array`,`sort`));e({target:`Array`,proto:!0},{toSorted:function(e){return e!==void 0&&n(e),l(i(s,r(this)),e)}}),o(`toSorted`)})),An=/* @__PURE__ */ e(((e,n)=>{var r=t(),i=c();n.exports=function(e,t){return i(r[e].prototype[t])}})),jn=/* @__PURE__ */ e(((e,t)=>{Dn(),kn(),t.exports=An()(`Array`,`toSorted`)})),Mn=/* @__PURE__ */ e(((e,t)=>{t.exports=jn()})),Nn=/* @__PURE__ */ e((()=>{kn()})),Pn=/* @__PURE__ */ e(((e,t)=>{var n=Mn();Nn(),t.exports=n})),Fn=/* @__PURE__ */ e((()=>{var e=J(),t=H(),n=p(),r=vt(),i=en(),a=Array;e({target:`Array`,proto:!0},{toReversed:function(){for(var e=n(this),i=t(e),o=new a(i),s=0;s<i;s++)r(o,s,e[i-s-1]);return o}}),i(`toReversed`)})),In=/* @__PURE__ */ e(((e,t)=>{Fn(),t.exports=An()(`Array`,`toReversed`)})),Ln=/* @__PURE__ */ e(((e,t)=>{t.exports=In()})),Rn=/* @__PURE__ */ e((()=>{Fn()})),zn=/* @__PURE__ */ e(((e,t)=>{var n=Ln();Rn(),t.exports=n})),Bn=/* @__PURE__ */ e((()=>{var e=J(),t=en(),n=yt(),r=H(),i=fe(),a=p(),o=de(),s=vt(),c=Array,l=Math.max,u=Math.min;e({target:`Array`,proto:!0},{toSpliced:function(e,t){var d=a(this),f=r(d),p=i(e,f),m=arguments.length,h=0,g,_,v,y;for(m===0?g=_=0:m===1?(g=0,_=f-p):(g=m-2,_=u(l(o(t),0),f-p)),v=n(f+g-_),y=c(v);h<p;h++)s(y,h,d[h]);for(;h<p+g;h++)s(y,h,arguments[h-p+2]);for(;h<v;h++)s(y,h,d[h+_-g]);return y}}),t(`toSpliced`)})),Vn=/* @__PURE__ */ e(((e,t)=>{Bn(),t.exports=An()(`Array`,`toSpliced`)})),Hn=/* @__PURE__ */ e(((e,t)=>{t.exports=Vn()})),Un=/* @__PURE__ */ e((()=>{Bn()})),Wn=/* @__PURE__ */ e(((e,t)=>{var n=Hn();Un(),t.exports=n})),Gn=/* @__PURE__ */ e((()=>{var e=J(),t=H(),n=de(),r=p(),i=vt(),a=Array,o=RangeError;e({target:`Array`,proto:!0,forced:(function(){try{[].with({valueOf:function(){throw 4}},null)}catch(e){return e!==4}})()},{with:function(e,s){var c=r(this),l=t(c),u=n(e),d=u<0?l+u:u;if(d>=l||d<0)throw new o(`Incorrect index`);for(var f=new a(l),p=0;p<l;p++)i(f,p,p===d?s:c[p]);return f}})})),Kn=/* @__PURE__ */ e(((e,t)=>{Gn(),t.exports=An()(`Array`,`with`)})),qn=/* @__PURE__ */ e(((e,t)=>{t.exports=Kn()})),Jn=/* @__PURE__ */ e((()=>{Gn()})),Yn=/* @__PURE__ */ e(((e,t)=>{var n=qn();Jn(),t.exports=n})),Xn=/* @__PURE__ */ e((()=>{var e=J(),t=c(),n=f(),r=mn(),i=t(``.charCodeAt);e({target:`String`,proto:!0},{isWellFormed:function(){for(var e=r(n(this)),t=e.length,a=0;a<t;a++){var o=i(e,a);if((o&63488)==55296&&(o>=56320||++a>=t||(i(e,a)&64512)!=56320))return!1}return!0}})})),Zn=/* @__PURE__ */ e((()=>{Xn()})),Qn=/* @__PURE__ */ e(((e,t)=>{Xn(),t.exports=An()(`String`,`isWellFormed`)})),$n=/* @__PURE__ */ e(((e,t)=>{t.exports=Qn()})),er=/* @__PURE__ */ e(((e,t)=>{Zn(),t.exports=$n()})),tr=/* @__PURE__ */ e((()=>{var e=J(),t=a(),r=c(),i=f(),o=mn(),s=n(),l=Array,u=r(``.charAt),d=r(``.charCodeAt),p=r([].join),m=``.toWellFormed,h=`�`,g=m&&s(function(){return t(m,1)!==`1`});e({target:`String`,proto:!0,forced:g},{toWellFormed:function(){var e=o(i(this));if(g)return t(m,e);for(var n=e.length,r=l(n),a=0;a<n;a++){var s=d(e,a);(s&63488)==55296?s>=56320||a+1>=n||(d(e,a+1)&64512)!=56320?r[a]=h:(r[a]=u(e,a),r[++a]=u(e,a)):r[a]=u(e,a)}return p(r,``)}})})),nr=/* @__PURE__ */ e((()=>{tr()})),rr=/* @__PURE__ */ e(((e,t)=>{tr(),t.exports=An()(`String`,`toWellFormed`)})),ir=/* @__PURE__ */ e(((e,t)=>{t.exports=rr()})),ar=/* @__PURE__ */ e(((e,t)=>{nr(),t.exports=ir()}));pt(),wt(),$t(),bn(),Pn(),zn(),Wn(),Yn(),er(),ar();const or=`
fn erf_f32(v: f32) -> f32 {
  let a = abs(v);
  let x = 1.0 / (1.0 + 0.3275911 * a);
  let y = 1.0 - ((((1.061405429 * x - 1.453152027) * x + 1.421413741) * x - 0.284496736) * x + 0.254829592) * x * exp(-a * a);
  return select(-y, y, v >= 0.0);
}
fn gelu(v: f32) -> f32 { return 0.5 * v * (1.0 + erf_f32(v * 0.7071067811865476)); }
`,sr=`
struct P { frames: u32, bins: u32 }
@group(0) @binding(0) var<storage, read> mel: array<f32>;      // [T, bins]
@group(0) @binding(1) var<storage, read> scale: array<f32>;
@group(0) @binding(2) var<storage, read> shift: array<f32>;
@group(0) @binding(3) var<storage, read_write> out: array<f32>; // [bins, T]
@group(0) @binding(4) var<uniform> p: P;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let i = id.x;
  if (i >= p.frames * p.bins) { return; }
  let f = i / p.frames;
  let t = i % p.frames;
  out[i] = mel[t * p.bins + f] * scale[f] + shift[f];
}
`,cr=`
${or}
struct P { fout: u32, frames: u32, cin: u32, cout: u32, kf: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
@group(0) @binding(1) var<storage, read> w: array<f32>;   // [kf, 3, cin, cout] = [K, cout]
@group(0) @binding(2) var<storage, read> b: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform> p: P;
var<workgroup> as_: array<f32, 1024>; // [64 rows][16 k]
var<workgroup> bs_: array<f32, 1024>; // [16 k][64 cols]

fn gather(row: u32, k: u32) -> f32 {
  let ci = k % p.cin;
  let tap = k / p.cin;
  let dt = tap % 3u;
  let df = tap / 3u;
  let t = row % p.frames;
  let fo = row / p.frames;
  let ti = i32(t) + i32(dt) - 1;
  if (ti < 0 || ti >= i32(p.frames)) { return 0.0; }
  return x[((fo * p.kf + df) * p.frames + u32(ti)) * p.cin + ci];
}

@compute @workgroup_size(16, 16)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_id) l: vec3u, @builtin(local_invocation_index) li: u32) {
  let m = p.fout * p.frames;
  let kTotal = p.kf * 3u * p.cin;
  let row0 = wg.y * 64u;
  let col0 = wg.x * 64u;
  var acc: array<array<f32, 4>, 4>;
  for (var k0 = 0u; k0 < kTotal; k0 += 16u) {
    for (var i = 0u; i < 4u; i++) {
      let idx = li + i * 256u;
      let ar = row0 + idx / 16u;
      let ak = k0 + idx % 16u;
      var av = 0.0;
      if (ar < m && ak < kTotal) { av = gather(ar, ak); }
      as_[idx] = av;
      let bk = k0 + idx / 64u;
      let bc = col0 + idx % 64u;
      var bv = 0.0;
      if (bk < kTotal && bc < p.cout) { bv = w[bk * p.cout + bc]; }
      bs_[idx] = bv;
    }
    workgroupBarrier();
    for (var kk = 0u; kk < 16u; kk++) {
      var a: array<f32, 4>;
      var bb: array<f32, 4>;
      for (var i = 0u; i < 4u; i++) { a[i] = as_[(l.y * 4u + i) * 16u + kk]; }
      for (var j = 0u; j < 4u; j++) { bb[j] = bs_[kk * 64u + l.x * 4u + j]; }
      for (var i = 0u; i < 4u; i++) {
        for (var j = 0u; j < 4u; j++) { acc[i][j] += a[i] * bb[j]; }
      }
    }
    workgroupBarrier();
  }
  for (var i = 0u; i < 4u; i++) {
    let r = row0 + l.y * 4u + i;
    if (r >= m) { continue; }
    for (var j = 0u; j < 4u; j++) {
      let c = col0 + l.x * 4u + j;
      if (c >= p.cout) { continue; }
      y[r * p.cout + c] = gelu(acc[i][j] + b[c]);
    }
  }
}
`,lr=(e,t=`f32`)=>{let n=t!==`f32`;return`
${n?`enable f16;`:``}
${or}
struct P { m: u32, n: u32, k: u32, flags: u32, posDiv: u32, posMod: u32, dmodel: u32, wStride: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
${e}
@group(0) @binding(2) var<storage, read> bias: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<storage, read> table: array<f32>; // [pos][16][cos, sin]
@group(0) @binding(5) var<uniform> p: P;
${n?`var<workgroup> as_: array<vec4h, 256>; // [16 k][16 row quads]
var<workgroup> bs_: array<vec4h, 256>; // [16 k][16 col quads]
var<workgroup> sums_: array<vec4f, 256>; // per-loader row sums of squares
const A_SCALE = 64.0;`:`var<workgroup> as_: array<vec4f, 256>; // [16 k][16 row quads]; afterwards the per-loader row sums of squares
var<workgroup> bs_: array<vec4f, 256>; // [16 k][16 col quads]`}
@compute @workgroup_size(16, 16)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_id) l: vec3u, @builtin(local_invocation_index) li: u32) {
  let row0 = wg.y * 64u;
  let col0 = wg.x * 64u;
  var acc: array<vec4f, 4>; // acc[j] = rows 4*l.y..+3 of column 4*l.x + j
  // Loader roles: A - invocation li reads k = li % 16 of row quad li / 16; B - k = li / 16, col quad li % 16.
  let aK = li % 16u;
  let aQuad = li / 16u;
  let bK = li / 16u;
  let bQuad = li % 16u;
  var squares = vec4f(0.0);
  for (var k0 = 0u; k0 < p.k; k0 += 16u) {
    var av = vec4f(0.0);
    let ak = k0 + aK;
    if (ak < p.k) {
      for (var r = 0u; r < 4u; r++) {
        let row = row0 + aQuad * 4u + r;
        if (row < p.m) { av[r] = x[row * p.k + ak]; }
      }
    }
    squares = fma(av, av, squares);
    as_[aK * 16u + aQuad] = ${n?`vec4h(select(av, av / A_SCALE, (p.flags & 4u) != 0u))`:`av`};
    var bv = ${n?`vec4h`:`vec4f`}(0.0);
    let bk = k0 + bK;
    let bc = col0 + bQuad * 4u;
    if (bk < p.k && bc < p.n) { bv = weightQuad((bk * p.wStride + bc) / 4u); }
    bs_[bK * 16u + bQuad] = bv;
    workgroupBarrier();
${n?`${t===`math`?`    var part: array<vec4h, 4>;
`:``}    for (var kk = 0u; kk < 16u; kk++) {
      let a = as_[kk * 16u + l.y];
      let b = bs_[kk * 16u + l.x];
${t===`math`?`      part[0] = fma(a, vec4h(b.x), part[0]);
      part[1] = fma(a, vec4h(b.y), part[1]);
      part[2] = fma(a, vec4h(b.z), part[2]);
      part[3] = fma(a, vec4h(b.w), part[3]);
    }
    for (var j = 0u; j < 4u; j++) { acc[j] += vec4f(part[j]); }`:`      let af = vec4f(a);
      let bf = vec4f(b);
      acc[0] = fma(af, vec4f(bf.x), acc[0]);
      acc[1] = fma(af, vec4f(bf.y), acc[1]);
      acc[2] = fma(af, vec4f(bf.z), acc[2]);
      acc[3] = fma(af, vec4f(bf.w), acc[3]);
    }`}`:`    for (var kk = 0u; kk < 16u; kk++) {
      let a = as_[kk * 16u + l.y];
      let b = bs_[kk * 16u + l.x];
      acc[0] = fma(a, vec4f(b.x), acc[0]);
      acc[1] = fma(a, vec4f(b.y), acc[1]);
      acc[2] = fma(a, vec4f(b.z), acc[2]);
      acc[3] = fma(a, vec4f(b.w), acc[3]);
    }`}
    workgroupBarrier();
  }
  // The 16 loaders of row quad q are invocations 16q..16q+15; their partial sums meet in ${n?`sums_`:`as_`}.
  ${n?`sums_`:`as_`}[li] = squares;
  workgroupBarrier();
  var rowScale = vec4f(1.0);
  if ((p.flags & 4u) != 0u) {
    var total = vec4f(0.0);
    for (var t = 0u; t < 16u; t++) { total += ${n?`sums_`:`as_`}[l.y * 16u + t]; }
    rowScale = ${n?`A_SCALE`:`1.0`} / max(sqrt(total), vec4f(1e-12));
  }
  for (var i = 0u; i < 4u; i++) {
    let r = row0 + l.y * 4u + i;
    if (r >= p.m) { continue; }
    var v: array<f32, 4>;
    for (var j = 0u; j < 4u; j++) {
      let c = min(col0 + l.x * 4u + j, p.n - 1u);
      v[j] = acc[j][i] * rowScale[i] + bias[c];
    }
    let c0 = col0 + l.x * 4u;
    if ((p.flags & 8u) != 0u && c0 < 2u * p.dmodel) {
      let pos = (r / p.posDiv) % p.posMod;
      for (var pair = 0u; pair < 2u; pair++) {
        let t = (pos * 16u + ((c0 + 2u * pair) % 32u) / 2u) * 2u;
        let cs = table[t];
        let sn = table[t + 1u];
        let x0 = v[2u * pair];
        let x1 = v[2u * pair + 1u];
        v[2u * pair] = x0 * cs - x1 * sn;
        v[2u * pair + 1u] = x1 * cs + x0 * sn;
      }
    }
    for (var j = 0u; j < 4u; j++) {
      let c = c0 + j;
      if (c >= p.n) { continue; }
      var res = v[j];
      let o = r * p.n + c;
      if ((p.flags & 1u) != 0u) { res = gelu(res); }
      if ((p.flags & 2u) != 0u) { res += y[o]; }
      y[o] = res;
    }
  }
}
`},ur=lr(`
@group(0) @binding(1) var<storage, read> w: array<vec4f>;
fn weightQuad(i: u32) -> vec4f { return w[i]; }`),dr=lr(`
@group(0) @binding(1) var<storage, read> w: array<vec2u>; // four fp16 columns per element
fn weightQuad(i: u32) -> vec4f { let q = w[i]; return vec4f(unpack2x16float(q.x), unpack2x16float(q.y)); }`),fr=`
@group(0) @binding(1) var<storage, read> w: array<vec4h>;
fn weightQuad(i: u32) -> vec4h { return w[i]; }`,pr=lr(fr,`storage`),mr=lr(fr,`math`),hr=e=>`
${e?`enable f16;`:``}
struct P { n: u32, heads: u32, width: u32, dmodel: u32, seqStride: u32, batchStride: u32 }
@group(0) @binding(0) var<storage, read> qkv: array<f32>;
@group(0) @binding(1) var<storage, read_write> o: array<f32>; // [rows, dmodel]
@group(0) @binding(2) var<uniform> p: P;
const TILE = 32u;
var<workgroup> ks: array<${e?.k?`vec4h`:`vec4f`}, 256>; // [TILE keys][8 x vec4]
var<workgroup> vs: array<${e?`vec4h`:`vec4f`}, 256>;
@compute @workgroup_size(64)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_id) l: vec3u) {
  let b = wg.z;
  let h = wg.y;
  let qi = wg.x * 64u + l.x;
  let valid = qi < p.n;
  let qrow = b * p.batchStride + min(qi, p.n - 1u) * p.seqStride;
  var q: array<vec4f, 8>;
  var acc: array<vec4f, 8>;
  let qBase = qrow * p.width + h * 32u;
  for (var d = 0u; d < 8u; d++) {
    q[d] = vec4f(qkv[qBase + 4u * d], qkv[qBase + 4u * d + 1u], qkv[qBase + 4u * d + 2u], qkv[qBase + 4u * d + 3u]) * 0.17677669529663687;
    acc[d] = vec4f(0.0);
  }
  var m = -3.0e38;
  var sum = 0.0;
  for (var k0 = 0u; k0 < p.n; k0 += TILE) {
    // 32 keys x 8 vec4 = 256 vec4 each for K and V, four per invocation.
    for (var i = 0u; i < 4u; i++) {
      let idx = l.x + i * 64u;
      let kj = k0 + idx / 8u;
      var kv = vec4f(0.0);
      var vv = vec4f(0.0);
      if (kj < p.n) {
        let base = (b * p.batchStride + kj * p.seqStride) * p.width + h * 32u + (idx % 8u) * 4u;
        let kb = base + p.dmodel;
        let vb = base + 2u * p.dmodel;
        kv = vec4f(qkv[kb], qkv[kb + 1u], qkv[kb + 2u], qkv[kb + 3u]);
        vv = vec4f(qkv[vb], qkv[vb + 1u], qkv[vb + 2u], qkv[vb + 3u]);
      }
      ks[idx] = ${e?.k?`vec4h(kv)`:`kv`};
      vs[idx] = ${e?`vec4h(vv)`:`vv`};
    }
    workgroupBarrier();
    // Constant trip counts so the compiler unrolls and keeps s[] in registers; keys past the end
    // of the sequence get a score of -3e38, whose exp is exactly 0.
    var s: array<f32, 32>;
    var tileMax = -3.0e38;
    for (var j = 0u; j < TILE; j++) {
      var dotv = vec4f(0.0);
      for (var d = 0u; d < 8u; d++) { dotv = fma(q[d], ${e?.k?`vec4f(ks[j * 8u + d])`:`ks[j * 8u + d]`}, dotv); }
      s[j] = select(-3.0e38, dotv.x + dotv.y + dotv.z + dotv.w, k0 + j < p.n);
      tileMax = max(tileMax, s[j]);
    }
    let mNew = max(m, tileMax);
    let r = exp(m - mNew);
    sum *= r;
    for (var d = 0u; d < 8u; d++) { acc[d] *= r; }
    m = mNew;
${e?.pv?`    var part: array<vec4h, 8>;
    for (var j = 0u; j < TILE; j++) {
      let e = exp(s[j] - m);
      sum += e;
      let eh = vec4h(f16(e));
      for (var d = 0u; d < 8u; d++) { part[d] = fma(eh, vs[j * 8u + d], part[d]); }
    }
    for (var d = 0u; d < 8u; d++) { acc[d] += vec4f(part[d]); }`:`    for (var j = 0u; j < TILE; j++) {
      let e = exp(s[j] - m);
      sum += e;
      for (var d = 0u; d < 8u; d++) { acc[d] = fma(vec4f(e), ${e?`vec4f(vs[j * 8u + d])`:`vs[j * 8u + d]`}, acc[d]); }
    }`}
    workgroupBarrier();
  }
  if (!valid) { return; }
  let gate = 1.0 / (1.0 + exp(-qkv[qrow * p.width + 3u * p.dmodel + h]));
  let scale = gate / sum;
  let oBase = qrow * p.dmodel + h * 32u;
  for (var d = 0u; d < 8u; d++) {
    let v = acc[d] * scale;
    o[oBase + 4u * d] = v.x;
    o[oBase + 4u * d + 1u] = v.y;
    o[oBase + 4u * d + 2u] = v.z;
    o[oBase + 4u * d + 3u] = v.w;
  }
}
`,gr=hr(null),_r=hr({k:!0,pv:!1}),vr=hr({k:!0,pv:!0}),yr=`
struct P { freqs: u32, frames: u32, channels: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
@group(0) @binding(1) var<storage, read_write> y: array<f32>;
@group(0) @binding(2) var<uniform> p: P;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let i = id.x;
  if (i >= p.freqs * p.frames * p.channels) { return; }
  let c = i % p.channels;
  let t = (i / p.channels) % p.frames;
  let f = i / (p.channels * p.frames);
  y[(t * p.freqs + f) * p.channels + c] = x[i];
}
`,br=`
struct P { frames: u32 }
@group(0) @binding(0) var<storage, read> l: array<f32>;
@group(0) @binding(1) var<storage, read_write> out: array<f32>;
@group(0) @binding(2) var<uniform> p: P;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let t = id.x;
  if (t >= p.frames) { return; }
  out[t] = l[t * 2u] + l[t * 2u + 1u];
  out[p.frames + t] = l[t * 2u + 1u];
}
`,xr=1500,Sr=e=>e.reduce((e,t)=>e*t,1),Cr=/* @__PURE__ */ new Map([[sr,`stem_bn`],[cr,`conv`],[ur,`matmul`],[dr,`matmul`],[pr,`matmul`],[mr,`matmul`],[gr,`attention`],[_r,`attention`],[vr,`attention`],[yr,`flatten`],[br,`sum_head`]]);var wr=class e{constructor(e,t,n,r,i){this.device=e,this.build=t,this.melBuffer=n,this.outBuffer=r,this.owned=i,this.fineStages=/* @__PURE__ */ new Set,this.autoRuns=0,this.graphs=/* @__PURE__ */ new Map}graph(e){let t=this.graphs.get(e);if(!t){for(let[e,t]of this.graphs)e!==1500&&(t.owned.forEach(e=>e.destroy()),this.graphs.delete(e));t=this.build(e),this.graphs.set(e,t)}return t.steps}static async create(t,n,r,i={}){if(i.f16&&!t.features.has(`shader-f16`))throw Error(`beat_this f16: device lacks shader-f16`);let a=xr,o=[],s=(e,n=0)=>{let r=t.createBuffer({size:Math.max(16,e*4),usage:GPUBufferUsage.STORAGE|GPUBufferUsage.COPY_SRC|GPUBufferUsage.COPY_DST|n});return o.push(r),r},c=s(Math.ceil(n.byteLength/4));t.queue.writeBuffer(c,0,n.buffer,n.byteOffset,n.byteLength);let l=e=>{let t=r.tensors[e];if(!t)throw Error(`beat_this weights: missing tensor ${e}`);return t},u=e=>{let{byte_offset:t,shape:n,dtype:r,stride:i}=l(e),a=i?n[0]*i:Sr(n);return{buffer:c,offset:t,size:a*(r===`f16`?2:4)}},d=e=>r.tensors[e].shape,f=r.hyper_parameters.transformer_dim,p=r.hyper_parameters.stem_dim,m=r.hyper_parameters.ff_mult,h=32*a,g=e=>3*e+e/32,_=s(a*128),v=s(a*128),y=[s(h*p),s(h*p)],b=s(Math.max(h*g(p),a*g(f))),x=s(h*p),S=s(Math.max(h*p*m,a*f*m)),C=s(a*4*p*8),w=s(a*2),T=s(a*2),E=s(4096),D=n.byteOffset+l(`rope.freqs`).byte_offset,O=new Float32Array(n.buffer.slice(D,D+64)),k=new Float32Array(a*16*2);for(let e=0;e<a;e+=1)for(let t=0;t<16;t+=1){let n=Math.fround(Math.fround(e)*O[t]);k[(e*16+t)*2]=Math.cos(n),k[(e*16+t)*2+1]=Math.sin(n)}let A=s(k.length);t.queue.writeBuffer(A,0,k);let j=/* @__PURE__ */ new Map,M=e=>{let n=j.get(e);return n||(n=t.createComputePipeline({layout:`auto`,compute:{module:t.createShaderModule({code:e}),entryPoint:`main`}}),j.set(e,n)),n},N=new e(t,e=>{let n=[],a=e=>{let r=new Uint32Array(Math.ceil(e.length/4)*4);r.set(e);let i=t.createBuffer({size:r.byteLength,usage:GPUBufferUsage.UNIFORM|GPUBufferUsage.COPY_DST});return t.queue.writeBuffer(i,0,r),n.push(i),i},o=[],s=`stem`,c=(e,n,r,i,c=``)=>{let l=M(e),u=[...n,{buffer:a(r)}].map((e,t)=>({binding:t,resource:e})),d=`${s} ${Cr.get(e)}${c?` ${c}`:``}`;o.push({kind:`dispatch`,label:d,pipeline:l,group:t.createBindGroup({layout:l.getBindGroupLayout(0),entries:u}),count:i})},m=e=>[Math.ceil(e/64),1,1],h=(e,t,n)=>o.push({kind:`stage`,name:e,buffer:t,floats:n}),g=(e,t,n,r,a,o,s={posDiv:1,posMod:1,dmodel:0})=>{let[f,p]=d(t),{dtype:m,stride:h}=l(t);if(!h)throw Error(`beat_this weights: ${t} has no row stride (re-export the weights)`);let g=i.f16===`math`?mr:pr;c(m===`f16`?i.f16?g:dr:ur,[{buffer:e},u(t),n?u(n):{buffer:E},{buffer:r},{buffer:A}],[a,p,f,o,s.posDiv,s.posMod,s.dmodel,h],[Math.ceil(p/64),Math.ceil(a/64),1],t.split(`.`).slice(-2).join(`.`))},D=(e,t,n,r,a)=>{let o=r/32,l=3*r+o;s=t,g(e,`${t}.attn.qkvg.w`,`${t}.attn.qkvg.b`,b,n,12,{posDiv:a.posDiv,posMod:a.n,dmodel:r}),c(i.f16?i.f16===`math`?vr:_r:gr,[{buffer:b},{buffer:x}],[a.n,o,l,r,a.seqStride,a.batchStride],[Math.ceil(a.n/64),o,a.batches]),g(x,`${t}.attn.out.w`,null,e,n,2),g(e,`${t}.ff.w1`,`${t}.ff.b1`,S,n,5),g(S,`${t}.ff.w2`,`${t}.ff.b2`,e,n,2)};c(sr,[{buffer:_},u(`stem.bn.scale`),u(`stem.bn.shift`),{buffer:v}],[e,128],m(e*128));let O=32,k=p;c(cr,[{buffer:v},u(`stem.conv.w`),u(`stem.conv.b`),{buffer:y[0]}],[O,e,1,k,4],[Math.ceil(k/64),Math.ceil(O*e/64),1]);let j=0;h(`stem`,y[j],O*e*k);for(let t=0;t<3;t+=1){let n=O*e;D(y[j],`block${t}.freq`,n,k,{n:O,seqStride:e,batchStride:1,batches:e,posDiv:e}),h(`block${t}_freq`,y[j],n*k),D(y[j],`block${t}.time`,n,k,{n:e,seqStride:1,batchStride:e,batches:O,posDiv:1}),h(`block${t}_time`,y[j],n*k);let r=1-j;s=`block${t}`,c(cr,[{buffer:y[j]},u(`block${t}.conv.w`),u(`block${t}.conv.b`),{buffer:y[r]}],[O/2,e,k,k*2,2],[Math.ceil(k*2/64),Math.ceil(O/2*e/64),1]),j=r,O/=2,k*=2,h(`block${t}_conv`,y[j],O*e*k)}s=`frontend`,c(yr,[{buffer:y[j]},{buffer:C}],[O,e,k],m(O*e*k)),j=1-j,g(C,`frontend.linear.w`,`frontend.linear.b`,y[j],e,0),h(`frontend`,y[j],e*f);for(let t=0;t<r.hyper_parameters.n_layers;t+=1)D(y[j],`layer${t}`,e,f,{n:e,seqStride:1,batchStride:e,batches:1,posDiv:1}),h(`layer${t}`,y[j],e*f);return s=`head`,g(y[j],`head.w`,`head.b`,w,e,4),c(br,[{buffer:w},{buffer:T}],[e],m(e)),h(`logits`,T,e*2),{steps:o,owned:n}},_,T,o);return N.graph(a),await t.queue.onSubmittedWorkDone(),N}async run(e,t={}){let n=e.length/128;if(!Number.isInteger(n)||n<1||n>1500)throw Error(`beat_this chunk must be 1..${xr} frames x 128, got ${e.length} values`);let r=this.graph(n),{device:i}=this;i.queue.writeBuffer(this.melBuffer,0,e.buffer,e.byteOffset,e.byteLength);let a=i.createCommandEncoder(),o=[],s=t.profile&&i.features.has(`timestamp-query`)?r.filter(e=>e.kind===`dispatch`):[],c=s.length?i.createQuerySet({type:`timestamp`,count:s.length*2}):null,l=0,u=null,d=0,f=0,p=t.slice??`none`,m=p===`auto`?1:Math.max(1,t.inFlight??1),h=[],g=async(e=!1)=>{u?.end(),u=null;let t=performance.now();for(i.queue.submit([a.finish()]),h.push(i.queue.onSubmittedWorkDone());h.length>(e?0:m-1);)await h.shift();let n=performance.now()-t;return d+=n,f+=1,a=i.createCommandEncoder(),n},_=t.budgetMs??8;p===`auto`&&++this.autoRuns%50==0&&this.fineStages.clear();let v=0,y=performance.now();for(let e of r){if((p===`dispatch`||p===`auto`&&this.fineStages.has(v))&&u&&(await g(),await t.between?.()),e.kind===`dispatch`){c&&(u?.end(),u=a.beginComputePass({timestampWrites:{querySet:c,beginningOfPassWriteIndex:l,endOfPassWriteIndex:l+1}}),l+=2),u??=a.beginComputePass(),u.setPipeline(e.pipeline),u.setBindGroup(0,e.group),u.dispatchWorkgroups(...e.count);continue}if((p===`stage`||p===`auto`&&!this.fineStages.has(v))&&e.name!==`logits`){let e=await g();p===`auto`&&e>_&&this.fineStages.add(v),await t.between?.()}if(v+=1,!t.stages&&e.name!==`logits`)continue;u?.end(),u=null;let n=i.createBuffer({size:e.floats*4,usage:GPUBufferUsage.MAP_READ|GPUBufferUsage.COPY_DST});a.copyBufferToBuffer(e.buffer,0,n,0,e.floats*4),o.push({name:e.name,buffer:n})}u?.end();let b=null;if(c){let e=i.createBuffer({size:l*8,usage:GPUBufferUsage.QUERY_RESOLVE|GPUBufferUsage.COPY_SRC});b=i.createBuffer({size:l*8,usage:GPUBufferUsage.MAP_READ|GPUBufferUsage.COPY_DST}),a.resolveQuerySet(c,0,l,e,0),a.copyBufferToBuffer(e,0,b,0,l*8)}await g(!0);let x=performance.now()-y,S={};for(let{name:e,buffer:t}of o)await t.mapAsync(GPUMapMode.READ),S[e]=new Float32Array(t.getMappedRange().slice(0)),t.destroy();let C;if(b){await b.mapAsync(GPUMapMode.READ);let e=new BigInt64Array(b.getMappedRange());C=s.map((t,n)=>({label:t.kind===`dispatch`?t.label:``,ms:Number(e[n*2+1]-e[n*2])/1e6})),b.destroy(),c?.destroy()}let w=S.logits;return{beat:w.subarray(0,n),downbeat:w.subarray(n,2*n),gpuMs:d,wallMs:x,slices:f,...t.stages?{stages:S}:{},...C?{profile:C}:{}}}destroy(){this.owned.forEach(e=>e.destroy()),this.graphs.forEach(e=>e.owned.forEach(e=>e.destroy()))}};const Tr=1024,Er=/* @__PURE__ */ new WeakMap,Dr=async(e,t,n)=>{if(n<1||t.length<n*128)throw Error(`percussive: ${t.length} floats for ${n} frames`);let r=Er.get(e);r||(r=await e.createComputePipelineAsync({layout:`auto`,compute:{module:e.createShaderModule({code:`
struct Params { frames: u32, first: u32, count: u32 }
@group(0) @binding(0) var<storage, read> mel: array<f32>;
@group(0) @binding(1) var<storage, read_write> energy: array<f32>;
@group(0) @binding(2) var<uniform> p: Params;

const BANDS: i32 = 128;
const HALF: i32 = 8;
const TAPS: i32 = 17;

fn magnitude(frame: i32, band: i32) -> f32 {
  if (frame < 0 || frame >= i32(p.frames) || band < 0 || band >= BANDS) { return 0.0; }
  let v = mel[u32(frame * BANDS + band)];
  // expm1 without its cancellation near 0.
  if (v < 1e-3) { return max(0.0, v + 0.5 * v * v); }
  return exp(v) - 1.0;
}

fn median(values: ptr<function, array<f32, 17>>) -> f32 {
  for (var i = 1; i < TAPS; i++) {
    let value = (*values)[i];
    var j = i - 1;
    while (j >= 0 && (*values)[j] > value) {
      (*values)[j + 1] = (*values)[j];
      j--;
    }
    (*values)[j + 1] = value;
  }
  return (*values)[HALF];
}

@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3<u32>) {
  if (id.x >= p.count) { return; }
  let at = p.first + id.x;
  let frame = i32(at);
  var percussive = 0.0;
  var total = 0.0;
  var taps: array<f32, 17>;
  for (var band = 0; band < BANDS; band++) {
    for (var k = 0; k < TAPS; k++) { taps[k] = magnitude(frame + k - HALF, band); }
    let harmonic = median(&taps);
    for (var k = 0; k < TAPS; k++) { taps[k] = magnitude(frame, band + k - HALF); }
    let hit = median(&taps);
    let value = magnitude(frame, band);
    let e = value * value;
    let hh = harmonic * harmonic;
    let pp = hit * hit;
    if (hh + pp > 0.0) { percussive += e * pp / (hh + pp); }
    total += e;
  }
  energy[2u * at] = percussive;
  energy[2u * at + 1u] = total;
}
`}),entryPoint:`main`}}),Er.set(e,r));let i=e.createBuffer({size:n*128*4,usage:GPUBufferUsage.STORAGE|GPUBufferUsage.COPY_DST}),a=e.createBuffer({size:n*2*4,usage:GPUBufferUsage.STORAGE|GPUBufferUsage.COPY_SRC}),o=Math.ceil(n/Tr),s=e.createBuffer({size:o*256,usage:GPUBufferUsage.UNIFORM|GPUBufferUsage.COPY_DST}),c=e.createBuffer({size:n*2*4,usage:GPUBufferUsage.MAP_READ|GPUBufferUsage.COPY_DST});try{e.queue.writeBuffer(i,0,t.buffer,t.byteOffset,n*128*4);for(let t=0;t<o;t+=1){let l=t*Tr,u=Math.min(Tr,n-l);e.queue.writeBuffer(s,t*256,new Uint32Array([n,l,u,0]));let d=e.createBindGroup({layout:r.getBindGroupLayout(0),entries:[{binding:0,resource:{buffer:i}},{binding:1,resource:{buffer:a}},{binding:2,resource:{buffer:s,offset:t*256,size:16}}]}),f=e.createCommandEncoder(),p=f.beginComputePass();p.setPipeline(r),p.setBindGroup(0,d),p.dispatchWorkgroups(Math.ceil(u/64)),p.end(),t===o-1&&f.copyBufferToBuffer(a,0,c,0,n*2*4),e.queue.submit([f.finish()]),await e.queue.onSubmittedWorkDone()}await c.mapAsync(GPUMapMode.READ);let l=new Float32Array(c.getMappedRange().slice(0));c.unmap();let u=new Float32Array(n),d=new Float32Array(n);for(let e=0;e<n;e+=1)u[e]=l[2*e],d[e]=l[2*e+1];return{percussive:u,total:d}}finally{i.destroy(),a.destroy(),s.destroy(),c.destroy()}},Or=`
fn erf_f32(v: f32) -> f32 {
  let a = abs(v);
  let x = 1.0 / (1.0 + 0.3275911 * a);
  let y = 1.0 - ((((1.061405429 * x - 1.453152027) * x + 1.421413741) * x - 0.284496736) * x + 0.254829592) * x * exp(-a * a);
  return select(-y, y, v >= 0.0);
}
fn gelu(v: f32) -> f32 { return 0.5 * v * (1.0 + erf_f32(v * 0.7071067811865476)); }
`,kr=e=>e?`
@group(0) @binding(1) var<storage, read> w: array<u32>;
fn wload(e: u32) -> f32 {
  let pair = unpack2x16float(w[e >> 1u]);
  return select(pair.x, pair.y, (e & 1u) == 1u);
}`:`
@group(0) @binding(1) var<storage, read> w: array<f32>;
fn wload(e: u32) -> f32 { return w[e]; }`,Ar=e=>`
var<workgroup> as_: array<${e===`f16`?`vec4h`:`vec4f`}, 256>;
var<workgroup> bs_: array<${e===`f16`?`vec4h`:`vec4f`}, 256>;`,jr=(e,t)=>`${e===`f16`?`vec4h`:`vec4f`}(${t})`,Mr=e=>e===`f16`?`
    var part: array<vec4h, 4>;
    for (var kk = 0u; kk < 16u; kk++) {
      let a = as_[kk * 16u + l.y];
      let bb = bs_[kk * 16u + l.x];
      for (var i = 0u; i < 4u; i++) { part[i] = fma(vec4h(a[i]), bb, part[i]); }
    }
    for (var i = 0u; i < 4u; i++) { acc[i] += vec4f(part[i]); }`:`
    for (var kk = 0u; kk < 16u; kk++) {
      let a = as_[kk * 16u + l.y];
      let bb = bs_[kk * 16u + l.x];
      for (var i = 0u; i < 4u; i++) { acc[i] = fma(vec4f(a[i]), bb, acc[i]); }
    }`,Nr=e=>e===`f16`?`enable f16;`:``,Pr=(e,t)=>`
${Ar(t)}
@compute @workgroup_size(16, 16)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_id) l: vec3u, @builtin(local_invocation_index) li: u32) {
  let n = ${e};
  let row0 = wg.y * 64u;
  let col0 = wg.x * 64u;
  var acc: array<vec4f, 4>;
  for (var k0 = 0u; k0 < p.k; k0 += 16u) {
    // Invocation li: rows 4 (li / 16).. at k li % 16 of A, columns 4 (li % 16).. at k li / 16 of B.
    let ak = k0 + li % 16u;
    var av = vec4f(0.0);
    let bk = k0 + li / 16u;
    var bv = vec4f(0.0);
    for (var c = 0u; c < 4u; c++) {
      let ar = row0 + (li / 16u) * 4u + c;
      if (ar < p.m && ak < p.k) { av[c] = wload(ar * p.k + ak); }
      let bc = col0 + (li % 16u) * 4u + c;
      if (bk < p.k && bc < n) { bv[c] = gather(bk, bc); }
    }
    as_[(li % 16u) * 16u + li / 16u] = ${jr(t,`av`)};
    bs_[li] = ${jr(t,`bv`)};
    workgroupBarrier();${Mr(t)}
    workgroupBarrier();
  }
  for (var i = 0u; i < 4u; i++) {
    let r = row0 + l.y * 4u + i;
    if (r >= p.m) { continue; }
    for (var j = 0u; j < 4u; j++) {
      let c = col0 + l.x * 4u + j;
      if (c >= n) { continue; }
      store(r, c, acc[i][j]);
    }
  }
}
`,Fr=(e,t=`f32`)=>`
${Nr(t)}
${Or}
struct P { m: u32, k: u32, cin: u32, kh: u32, kw: u32, sh: u32, sw: u32, ph: u32, pw: u32, dh: u32, dw: u32, hin: u32, win: u32, hout: u32, wout: u32, flags: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
${kr(e)}
@group(0) @binding(2) var<storage, read> b: array<f32>;
@group(0) @binding(3) var<storage, read> side: array<f32>;
@group(0) @binding(4) var<storage, read_write> y: array<f32>;
@group(0) @binding(5) var<uniform> p: P;

fn gather(k: u32, n: u32) -> f32 {
  let taps = p.kh * p.kw;
  let ci = k / taps;
  let r = k - ci * taps;
  let a = r / p.kw;
  let c = r - a * p.kw;
  let ho = n / p.wout;
  let wo = n - ho * p.wout;
  let hi = i32(ho * p.sh + a * p.dh) - i32(p.ph);
  let wi = i32(wo * p.sw + c * p.dw) - i32(p.pw);
  if (hi < 0 || hi >= i32(p.hin) || wi < 0 || wi >= i32(p.win)) { return 0.0; }
  return x[(ci * p.hin + u32(hi)) * p.win + u32(wi)];
}

fn store(r: u32, c: u32, sum: f32) {
  var v = sum + b[r];
  if ((p.flags & 1u) != 0u) { v = gelu(v); }
  let o = r * p.hout * p.wout + c;
  if ((p.flags & 2u) != 0u) { y[o] = y[o] + side[r] * v; } else { y[o] = v; }
}
${Pr(`p.hout * p.wout`,t)}
`,Ir=(e,t=`f32`)=>`
${Nr(t)}
${Or}
struct P { m: u32, k: u32, cin: u32, hin: u32, win: u32, axis: u32, flags: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
${kr(e)}
@group(0) @binding(2) var<storage, read> b: array<f32>;
@group(0) @binding(3) var<storage, read_write> y: array<f32>;
@group(0) @binding(4) var<uniform> p: P;

fn gather(k: u32, n: u32) -> f32 {
  let ci = k / 2u;
  let t = k - ci * 2u;
  if (p.axis == 0u) {
    let q = n / p.win;
    let w = n - q * p.win;
    if (q < t || q - t >= p.hin) { return 0.0; }
    return x[(ci * p.hin + q - t) * p.win + w];
  }
  let h = n / (p.win + 1u);
  let q = n - h * (p.win + 1u);
  if (q < t || q - t >= p.win) { return 0.0; }
  return x[(ci * p.hin + h) * p.win + q - t];
}

fn store(r: u32, c: u32, sum: f32) {
  let co = r / 4u;
  let ph = r - co * 4u;
  var v = sum + b[co];
  if ((p.flags & 1u) != 0u) { v = gelu(v); }
  if (p.axis == 0u) {
    let q = c / p.win;
    let w = c - q * p.win;
    let o = 4u * q + ph;
    if (o < 2u || o - 2u >= 4u * p.hin) { return; }
    y[(co * 4u * p.hin + o - 2u) * p.win + w] = v;
  } else {
    let h = c / (p.win + 1u);
    let q = c - h * (p.win + 1u);
    let o = 4u * q + ph;
    if (o < 2u || o - 2u >= 4u * p.win) { return; }
    y[(co * p.hin + h) * 4u * p.win + o - 2u] = v;
  }
}
${Pr(`select(p.hin * (p.win + 1u), (p.hin + 1u) * p.win, p.axis == 0u)`,t)}
`,Lr=`
struct P { parts: u32, c: u32, inner: u32, cs: u32, gs: u32, mode: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
@group(0) @binding(1) var<storage, read> stats: array<f32>;
@group(0) @binding(2) var<storage, read_write> partial: array<f32>;
@group(0) @binding(3) var<uniform> p: P;
var<workgroup> sums: array<f32, 256>;
@compute @workgroup_size(256)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_index) li: u32) {
  let g = wg.y;
  let n = p.c * p.inner;
  let chunk = (n + p.parts - 1u) / p.parts;
  let lo = wg.x * chunk;
  let hi = min(n, lo + chunk);
  let mean = stats[g * 4u];
  var s = 0.0;
  for (var e = lo + li; e < hi; e += 256u) {
    let ch = e / p.inner;
    let v = x[ch * p.cs + g * p.gs + e - ch * p.inner];
    if (p.mode == 0u) { s += v; } else { let d = v - mean; s = fma(d, d, s); }
  }
  sums[li] = s;
  workgroupBarrier();
  for (var span = 128u; span > 0u; span >>= 1u) {
    if (li < span) { sums[li] += sums[li + span]; }
    workgroupBarrier();
  }
  if (li == 0u) { partial[g * p.parts + wg.x] = sums[0]; }
}
`,Rr=`
struct P { parts: u32, n: u32, mode: u32 }
@group(0) @binding(0) var<storage, read> partial: array<f32>;
@group(0) @binding(1) var<storage, read_write> stats: array<f32>;
@group(0) @binding(2) var<uniform> p: P;
var<workgroup> sums: array<f32, 256>;
@compute @workgroup_size(256)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_index) li: u32) {
  let g = wg.x;
  var s = 0.0;
  for (var i = li; i < p.parts; i += 256u) { s += partial[g * p.parts + i]; }
  sums[li] = s;
  workgroupBarrier();
  for (var span = 128u; span > 0u; span >>= 1u) {
    if (li < span) { sums[li] += sums[li + span]; }
    workgroupBarrier();
  }
  if (li != 0u) { return; }
  let total = sums[0];
  let n = f32(p.n);
  if (p.mode == 0u) {
    stats[g * 4u] = total / n;
  } else if (p.mode == 1u) {
    stats[g * 4u + 1u] = 1.0 / sqrt(total / n + 1e-5);
  } else {
    let sd = sqrt(total / (n - 1.0));
    stats[g * 4u + 1u] = 1.0 / (sd + 1e-5);
    stats[g * 4u + 2u] = sd;
  }
}
`,zr=`
${Or}
struct P { c: u32, h: u32, w: u32, perH: u32, gelu: u32 }
@group(0) @binding(0) var<storage, read_write> x: array<f32>;
@group(0) @binding(1) var<storage, read> stats: array<f32>;
@group(0) @binding(2) var<storage, read> gamma: array<f32>;
@group(0) @binding(3) var<storage, read> beta: array<f32>;
@group(0) @binding(4) var<uniform> p: P;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) id: vec3u, @builtin(num_workgroups) groups: vec3u) {
  let i = id.x + id.y * groups.x * 256u;
  let plane = p.h * p.w;
  if (i >= p.c * plane) { return; }
  let ch = i / plane;
  let g = select(0u, (i - ch * plane) / p.w, p.perH == 1u);
  var v = (x[i] - stats[g * 4u]) * stats[g * 4u + 1u] * gamma[ch] + beta[ch];
  if (p.gelu == 1u) { v = gelu(v); }
  x[i] = v;
}
`,Br=`
struct P { c: u32, h: u32, w: u32, perH: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
@group(0) @binding(1) var<storage, read> stats: array<f32>;
@group(0) @binding(2) var<storage, read> gamma: array<f32>;
@group(0) @binding(3) var<storage, read> beta: array<f32>;
@group(0) @binding(4) var<storage, read> scale: array<f32>;
@group(0) @binding(5) var<storage, read_write> res: array<f32>;
@group(0) @binding(6) var<uniform> p: P;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) id: vec3u, @builtin(num_workgroups) groups: vec3u) {
  let i = id.x + id.y * groups.x * 256u;
  let plane = p.h * p.w;
  if (i >= p.c * plane) { return; }
  let ch = i / plane;
  let at = i - ch * plane;
  let g = select(0u, at / p.w, p.perH == 1u);
  let mean = stats[g * 4u];
  let rstd = stats[g * 4u + 1u];
  let a = (x[i] - mean) * rstd * gamma[ch] + beta[ch];
  let gch = ch + p.c;
  let gate = (x[gch * plane + at] - mean) * rstd * gamma[gch] + beta[gch];
  res[i] = res[i] + scale[ch] * (a / (1.0 + exp(-gate)));
}
`,Vr=`
struct P { c: u32, h: u32, w: u32, emb: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
@group(0) @binding(1) var<storage, read> emb: array<f32>;
@group(0) @binding(2) var<storage, read_write> y: array<f32>;
@group(0) @binding(3) var<uniform> p: P;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) id: vec3u, @builtin(num_workgroups) groups: vec3u) {
  let i = id.x + id.y * groups.x * 256u;
  let plane = p.h * p.w;
  if (i >= p.c * plane) { return; }
  var v = x[i] / (1.0 + exp(-x[i + p.c * plane]));
  if (p.emb == 1u) {
    let ch = i / plane;
    v += emb[ch * p.h + (i - ch * plane) / p.w];
  }
  y[i] = v;
}
`,Hr=`
struct P { n: u32 }
@group(0) @binding(0) var<storage, read_write> x: array<f32>;
@group(0) @binding(1) var<storage, read> y: array<f32>;
@group(0) @binding(2) var<uniform> p: P;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) id: vec3u, @builtin(num_workgroups) groups: vec3u) {
  let i = id.x + id.y * groups.x * 256u;
  if (i < p.n) { x[i] = x[i] + y[i]; }
}
`,Ur=`
struct P { n: u32 }
@group(0) @binding(0) var<storage, read_write> x: array<f32>;
@group(0) @binding(1) var<storage, read> stats: array<f32>;
@group(0) @binding(2) var<uniform> p: P;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) id: vec3u, @builtin(num_workgroups) groups: vec3u) {
  let i = id.x + id.y * groups.x * 256u;
  if (i < p.n) { x[i] = (x[i] - stats[0]) * stats[1]; }
}
`,Wr=`
struct P { c: u32, n: u32, pos: u32 }
@group(0) @binding(0) var<storage, read> x: array<f32>;
@group(0) @binding(1) var<storage, read> gamma: array<f32>;
@group(0) @binding(2) var<storage, read> beta: array<f32>;
@group(0) @binding(3) var<storage, read> pos: array<f32>;
@group(0) @binding(4) var<storage, read_write> y: array<f32>;
@group(0) @binding(5) var<uniform> p: P;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let t = id.x;
  if (t >= p.n) { return; }
  var s = 0.0;
  for (var ch = 0u; ch < p.c; ch++) { s += x[ch * p.n + t]; }
  let mean = s / f32(p.c);
  var q = 0.0;
  for (var ch = 0u; ch < p.c; ch++) { let d = x[ch * p.n + t] - mean; q = fma(d, d, q); }
  let rstd = 1.0 / sqrt(q / f32(p.c) + 1e-5);
  for (var ch = 0u; ch < p.c; ch++) {
    var v = (x[ch * p.n + t] - mean) * rstd * gamma[ch] + beta[ch];
    if (p.pos == 1u) { v += pos[ch * p.n + t]; }
    y[ch * p.n + t] = v;
  }
}
`,Gr=(e=`f32`)=>`
${Nr(e)}
struct P { nq: u32, nk: u32, qr0: u32, kr0: u32, head0: u32 }
@group(0) @binding(0) var<storage, read> q: array<f32>;
@group(0) @binding(1) var<storage, read> kv: array<f32>;
@group(0) @binding(2) var<storage, read_write> s: array<f32>;
@group(0) @binding(3) var<uniform> p: P;
${Ar(e)} // [16 d][16 query quads], [16 d][16 key quads]
@compute @workgroup_size(16, 16)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_id) l: vec3u, @builtin(local_invocation_index) li: u32) {
  let h = p.head0 + wg.z;
  let row0 = wg.y * 64u;
  let col0 = wg.x * 64u;
  var acc: array<vec4f, 4>;
  for (var d0 = 0u; d0 < 64u; d0 += 16u) {
    // Invocation li: queries and keys 4 (li % 16).. at dimension li / 16.
    let d = d0 + li / 16u;
    var av = vec4f(0.0);
    var bv = vec4f(0.0);
    for (var c = 0u; c < 4u; c++) {
      let e = (li % 16u) * 4u + c;
      let i = row0 + e;
      let j = col0 + e;
      av[c] = select(0.0, q[(p.qr0 + h * 64u + d) * p.nq + min(i, p.nq - 1u)], i < p.nq);
      bv[c] = select(0.0, kv[(p.kr0 + h * 64u + d) * p.nk + min(j, p.nk - 1u)], j < p.nk);
    }
    as_[li] = ${jr(e,`av`)};
    bs_[li] = ${jr(e,`bv`)};
    workgroupBarrier();${Mr(e)}
    workgroupBarrier();
  }
  for (var i = 0u; i < 4u; i++) {
    let r = row0 + l.y * 4u + i;
    if (r >= p.nq) { continue; }
    for (var j = 0u; j < 4u; j++) {
      let c = col0 + l.x * 4u + j;
      if (c < p.nk) { s[(wg.z * p.nq + r) * p.nk + c] = acc[i][j]; }
    }
  }
}
`,Kr=`
struct P { nk: u32 }
@group(0) @binding(0) var<storage, read_write> s: array<f32>;
@group(0) @binding(1) var<uniform> p: P;
var<workgroup> red: array<f32, 256>;
@compute @workgroup_size(256)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(num_workgroups) groups: vec3u, @builtin(local_invocation_index) li: u32) {
  let base = (wg.x + wg.y * groups.x) * p.nk;
  var m = -3.0e38;
  for (var j = li; j < p.nk; j += 256u) { m = max(m, s[base + j]); }
  red[li] = m;
  workgroupBarrier();
  for (var span = 128u; span > 0u; span >>= 1u) {
    if (li < span) { red[li] = max(red[li], red[li + span]); }
    workgroupBarrier();
  }
  m = red[0];
  workgroupBarrier();
  var sum = 0.0;
  for (var j = li; j < p.nk; j += 256u) {
    let e = exp(s[base + j] - m);
    s[base + j] = e;
    sum += e;
  }
  red[li] = sum;
  workgroupBarrier();
  for (var span = 128u; span > 0u; span >>= 1u) {
    if (li < span) { red[li] += red[li + span]; }
    workgroupBarrier();
  }
  let inv = 1.0 / red[0];
  for (var j = li; j < p.nk; j += 256u) { s[base + j] *= inv; }
}
`,qr=(e=`f32`)=>`
${Nr(e)}
struct P { nq: u32, nk: u32, vr0: u32, head0: u32 }
@group(0) @binding(0) var<storage, read> s: array<f32>;
@group(0) @binding(1) var<storage, read> kv: array<f32>;
@group(0) @binding(2) var<storage, read_write> o: array<f32>;
@group(0) @binding(3) var<uniform> p: P;
${Ar(e)} // [16 keys][16 query quads], [16 keys][16 d quads]
@compute @workgroup_size(16, 16)
fn main(@builtin(workgroup_id) wg: vec3u, @builtin(local_invocation_id) l: vec3u, @builtin(local_invocation_index) li: u32) {
  let h = p.head0 + wg.z;
  let row0 = wg.y * 64u;
  var acc: array<vec4f, 4>;
  for (var j0 = 0u; j0 < p.nk; j0 += 16u) {
    // Invocation li: queries and dimensions 4 (li / 16).. at key li % 16.
    let jj = li % 16u;
    let j = j0 + jj;
    var a = vec4f(0.0);
    var b = vec4f(0.0);
    for (var c = 0u; c < 4u; c++) {
      let r = (li / 16u) * 4u + c;
      let i = row0 + r;
      if (j < p.nk) {
        if (i < p.nq) { a[c] = s[(wg.z * p.nq + i) * p.nk + j]; }
        b[c] = kv[(p.vr0 + h * 64u + r) * p.nk + j];
      }
    }
    as_[jj * 16u + li / 16u] = ${jr(e,`a`)};
    bs_[jj * 16u + li / 16u] = ${jr(e,`b`)};
    workgroupBarrier();${Mr(e)}
    workgroupBarrier();
  }
  for (var i = 0u; i < 4u; i++) {
    let r = row0 + l.y * 4u + i;
    if (r >= p.nq) { continue; }
    for (var j = 0u; j < 4u; j++) {
      let d = l.x * 4u + j;
      o[(h * 64u + d) * p.nq + r] = acc[i][j];
    }
  }
}
`,Jr=4096,Yr=`
struct P { frames: u32, len: u32 }
@group(0) @binding(0) var<storage, read> mix: array<f32>;      // [2, len]
@group(0) @binding(1) var<storage, read> win: array<f32>;
@group(0) @binding(2) var<storage, read> tw: array<vec2f>;
@group(0) @binding(3) var<storage, read_write> y: array<vec2f>;  // [2 frames, 64 k1, 64 n2]
@group(0) @binding(4) var<uniform> p: P;
fn reflect(i: i32, n: i32) -> i32 {
  var j = select(i, -i, i < 0);
  return select(j, 2 * (n - 1) - j, j >= n);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let n2 = id.x;
  let k1 = id.y;
  let s = id.z;
  if (n2 >= 64u || k1 >= 64u || s >= 2u * p.frames) { return; }
  let ch = s / p.frames;
  let f = s - ch * p.frames;
  let padded = i32(p.len) + 3072;
  var acc = vec2f(0.0);
  for (var n1 = 0u; n1 < 64u; n1++) {
    let n = n1 * 64u + n2;
    let at = reflect(reflect(i32((f + 2u) * 1024u + n) - 2048, padded) - 1536, i32(p.len));
    let v = mix[ch * p.len + u32(at)] * win[n];
    let t = tw[((k1 * n1) % 64u) * 64u];
    acc += v * vec2f(t.x, -t.y);
  }
  let t = tw[k1 * n2];
  y[(s * 64u + k1) * 64u + n2] = vec2f(acc.x * t.x + acc.y * t.y, acc.y * t.x - acc.x * t.y);
}
`,Xr=`
struct P { frames: u32 }
@group(0) @binding(0) var<storage, read> y: array<vec2f>;
@group(0) @binding(1) var<storage, read> tw: array<vec2f>;
@group(0) @binding(2) var<storage, read_write> spec: array<f32>;
@group(0) @binding(3) var<uniform> p: P;
@compute @workgroup_size(32)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let k2 = id.x;
  let k1 = id.y;
  let s = id.z;
  if (k2 >= 32u || k1 >= 64u || s >= 2u * p.frames) { return; }
  var acc = vec2f(0.0);
  for (var n2 = 0u; n2 < 64u; n2++) {
    let v = y[(s * 64u + k1) * 64u + n2];
    let t = tw[((k2 * n2) % 64u) * 64u];
    acc += vec2f(v.x * t.x + v.y * t.y, v.y * t.x - v.x * t.y);
  }
  let ch = s / p.frames;
  let f = s - ch * p.frames;
  let k = k1 + 64u * k2;
  spec[((ch * 2u) * 2048u + k) * p.frames + f] = acc.x;
  spec[((ch * 2u + 1u) * 2048u + k) * p.frames + f] = acc.y;
}
`,Zr=`
struct P { frames: u32, source: u32 }
@group(0) @binding(0) var<storage, read> z: array<f32>;     // [16, 2048, frames]
@group(0) @binding(1) var<storage, read> stats: array<f32>;
@group(0) @binding(2) var<storage, read> tw: array<vec2f>;
@group(0) @binding(3) var<storage, read_write> y: array<vec2f>;
@group(0) @binding(4) var<uniform> p: P;
fn bin(ch: u32, f: u32, k: u32) -> vec2f {
  let kk = select(k, 4096u - k, k > 2048u);
  if (kk == 2048u) { return vec2f(0.0); }
  let base = (p.source * 4u + ch * 2u) * 2048u + kk;
  let re = z[base * p.frames + f] * stats[2] + stats[0];
  let im = z[(base + 2048u) * p.frames + f] * stats[2] + stats[0];
  return select(vec2f(re, im), vec2f(re, -im), k > 2048u);
}
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let n2 = id.x;
  let k1 = id.y;
  let s = id.z;
  if (n2 >= 64u || k1 >= 64u || s >= 2u * p.frames) { return; }
  let ch = s / p.frames;
  let f = s - ch * p.frames;
  var acc = vec2f(0.0);
  for (var k2 = 0u; k2 < 64u; k2++) {
    let v = bin(ch, f, k1 + 64u * k2);
    let t = tw[((k2 * n2) % 64u) * 64u];
    acc += vec2f(v.x * t.x - v.y * t.y, v.x * t.y + v.y * t.x);
  }
  let t = tw[k1 * n2];
  y[(s * 64u + k1) * 64u + n2] = vec2f(acc.x * t.x - acc.y * t.y, acc.x * t.y + acc.y * t.x);
}
`,Qr=`
struct P { frames: u32 }
@group(0) @binding(0) var<storage, read> y: array<vec2f>;
@group(0) @binding(1) var<storage, read> tw: array<vec2f>;
@group(0) @binding(2) var<storage, read> win: array<f32>;
@group(0) @binding(3) var<storage, read_write> out: array<f32>;  // [2 frames, 4096]
@group(0) @binding(4) var<uniform> p: P;
@compute @workgroup_size(64)
fn main(@builtin(global_invocation_id) id: vec3u) {
  let n2 = id.x;
  let n1 = id.y;
  let s = id.z;
  if (n2 >= 64u || n1 >= 64u || s >= 2u * p.frames) { return; }
  var acc = 0.0;
  for (var k1 = 0u; k1 < 64u; k1++) {
    let v = y[(s * 64u + k1) * 64u + n2];
    let t = tw[((k1 * n1) % 64u) * 64u];
    acc += v.x * t.x - v.y * t.y;
  }
  let n = n1 * 64u + n2;
  out[s * 4096u + n] = acc / 4096.0 * win[n];
}
`,$r=`
struct P { frames: u32, len: u32, source: u32 }
@group(0) @binding(0) var<storage, read> fr: array<f32>;
@group(0) @binding(1) var<storage, read> env: array<f32>;
@group(0) @binding(2) var<storage, read> xt: array<f32>;      // [8, len]
@group(0) @binding(3) var<storage, read> tstats: array<f32>;
@group(0) @binding(4) var<storage, read_write> out: array<f32>; // [2, len]
@group(0) @binding(5) var<uniform> p: P;
@compute @workgroup_size(256)
fn main(@builtin(global_invocation_id) id: vec3u, @builtin(num_workgroups) groups: vec3u) {
  let i = id.x + id.y * groups.x * 256u;
  if (i >= 2u * p.len) { return; }
  let ch = i / p.len;
  let j = i - ch * p.len;
  let pos = 3584u + j;
  var acc = 0.0;
  let last = pos / 1024u;
  for (var jj = select(0u, last - 3u, last >= 3u); jj <= last; jj++) {
    if (jj < 2u || jj >= p.frames + 2u) { continue; }
    let n = pos - jj * 1024u;
    if (n < 4096u) { acc += fr[(ch * p.frames + jj - 2u) * 4096u + n]; }
  }
  let t = xt[(p.source * 2u + ch) * p.len + j] * tstats[2] + tstats[0];
  out[i] = acc / env[1536u + j] + t;
}
`,ei=172032,$=2048,ti=e=>e.reduce((e,t)=>e*t,1);var ni=class e{constructor(e,t,n,r,i){this.device=e,this.steps=t,this.mixBuffer=n,this.outBuffer=r,this.owned=i,this.fineStages=/* @__PURE__ */ new Set,this.autoRuns=0}static async create(t,n,r,i={}){if(r.segment!==172032)throw Error(`htdemucs weights are for a ${r.segment}-sample segment, not ${ei}`);if(i.f16&&!t.features.has(`shader-f16`))throw Error(`htdemucs f16 needs a device opened with shader-f16`);let a=i.source??0,o=i.f16?`f16`:`f32`,s=Gr(o),c=qr(o);t.pushErrorScope(`validation`);let l=ei,u=[],d=e=>{let n=t.createBuffer({size:Math.max(16,Math.ceil(e)*4),usage:GPUBufferUsage.STORAGE|GPUBufferUsage.COPY_SRC|GPUBufferUsage.COPY_DST});return u.push(n),n},f=d(Math.ceil(n.byteLength/4));t.queue.writeBuffer(f,0,n.buffer,n.byteOffset,n.byteLength);let p=e=>{let t=r.tensors[e];if(!t)throw Error(`htdemucs weights: missing tensor ${e}`);return t},m=e=>{let{byte_offset:t,shape:n,dtype:r}=p(e),i=ti(n)*(r===`f16`?2:4);return{buffer:f,offset:t,size:Math.ceil(i/4)*4}},h=e=>p(e).dtype===`f16`,g=e=>p(e).shape[0],_=new Float32Array(Jr*2);for(let e=0;e<Jr;e+=1)_[e*2]=Math.cos(2*Math.PI*e/Jr),_[e*2+1]=Math.sin(2*Math.PI*e/Jr);let v=d(_.length);t.queue.writeBuffer(v,0,_);let y=$/4*48*168,b=d(2*l),x=d(2*l),S=d(4),C=d(4),w=d(4*$),T=d(4096),E=d(64),D=d(2752512),O=d(4*$*168),k=d(y),A=d(y/8),j=d(2*y),M=[d(16*$*168),d(16*$*168)],N=d(16*$*168),P=d(8*l),ee=d(1376256),F=[],I=[];for(let e=0;e<4;e+=1)F.push(d(48*2**e*($/4**(e+1))*168)),I.push(d(48*2**e*(l/4**(e+1))));let L=1344,R=d(512*L),z=d(344064),B=d(512*L),V=d(344064),te=d(512*L),ne=d(344064),re=d(1536*L),ie=d(1032192),ae=d(688128),oe=d(1024*L),se=d(512*L),ce=d(344064),le=d(2048*L),ue=d(4*L*L),de=/* @__PURE__ */ new Map,fe=e=>{let n=de.get(e);return n||(n=t.createComputePipeline({layout:`auto`,compute:{module:t.createShaderModule({code:e}),entryPoint:`main`}}),de.set(e,n)),n},pe=e=>{let n=new Uint32Array(Math.ceil(e.length/4)*4);n.set(e);let r=t.createBuffer({size:n.byteLength,usage:GPUBufferUsage.UNIFORM|GPUBufferUsage.COPY_DST});return t.queue.writeBuffer(r,0,n),u.push(r),r},H=[],U=`input`,me=/* @__PURE__ */ new Map([[Hr,`add`],[c,`attn_out`],[s,`attn_scores`],[Kr,`softmax`],[Vr,`glu`],[Br,`gn_glu`],[zr,`gn`],[Zr,`istft_a`],[Qr,`istft_b`],[Wr,`layer_norm`],[Ur,`normalize`],[$r,`overlap_add`],[Rr,`stats_final`],[Lr,`stats`],[Yr,`stft_a`],[Xr,`stft_b`]]),W=(e,n,r,i,a=``)=>{let o=fe(e),s=[...n,pe(r)].map((e,t)=>({binding:t,resource:e instanceof GPUBuffer?{buffer:e}:e})),c=`${U} ${me.get(e)??`gemm`}${a?` ${a}`:``}`;H.push({kind:`dispatch`,label:c,pipeline:o,group:t.createBindGroup({layout:o.getBindGroupLayout(0),entries:s}),count:i})},G=e=>{let t=Math.ceil(e/256),n=Math.min(t,32768);return[n,Math.ceil(t/n),1]},K=(e,t,n)=>H.push({kind:`stage`,name:e,buffer:t,floats:n}),he=(e,t,n,r,i,a,o,s)=>{let c=r*i,l=Math.max(1,Math.min(256,Math.floor(4096/n),Math.ceil(c/16384)));W(Lr,[e,t,T],[l,r,i,a,o,0],[l,n,1]),W(Rr,[T,t],[l,c,0],[n,1,1]),W(Lr,[e,t,T],[l,r,i,a,o,1],[l,n,1]),W(Rr,[T,t],[l,c,s],[n,1,1])},ge=(e,t,n,r,i)=>{i?he(e,w,n,t,r,n*r,r,1):he(e,w,1,1,t*n*r,0,0,1)},q=(e,t,n,r,i=0,a=E)=>{let{sh:s=1,sw:c=1,ph:l=0,pw:u=0,dh:d=1,dw:f=1}=r,_=g(`${t}.w`),v=p(`${t}.w`).shape[1],y=v/(r.kh*r.kw),b=Math.floor((r.hin+2*l-d*(r.kh-1)-1)/s)+1,x=Math.floor((r.win+2*u-f*(r.kw-1)-1)/c)+1;return W(Fr(h(`${t}.w`),o),[e,m(`${t}.w`),m(`${t}.b`),a,n],[_,v,y,r.kh,r.kw,s,c,l,u,d,f,r.hin,r.win,b,x,i],[Math.ceil(b*x/64),Math.ceil(_/64),1],t.split(`.`).slice(1).join(`.`)),[b,x]},J=(e,t,n,r,i,a,s)=>{let c=g(`${t}.w`),l=p(`${t}.w`).shape[1],u=a===0?(r+1)*i:r*(i+1);W(Ir(h(`${t}.w`),o),[e,m(`${t}.w`),m(`${t}.b`),n],[c,l,l/2,r,i,a,s],[Math.ceil(u/64),Math.ceil(c/64),1],`tr`)},Y=(e,t,n,r,i)=>{for(let a=0;a<2;a+=1){let o=`${e}.dc${a}`,s=2**a,c=g(`${o}.c1.w`);q(k,`${o}.c1`,A,{hin:n,win:r,kh:1,kw:3,pw:s,dw:s}),ge(A,c,n,r,i),W(zr,[A,w,m(`${o}.n1.g`),m(`${o}.n1.b`)],[c,n,r,+!!i,1],G(c*n*r)),q(A,`${o}.c2`,j,{hin:n,win:r,kh:1,kw:1}),ge(j,2*t,n,r,i),W(Br,[j,w,m(`${o}.n2.g`),m(`${o}.n2.b`),m(`${o}.ls`),k],[t,n,r,+!!i],G(t*n*r))}},_e=P;he(b,S,1,1,2*l,0,0,2),H.push({kind:`copy`,from:b,to:_e,bytes:2*l*4}),W(Ur,[_e,S],[2*l],G(2*l)),W(Yr,[b,m(`stft.window`),v,D],[168,l],[1,64,336]),W(Xr,[D,v,O],[168],[1,64,336]),he(O,C,1,1,4*$*168,0,0,2),W(Ur,[O,C],[4*$*168],G(4*$*168)),K(`norm_spec`,O,4*$*168);let ve=_e,ye=O,X=l,Z=$;for(let e=0;e<4;e+=1){let t=48*2**e;U=`tenc${e}`;let[,n]=q(ve,`tenc${e}.conv`,k,{hin:1,win:X,kh:1,kw:8,sw:4,pw:2},1);X=n,Y(`tenc${e}`,t,1,X,!1),q(k,`tenc${e}.rw`,j,{hin:1,win:X,kh:1,kw:1}),W(Vr,[j,E,I[e]],[t,1,X,0],G(t*X)),K(`tenc${e}`,I[e],t*X),ve=I[e],U=`enc${e}`;let[r]=q(ye,`enc${e}.conv`,k,{hin:Z,win:168,kh:8,kw:1,sh:4,ph:2},1);Z=r,Y(`enc${e}`,t,Z,168,!0),q(k,`enc${e}.rw`,j,{hin:Z,win:168,kh:1,kw:1}),W(Vr,[j,e===0?m(`freq_emb`):E,F[e]],[t,Z,168,+(e===0)],G(t*Z*168)),K(`enc${e}`,F[e],t*Z*168),ye=F[e]}U=`tf_in`,q(F[3],`up`,B,{hin:1,win:L,kh:1,kw:1}),q(I[3],`up_t`,V,{hin:1,win:672,kh:1,kw:1}),W(Wr,[B,m(`norm_in.g`),m(`norm_in.b`),m(`pos`),R],[512,L,1],[21,1,1]),W(Wr,[V,m(`norm_in_t.g`),m(`norm_in_t.b`),m(`pos_t`),z],[512,672,1],[11,1,1]),K(`tf_in`,R,512*L),K(`tf_in_t`,z,344064);let be=(e,t,n,r)=>W(Wr,[e,m(`${t}.g`),m(`${t}.b`),E,n],[512,r,0],[Math.ceil(r/64),1,1]),Q=(e,t,n,r,i=0,a=E)=>q(e,t,n,{hin:1,win:r,kh:1,kw:1},i,a),xe=(e,t,n,r,i,a,o,l)=>{for(let u=0;u<8;u+=4){let d=`heads ${u}-${u+4-1}`;W(s,[e,t,ue],[r,i,a,o,u],[Math.ceil(i/64),Math.ceil(r/64),4],d);let f=4*r,p=Math.min(f,32768);W(Kr,[ue],[i],[p,Math.ceil(f/p),1],d),W(c,[ue,t,n],[r,i,l,u],[1,Math.ceil(r/64),4],d)}},Se=(e,t,n,r)=>{be(e,`${t}.nf`,r,n),Q(r,`${t}.ff1`,le,n,1),Q(le,`${t}.ff2`,e,n,2,m(`${t}.g2`)),he(e,w,1,1,512*n,0,0,1),W(zr,[e,w,m(`${t}.no.g`),m(`${t}.no.b`)],[512,1,n,0,0],G(512*n))};for(let e=0;e<5;e+=1){let t=`tf${e}`,n=`tft${e}`;if(U=t,e%2==0)for(let[e,r,i,a,o,s]of[[R,t,L,B,re,se],[z,n,672,V,ie,ce]])be(e,`${r}.n1`,a,i),Q(a,`${r}.qkv`,o,i),xe(o,o,s,i,i,0,512,1024),Q(s,`${r}.out`,e,i,2,m(`${r}.g1`)),Se(e,r,i,a);else be(R,`${t}.n1`,B,L),Q(B,`${t}.q`,re,L),be(z,`${t}.nkv`,ne,672),Q(ne,`${t}.kv`,ae,672),be(z,`${n}.n1`,V,672),Q(V,`${n}.q`,ie,672),be(R,`${n}.nkv`,te,L),Q(te,`${n}.kv`,oe,L),xe(re,ae,se,L,672,0,0,512),xe(ie,oe,ce,672,L,0,0,512),Q(se,`${t}.out`,R,L,2,m(`${t}.g1`)),Q(ce,`${n}.out`,z,672,2,m(`${n}.g1`)),Se(R,t,L,B),Se(z,n,672,V);K(`tf${e}`,R,512*L),K(`tft${e}`,z,344064)}let Ce=M[0];U=`dec0`,Q(R,`down`,Ce,L);for(let e=0;e<4;e+=1){let t=48*2**(3-e);U=`dec${e}`;let n=Z*168;W(Hr,[Ce,F[3-e]],[t*n],G(t*n)),q(Ce,`dec${e}.rw`,j,{hin:Z,win:168,kh:3,kw:3,ph:1,pw:1}),W(Vr,[j,E,k],[t,Z,168,0],G(t*n)),Y(`dec${e}`,t,Z,168,!0);let r=e===3?N:M[(e+1)%2];J(k,`dec${e}.tr`,r,Z,168,0,e===3?0:1),Z*=4,K(`dec${e}`,r,g(`dec${e}.tr.w`)/4*Z*168),Ce=r}Ce=M[0],U=`tdec0`,Q(z,`down_t`,Ce,672);for(let e=0;e<4;e+=1){let t=48*2**(3-e);U=`tdec${e}`,W(Hr,[Ce,I[3-e]],[t*X],G(t*X)),q(Ce,`tdec${e}.rw`,j,{hin:1,win:X,kh:1,kw:3,pw:1}),W(Vr,[j,E,k],[t,1,X,0],G(t*X)),Y(`tdec${e}`,t,1,X,!1);let n=e===3?P:M[(e+1)%2];J(k,`tdec${e}.tr`,n,1,X,1,e===3?0:1),X*=4,K(`tdec${e}`,n,g(`tdec${e}.tr.w`)/4*X),Ce=n}U=`output`,W(Zr,[N,C,v,D],[168,a],[1,64,336]),W(Qr,[D,v,m(`istft.window`),ee],[168],[1,64,336]),W($r,[ee,m(`istft.env`),P,S,x],[168,l,a],G(2*l)),K(`out`,x,2*l);let we=await t.popErrorScope();if(we)throw u.forEach(e=>e.destroy()),Error(`htdemucs graph rejected: ${we.message}`);let Te=new e(t,H,b,x,u);return await t.queue.onSubmittedWorkDone(),Te}async run(e,t,n={}){let r=ei;if(e.length!==r||t.length!==r)throw Error(`htdemucs segment must be ${r} samples per channel, got ${e.length} and ${t.length}`);let{device:i}=this;i.queue.writeBuffer(this.mixBuffer,0,e.buffer,e.byteOffset,e.byteLength),i.queue.writeBuffer(this.mixBuffer,r*4,t.buffer,t.byteOffset,t.byteLength);let a=i.createCommandEncoder(),o=null,s=[],c=0,l=0,u=n.slice??`none`,d=async()=>{o?.end(),o=null;let e=performance.now();i.queue.submit([a.finish()]),await i.queue.onSubmittedWorkDone();let t=performance.now()-e;return c+=t,l+=1,a=i.createCommandEncoder(),t},f=n.budgetMs??8,p=[],m=``;u===`auto`&&++this.autoRuns%50==0&&this.fineStages.clear();let h=0,g=performance.now();for(let e of this.steps){if((u===`dispatch`||u===`auto`&&this.fineStages.has(h))&&o){let e=await d();n.timings&&p.push({label:m,ms:e})}if(e.kind===`dispatch`){m=e.label,o??=a.beginComputePass(),o.setPipeline(e.pipeline),o.setBindGroup(0,e.group),o.dispatchWorkgroups(...e.count);continue}if(e.kind===`copy`){o?.end(),o=null,a.copyBufferToBuffer(e.from,0,e.to,0,e.bytes);continue}if((u===`stage`||u===`auto`&&!this.fineStages.has(h))&&e.name!==`out`){let e=await d();u===`auto`&&e>f&&this.fineStages.add(h)}if(h+=1,!n.stages&&e.name!==`out`)continue;o?.end(),o=null;let t=i.createBuffer({size:e.floats*4,usage:GPUBufferUsage.MAP_READ|GPUBufferUsage.COPY_DST});a.copyBufferToBuffer(e.buffer,0,t,0,e.floats*4),s.push({name:e.name,buffer:t})}await d();let _=performance.now()-g,v={};for(let{name:e,buffer:t}of s)await t.mapAsync(GPUMapMode.READ),v[e]=new Float32Array(t.getMappedRange().slice(0)),t.destroy();let y=v.out;return n.stages||delete v.out,{left:y.subarray(0,r),right:y.subarray(r,2*r),gpuMs:c,wallMs:_,slices:l,...n.stages?{stages:v}:{},...n.timings?{timings:p}:{}}}destroy(){this.owned.forEach(e=>e.destroy())}};const ri=/* @__PURE__ */ new Map;let ii=null,ai=null,oi=``,si=!1,ci=null;const li=async()=>{if(ai)return ai;if(!navigator.gpu)throw Error(`no WebGPU in this worker`);let e=await navigator.gpu.requestAdapter({powerPreference:`high-performance`});if(!e)throw Error(`no WebGPU adapter`);si=e.features.has(`shader-f16`);let t=await e.requestDevice({requiredFeatures:si?[`shader-f16`]:[]});return t.lost.then(e=>{ci=`device lost (${e.reason}): ${e.message}`,ri.clear(),ii=null}),oi=`${e.info.vendor} ${e.info.architecture}${si?` f16`:``}`,ai=t,t};self.onmessage=async e=>{let t=e.data;try{if(t.kind===`load`){let e;try{e=await li()}catch(e){self.postMessage({id:t.id,error:String(e?.message??e),scope:`device`});return}if(t.model===`htdemucs`){if(!ii)try{ii=await ni.create(e,t.weights,t.manifest,{f16:si})}catch(e){self.postMessage({id:t.id,error:String(e?.message??e),scope:`model`});return}self.postMessage({id:t.id,result:oi});return}if(!ri.has(t.model))try{ri.set(t.model,await wr.create(e,t.weights,t.manifest,{f16:si?`storage`:void 0}))}catch(e){self.postMessage({id:t.id,error:String(e?.message??e),scope:`model`});return}self.postMessage({id:t.id,result:oi});return}if(t.kind===`percussive`){let e;try{e=await li()}catch(e){self.postMessage({id:t.id,error:String(e?.message??e),scope:`device`});return}if(ci)throw Error(ci);let n=await Dr(e,t.mel,t.frames);self.postMessage({id:t.id,result:n},{transfer:[n.percussive.buffer,n.total.buffer]});return}if(t.kind===`separate`){if(ci)throw Error(ci);if(!ii)throw Error(`htdemucs not loaded`);let e=await ii.run(t.left,t.right,{slice:`auto`}),n=e.left.slice(),r=e.right.slice();self.postMessage({id:t.id,result:{left:n,right:r}},{transfer:[n.buffer,r.buffer]});return}if(t.kind===`dispose`){for(let e of ri.values())e.destroy();ri.clear(),ii?.destroy(),ii=null,ai?.destroy(),self.postMessage({id:t.id,result:!0});return}let e=ri.get(t.model),n=[],r=[];for(let i of t.chunks){if(ci)throw Error(ci);if(!e)throw Error(`${t.model} not loaded`);let a=await e.run(i,{slice:`auto`});n.push(a.beat.slice()),r.push(a.downbeat.slice())}let i;if(t.percussive&&ai&&!ci)try{i=await Dr(ai,t.percussive.mel,t.percussive.frames)}catch(e){console.warn(`[beat-this-gpu] percussive energy failed: ${e?.message??e}`)}let a=[...n,...r,...i?[i.percussive,i.total]:[]];self.postMessage({id:t.id,result:{beat:n,downbeat:r,percussive:i}},{transfer:a.map(e=>e.buffer)})}catch(e){self.postMessage({id:t.id,error:String(e?.message??e)})}};