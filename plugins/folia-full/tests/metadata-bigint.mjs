import assert from 'node:assert/strict';
import {lowerMetadataBigInt} from '../compat-bigint-transform.mjs';
const original = globalThis.BigInt;
try {
    globalThis.BigInt = undefined;
    const math = await import('../compat-bigint.js?legacy-test');
    const max = math.from('18446744073709551615');
    assert.equal(math.binary('+', max, math.from(1)).toString(), '18446744073709551616');
    assert.equal(math.binary('===', math.from(7), math.from(7)), true);
    assert.equal(math.binary('===', math.from(7), 7), false);
    assert.equal(math.binary('>', max, Number.MAX_SAFE_INTEGER), true);
    assert.equal(math.binary('+', 2, 3), 5);
    const view = new DataView(new ArrayBuffer(8));
    for (const little of [true, false]) {
        math.write64(view, 0, max, little, false);
        assert.equal(math.read64(view, 0, little, false).toString(), max.toString());
        assert.equal(math.read64(view, 0, little, true).toString(), '-1');
        assert.equal(view.getUint8(0), 255);
    }
    const transformed = lowerMetadataBigInt('export function offset(a){let remaining=0xffffffffffffffffn;remaining-=BigInt(a);return Number(remaining)}','/node_modules/music-metadata/lib/mp4/test.js');
    assert.ok(!transformed.includes('0xffffffffffffffffn'));
    const {offset} = await import('data:text/javascript,'+encodeURIComponent(transformed.replace(/from "[^"]*compat-bigint.js"/, 'from '+JSON.stringify(new URL('../compat-bigint.js?legacy-test',import.meta.url).href))));
    assert.equal(offset(1), Number(original('18446744073709551614')));
} finally {globalThis.BigInt = original;}
console.log('PASS: exact unsigned/signed 64-bit metadata, both endian orders, source arithmetic transform');
