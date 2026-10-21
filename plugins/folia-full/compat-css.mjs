import fs from 'node:fs';
import path from 'node:path';
import postcss from 'postcss';
import presetEnv from 'postcss-preset-env';
import {fileURLToPath} from 'node:url';

// Keep modern declarations, with static CSS fallbacks for the Android WebView 61 path.
const root = process.argv[2];
let count = 0;
function prepareCss(input, file, runtime = false) {
    const tree = postcss.parse(input, {from: file});
    if (runtime) tree.walkRules(rule => { rule.selector = rule.selector.replace(/:where\(/g, ':is('); });
    // Chromium 68 rejects a whole selector list when :host/::backdrop is used outside its context.
    // Retain a separate ordinary-document rule so Tailwind's root tokens and reset still apply.
    tree.walkRules(rule => {
        if (!rule.selectors.some(selector => /:host\b|::backdrop\b/.test(selector))) return;
        const selectors = rule.selectors.filter(selector => !/:host\b|::backdrop\b/.test(selector));
        if (selectors.length) rule.cloneBefore({selectors});
    });
    tree.walkDecls(decl => {
        // A gradient direction hidden inside a custom property cannot be lowered by Lightning CSS.
        if (decl.prop.startsWith('--tw-gradient-')) decl.value = decl.value.replace(/\s+in (?:oklab|oklch|srgb-linear|lab|lch)\b/g, '');
        if (/\d(?:dvh|dvw|svh|svw|lvh|lvw)\b/.test(decl.value)) {
            decl.cloneBefore({value: decl.value.replace(/(\d)(?:d|s|l)(v[hw])\b/g, '$1$2')});
        }
        // The common modal constraints min(720px,100%) need a usable viewport bound.
        if (/^(?:min|max)-(?:width|height)$/.test(decl.prop) && /^min\(/.test(decl.value)) {
            const args = postcss.list.comma(decl.value.slice(4, -1));
            const fallback = args.find(value => /%|v[hw]/.test(value)) || args[0];
            if (!/\b(?:min|max|clamp)\(/.test(fallback)) decl.cloneBefore({value: fallback});
        }
    });
    const initial = [];
    tree.walkAtRules('property', rule => {
        const value = rule.nodes.find(node => node.prop === 'initial-value');
        if (value) initial.push(`${rule.params}:${value.value}`);
    });
    // @property's non-inheriting defaults are essential to Tailwind transforms and shadows.
    if (initial.length) tree.prepend(postcss.parse(`@layer properties{*,::before,::after{${initial.join(';')}}}`, {from: file}));
    return tree;
}
function processor() {
    return postcss([presetEnv({
        browsers: ['Chrome 61'], stage: 2, preserve: true,
        features: {
            'cascade-layers': true,
            'is-pseudo-class': true,
            'custom-properties': false,
            'has-pseudo-class': false,
            'focus-visible-pseudo-class': true,
            'prefers-color-scheme-query': false,
        },
    })]);
}
export async function lowerCss(input, file, runtime = false) {
    const result = await processor().process(prepareCss(input, file, runtime), {from: file, to: file, map: false});
    return result.css;
}
async function lowerStylesheets(files) {
    const combined = postcss.root();
    const buckets = new Map(files.map(file => [path.resolve(file), postcss.root()]));
    for (const file of files) combined.append(prepareCss(fs.readFileSync(file, 'utf8'), file).nodes);
    // Layer specificity must be computed across ALL split chunks. Processing each file separately
    // makes Tailwind's reset outrank unrelated unlayered component CSS in another chunk.
    const result = await processor().process(combined, {from: undefined, map: false});
    for (const node of [...result.root.nodes]) {
        const source = node.source?.input?.file || node.nodes?.find(child => child.source?.input?.file)?.source.input.file;
        if (!source || !buckets.has(path.resolve(source))) throw new Error(`CSS node lost its source: ${node.toString().slice(0,120)}`);
        buckets.get(path.resolve(source)).append(node);
    }
    for (const file of files) fs.writeFileSync(file, buckets.get(path.resolve(file)).toString());
    count = files.length;
}
const stylesheets = [];
async function walk(dir) {
    for (const item of fs.readdirSync(dir, {withFileTypes: true})) {
        const file = path.join(dir, item.name);
        if (item.isDirectory()) await walk(file);
        else if (file.endsWith('.css')) stylesheets.push(file);
        else if (file.endsWith('.html')) {
            let html = fs.readFileSync(file, 'utf8');
            const blocks = [...html.matchAll(/<style\b[^>]*>([\s\S]*?)<\/style>/gi)];
            for (const block of blocks) {
                const result = await postcss([presetEnv({browsers: ['Chrome 61'], stage: 2})])
                    .process(block[1], {from: undefined, map: false});
                html = html.replace(block[0], block[0].replace(block[1], result.css));
            }
            fs.writeFileSync(file, html);
        }
    }
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    await walk(root);
    await lowerStylesheets(stylesheets);
    console.log(`Chrome 61 CSS compatibility: ${count} stylesheets`);
}
