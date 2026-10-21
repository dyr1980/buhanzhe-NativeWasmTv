// Small DOM API gaps outside core-js, shared by the page and classic workers.
if (typeof Blob !== 'undefined') {
    for (const [method, readerMethod] of [['text', 'readAsText'], ['arrayBuffer', 'readAsArrayBuffer']]) {
        if (!Blob.prototype[method]) Object.defineProperty(Blob.prototype, method, {
            configurable: true, writable: true,
            value() {
                return new Promise((resolve, reject) => {
                    const reader = new FileReader();
                    reader.onload = () => resolve(reader.result);
                    reader.onerror = () => reject(reader.error);
                    reader[readerMethod](this);
                });
            },
        });
    }
}
if (typeof crypto !== 'undefined' && !crypto.randomUUID) {
    crypto.randomUUID = function () {
        const bytes = crypto.getRandomValues(new Uint8Array(16));
        bytes[6] = (bytes[6] & 15) | 64;
        bytes[8] = (bytes[8] & 63) | 128;
        const hex = Array.from(bytes, value => value.toString(16).padStart(2, '0')).join('');
        return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
    };
}
if (typeof ResizeObserverEntry !== 'undefined' && !('borderBoxSize' in ResizeObserverEntry.prototype)) {
    for (const [name, border] of [['borderBoxSize', true], ['contentBoxSize', false]]) {
        Object.defineProperty(ResizeObserverEntry.prototype, name, {
            configurable: true,
            get() {
                const style = getComputedStyle(this.target);
                const vertical = /^(?:vertical|sideways)/.test(style.writingMode);
                const width = border ? this.target.offsetWidth : this.contentRect.width;
                const height = border ? this.target.offsetHeight : this.contentRect.height;
                return [{inlineSize: vertical ? height : width, blockSize: vertical ? width : height}];
            },
        });
    }
}
if (typeof Element !== 'undefined') {
    if (!Element.prototype.toggleAttribute) Element.prototype.toggleAttribute = function (name, force) {
        name = String(name);
        const present = this.hasAttribute(name);
        const enabled = arguments.length > 1 ? Boolean(force) : !present;
        if (enabled && !present) this.setAttribute(name, ''); else if (!enabled && present) this.removeAttribute(name);
        return enabled;
    };
    try { document.documentElement.matches(':focus-visible'); }
    catch (_) {
        const matches = Element.prototype.matches;
        Element.prototype.matches = function (selector) {
            return matches.call(this, String(selector).replace(/:focus-visible\b/g, '.focus-visible'));
        };
    }
}
