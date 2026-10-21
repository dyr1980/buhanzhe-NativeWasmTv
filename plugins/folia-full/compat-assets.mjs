/** Lower Android's targetSdk<28 hex-alpha quirk in CSS values embedded in JS/HTML. */
import fs from 'node:fs';
import path from 'node:path';
import {parse} from '@babel/parser';
import rewritePattern from 'regexpu-core';
import {transformSync} from '@babel/core';
import asyncToGenerator from '@babel/plugin-transform-async-to-generator';
import asyncGenerators from '@babel/plugin-transform-async-generator-functions';
import {lowerCss} from './compat-css.mjs';
import systemModules from '@babel/plugin-transform-modules-systemjs';
import dynamicImport from '@babel/plugin-transform-dynamic-import';
import presetEnv from '@babel/preset-env';
const root=process.argv[2];
const pattern=/#([\da-f]{8}|[\da-f]{4})(?![\da-f])/gi;
function lower(value){return value.replace(pattern,(_,hex)=>{
 if(hex.length===4)hex=hex.split('').map(c=>c+c).join('');
 const rgba=[0,2,4].map(i=>parseInt(hex.slice(i,i+2),16));
 return `rgba(${rgba.join(',')},${Number((parseInt(hex.slice(6),16)/255).toFixed(6))})`;
});}
function cssValue(value){return /^#[\da-f]{4}(?:[\da-f]{4})?$/i.test(value)||/(?:gradient\(|(?:background|color|shadow|border|fill|stroke|--[\w-]+)\s*[:=])/.test(value);}
let literals=0,files=0;
async function visitFile(file){
 const original=fs.readFileSync(file,'utf8');let output=original;
 if(file.endsWith('.js')&&(/#[\da-f]{4}/i.test(original)||/\\[pP]\{/.test(original)||original.includes('@layer'))){
  const tree=parse(original,{sourceType:'unambiguous'}),edits=[],jobs=[];
  function runtimeCss(node,value){
   if(!/@layer\s+[\w-]+\s*\{/.test(value))return false;
   jobs.push(lowerCss(value,undefined,true).then(css=>{edits.push({start:node.start,end:node.end,value:JSON.stringify(lower(css))});}));
   return true;
  }
  function walk(node){
   if(!node||typeof node!=='object')return;
   // Some text-layout libraries construct Unicode regexes from constant strings.
   if((node.type==='CallExpression'||node.type==='NewExpression')&&node.callee?.name==='RegExp'){
    const text=n=>n?.type==='StringLiteral'?n.value:n?.type==='TemplateLiteral'&&n.expressions.length===0?n.quasis[0].value.cooked:null;
    const source=text(node.arguments[0]),flags=node.arguments.length>1?text(node.arguments[1]):'';
    if(source!==null&&flags!==null&&/\\[pP]\{/.test(source)){
     const pattern=rewritePattern(source,flags,{unicodePropertyEscapes:'transform',unicodeSetsFlag:'transform'});
     edits.push({start:node.start,end:node.end,value:`RegExp(${JSON.stringify(pattern)},${JSON.stringify(flags.replace('v','u'))})`});return;
    }
   }
   // Chromium 68 recognizes Unicode escapes but lacks newer properties such as Extended_Pictographic.
   if(node.type==='RegExpLiteral'&&/\\[pP]\{/.test(node.pattern)){
    const pattern=rewritePattern(node.pattern,node.flags,{unicodePropertyEscapes:'transform',unicodeSetsFlag:'transform'});
    edits.push({start:node.start,end:node.end,value:`/${pattern}/${node.flags.replace('v','u')}`});
   }
   if(node.type==='StringLiteral'&&runtimeCss(node,node.value))return;
   if(node.type==='StringLiteral'&&cssValue(node.value)){
    const value=lower(node.value);
    if(value!==node.value){edits.push({start:node.start,end:node.end,value:JSON.stringify(value)});literals++;}
   }
   if(node.type==='TemplateLiteral'&&node.expressions.length===0){
    const original=node.quasis[0].value.cooked;
    if(original!==null&&runtimeCss(node,original))return;
    if(original!==null&&cssValue(original)){const value=lower(original);if(value!==original){edits.push({start:node.start,end:node.end,value:JSON.stringify(value)});literals++;}}
   }
   // Interpolated template fragments are left intact: they are not complete values.
   for(const [key,value] of Object.entries(node)){
    if(key==='loc'||key==='extra'||key==='tokens'||key==='comments')continue;
    if(Array.isArray(value))for(const child of value)walk(child);else if(value&&typeof value==='object')walk(value);
   }
  }
  walk(tree);
  await Promise.all(jobs);
  for(const edit of edits.sort((a,b)=>b.start-a.start))output=output.slice(0,edit.start)+edit.value+output.slice(edit.end);
 }else if(file.endsWith('.html')){
  output=output.replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,block=>lower(block));
 }
 // Old Chromium's native await escapes Dexie's Promise scope and closes IDB transactions early.
 // Generator-based async follows the scoped Promise constructor throughout each transaction.
 if(file.endsWith('.js')){
  const classic=/\.worker-[\w-]+\.js$/.test(file)||['runtime-config.js','folia-cover-sw.js'].includes(path.basename(file));
  output=transformSync(output,{filename:file,configFile:false,babelrc:false,comments:false,compact:true,presets:[[presetEnv,{targets:{chrome:'61'},modules:false}]],plugins:[asyncGenerators,asyncToGenerator,...(classic?[]:[dynamicImport,systemModules])]}).code;
 }
 if(output!==original){fs.writeFileSync(file,output);files++;}
}
async function walkDir(dir){for(const item of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,item.name);if(item.isDirectory())await walkDir(file);else if(/\.(?:js|html)$/.test(file))await visitFile(file);}}
await walkDir(root);
console.log(`Android color compatibility: ${literals} CSS string literals in ${files} generated files`);
