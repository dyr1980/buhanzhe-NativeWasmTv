import {lowerMetadataBigInt} from './compat-bigint-transform.mjs';
// Reproducible, checked adapter transforms; the pinned upstream files stay unmodified.
export function ntvCompat(compatEntry) {
    return {
        name: 'ntv-runtime-compat', enforce: 'pre',
        transform(code, id) {
            const file = id.split('?')[0];
            const metadata = lowerMetadataBigInt(code, file);
            if (metadata !== null) return {code:metadata,map:null};
            if (file.endsWith('/services/localLibraryAvailability.ts')) {
                const token = 'if (window.electron) return { supported: true, reason: null };';
                if (!code.includes(token)) throw new Error('Local library availability contract changed');
                return {code: code.replace(token, token + ' if ((window as any).__NTV_FOLIA_FILES__?.supported) return { supported: true, reason: null };'), map: null};
            }
            if (file.endsWith('/src/library/registry.ts')) {
                const token = 'Object.entries(suiteEntryModules).map(';
                if (!code.includes(token)) throw new Error('Library registry contract changed');
                // Use the upstream registry's own Grid fallback, without changing saved preferences.
                // Bravais requires container units and modern colors throughout its wall/seam UI.
                const filtered = "Object.entries(suiteEntryModules).filter(([path]) => !path.includes('/bravais/') || (typeof CSS !== 'undefined' && CSS.supports('container-type', 'inline-size') && CSS.supports('color', 'color-mix(in srgb, red, blue)'))).map(";
                return {code: code.replace(token, filtered), map: null};
            }
            if (file.endsWith('/library/suites/grid/home/Grid3DSlider.tsx')) {
                const token = 'style={{ paddingInline: edgePadding }}';
                if (!code.includes(token)) throw new Error('Home carousel padding contract changed');
                return {code: code.replace(token, 'style={{ paddingLeft: edgePadding, paddingRight: edgePadding }}'), map: null};
            }
            if (file.endsWith('/src/index.tsx') || file.endsWith('.worker.ts')) {
                // A classic worker must never receive Vite's document.createElement style injector.
                const layout = file.endsWith('/src/index.tsx')
                    ? 'import ' + JSON.stringify(compatEntry.replace(/compat-entry\.js$/, 'compat-layout.css')) + ';\n' : '';
                return {code: 'import ' + JSON.stringify(compatEntry) + ';\n' + layout + code, map: null};
            }
        },
    };
}
