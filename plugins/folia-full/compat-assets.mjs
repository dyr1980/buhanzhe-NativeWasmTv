/** Lower Android's targetSdk<28 hex-alpha quirk in CSS values embedded in JS/HTML. */
import fs from 'node:fs';
import path from 'node:path';
import {parse} from '@babel/parser';
const root=process.argv[2];
const pattern=/#([\da-f]{8}|[\da-f]{4})(?![\da-f])/gi;
function lower(value){return value.replace(pattern,(_,hex)=>{
 if(hex.length===4)hex=hex.split('').map(c=>c+c).join('');
 const rgba=[0,2,4].map(i=>parseInt(hex.slice(i,i+2),16));
 return `rgba(${rgba.join(',')},${Number((parseInt(hex.slice(6),16)/255).toFixed(6))})`;
});}
function cssValue(value){return /^#[\da-f]{4}(?:[\da-f]{4})?$/i.test(value)||/(?:gradient\(|(?:background|color|shadow|border|fill|stroke|--[\w-]+)\s*[:=])/.test(value);}
let literals=0,files=0;
function visitFile(file){
 const original=fs.readFileSync(file,'utf8');let output=original;
 if(file.endsWith('.js')&&/#[\da-f]{4}/i.test(original)){
  const tree=parse(original,{sourceType:'unambiguous'}),edits=[];
  function walk(node){
   if(!node||typeof node!=='object')return;
   if(node.type==='StringLiteral'&&cssValue(node.value)){
    const value=lower(node.value);
    if(value!==node.value){edits.push({start:node.start,end:node.end,value:JSON.stringify(value)});literals++;}
   }
   if(node.type==='TemplateLiteral'&&node.expressions.length===0){
    const original=node.quasis[0].value.cooked;
    if(original!==null&&cssValue(original)){const value=lower(original);if(value!==original){edits.push({start:node.start,end:node.end,value:JSON.stringify(value)});literals++;}}
   }
   // Interpolated template fragments are left intact: they are not complete values.
   for(const [key,value] of Object.entries(node)){
    if(key==='loc'||key==='extra'||key==='tokens'||key==='comments')continue;
    if(Array.isArray(value))for(const child of value)walk(child);else if(value&&typeof value==='object')walk(value);
   }
  }
  walk(tree);
  for(const edit of edits.sort((a,b)=>b.start-a.start))output=output.slice(0,edit.start)+edit.value+output.slice(edit.end);
 }else if(file.endsWith('.html')){
  output=output.replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi,block=>lower(block));
 }
 if(output!==original){fs.writeFileSync(file,output);files++;}
}
function walkDir(dir){for(const item of fs.readdirSync(dir,{withFileTypes:true})){const file=path.join(dir,item.name);if(item.isDirectory())walkDir(file);else if(/\.(?:js|html)$/.test(file))visitFile(file);}}
walkDir(root);
console.log(`Android color compatibility: ${literals} CSS string literals in ${files} generated files`);
