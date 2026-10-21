import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {execFileSync} from 'node:child_process';
import postcss from 'postcss';
import {lowerCss} from '../compat-css.mjs';

// Verify the two production failures: runtime shader CSS and cross-chunk layer priorities.
const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const fixture = path.join(root, '.codex-tmp/folia-full/css-regression');
fs.mkdirSync(fixture, {recursive: true});
fs.writeFileSync(path.join(fixture, 'reset.css'), '@layer base{*{padding:0}::before{content:normal}}');
fs.writeFileSync(path.join(fixture, 'component.css'), '.cover::before{padding-top:100%;content:""}');
execFileSync(process.execPath, [path.resolve(here, '../compat-css.mjs'), fixture]);
const reset = postcss.parse(fs.readFileSync(path.join(fixture, 'reset.css'), 'utf8'));
const component = postcss.parse(fs.readFileSync(path.join(fixture, 'component.css'), 'utf8'));
const weight = selector => (selector.match(/:not\(#\\#\)/g) || []).length;
let resetWeight = 0, componentWeight = 0;
reset.walkRules(rule => { resetWeight = Math.max(resetWeight, weight(rule.selector)); });
component.walkRules(rule => { componentWeight = Math.max(componentWeight, weight(rule.selector)); });
assert.ok(componentWeight > resetWeight, 'An unlayered component in another chunk must outrank the reset');
const runtime = await lowerCss('@layer paper-shaders{:where([data-paper-shader]){position:relative;& canvas{position:absolute;inset:0;width:100%;height:100%}}}', undefined, true);
assert.ok(!runtime.includes('@layer'), 'Runtime stylesheet retained an unsupported layer');
let hasCanvas = false;
postcss.parse(runtime).walkRules(rule => {
    if (!/:where\(|:is\(|&/.test(rule.selector) && rule.selector.includes('[data-paper-shader]') && rule.selector.includes('canvas')) {
        const declarations = Object.fromEntries(rule.nodes.filter(node => node.type === 'decl').map(node => [node.prop, node.value]));
        hasCanvas ||= declarations.width === '100%' && declarations.height === '100%' && declarations.top === '0';
    }
});
assert.ok(hasCanvas, 'Runtime canvas has no Chrome 68-compatible sizing rule');
console.log('PASS: runtime shader sizing and shared cross-chunk CSS cascade');
