import {color, serializeRGB} from '@csstools/css-color-parser';
import {parseComponentValue} from '@csstools/css-parser-algorithms';
import {tokenize} from '@csstools/css-tokenizer';

// Resolve inline theme colors only on engines without color-mix; animation frames stay untouched.
if (typeof document !== 'undefined' && !CSS.supports('color', 'color-mix(in srgb, red, blue)')) {
    const queue = new Set();
    const originals = new Map();
    const primaryColors = new WeakMap();
    let scheduled = false;
    function resolveVars(value, style) {
        for (let i = 0; i < 16 && value.includes('var('); i++) {
            const next = value.replace(/var\(\s*(--[\w-]+)\s*(?:,\s*([^()]*))?\)/g,
                (_, name, fallback) => style.getPropertyValue(name).trim() || fallback || '');
            if (next === value) break;
            value = next;
        }
        return value;
    }
    function flush() {
        scheduled = false;
        for (const [element] of originals) if (!element.isConnected) originals.delete(element);
        for (const element of queue) {
            if (!element.isConnected) continue;
            const values = originals.get(element) || new Map();
            for (const name of Array.from(element.style)) {
                const value = element.style.getPropertyValue(name);
                if (value.includes('color-mix(')) values.set(name, value);
            }
            if (!values.size) continue;
            originals.set(element, values);
            const computed = getComputedStyle(element);
            for (const [name, value] of values) {
                const resolved = resolveVars(value, computed);
                try {
                    const data = color(parseComponentValue(tokenize({css: resolved})));
                    if (!data) continue;
                    const rgb = serializeRGB(data).toString();
                    const legacy = rgb.replace(/^rgba?\((.*)\)$/, (_, channels) => {
                        const parts = channels.trim().split(/\s*\/\s*|\s+/);
                        return `${parts.length === 4 ? 'rgba' : 'rgb'}(${parts.join(',')})`;
                    });
                    if (element.style.getPropertyValue(name) !== legacy) element.style.setProperty(name, legacy);
                } catch (_) { /* Leave unsupported advanced expressions to the normal CSS fallback. */ }
            }
        }
        queue.clear();
    }
    function enqueue(element) {
        if (element.nodeType !== 1) return;
        if ((element.getAttribute('style') || '').includes('color-mix(')) queue.add(element);
        const primary = element.style.getPropertyValue('--text-primary');
        if (primary && primaryColors.get(element) !== primary) {
            primaryColors.set(element, primary);
            for (const [node] of originals) queue.add(node);
        }
    }
    const observer = new MutationObserver(records => {
        for (const record of records) {
            if (record.type === 'attributes') enqueue(record.target);
            else for (const node of record.addedNodes) if (node.nodeType === 1) {
                enqueue(node);
                node.querySelectorAll('[style*="color-mix("]').forEach(enqueue);
            }
        }
        if (queue.size && !scheduled) { scheduled = true; requestAnimationFrame(flush); }
    });
    observer.observe(document.documentElement, {attributes: true, attributeFilter: ['style'], childList: true, subtree: true});
}
