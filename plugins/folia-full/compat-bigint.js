// Exact 64-bit metadata arithmetic on old engines; native BigInt stays in use where available.
import JSBI from 'jsbi';
const native = typeof BigInt === 'function' ? BigInt : null;
const isFallback = value => value instanceof JSBI;
export function from(value) { return native ? native(value) : JSBI.BigInt(value); }
export function toNumber(value) { return isFallback(value) ? JSBI.toNumber(value) : Number(value); }
export function binary(op, a, b) {
    const left = isFallback(a), right = isFallback(b);
    if (left || right) {
        if (op === '===' || op === '!==') {
            const same = left && right && JSBI.equal(a, b);
            return op === '===' ? same : !same;
        }
        if (op === '<') return JSBI.LT(a, b);
        if (op === '<=') return JSBI.LE(a, b);
        if (op === '>') return JSBI.GT(a, b);
        if (op === '>=') return JSBI.GE(a, b);
        if (op === '==') return JSBI.EQ(a, b);
        if (op === '!=') return !JSBI.EQ(a, b);
        if (op === '+' && (typeof a === 'string' || typeof b === 'string')) return String(a) + String(b);
        if (!left || !right) throw new TypeError('Cannot mix BigInt and other numeric types');
        const methods = {'+':'add','-':'subtract','*':'multiply','/':'divide','%':'remainder','**':'exponentiate','&':'bitwiseAnd','|':'bitwiseOr','^':'bitwiseXor','<<':'leftShift','>>':'signedRightShift'};
        if (methods[op]) return JSBI[methods[op]](a, b);
    }
    switch (op) {
        case '+': return a+b; case '-': return a-b; case '*': return a*b;
        case '/': return a/b; case '%': return a%b; case '**': return a**b;
        case '&': return a&b; case '|': return a|b; case '^': return a^b;
        case '<<': return a<<b; case '>>': return a>>b; case '>>>': return a>>>b;
        case '===': return a===b; case '!==': return a!==b;
        case '==': return a==b; case '!=': return a!=b;
        case '<': return a<b; case '<=': return a<=b; case '>': return a>b; case '>=': return a>=b;
        default: throw new TypeError('Unsupported metadata operator '+op);
    }
}
export function unary(op, value) {
    if (isFallback(value)) return op === '-' ? JSBI.unaryMinus(value) : JSBI.bitwiseNot(value);
    return op === '-' ? -value : ~value;
}
export function read64(view, offset, little, signed) {
    const method = signed ? 'getBigInt64' : 'getBigUint64';
    if (native && view[method]) return view[method](offset, little);
    const low = view.getUint32(offset + (little ? 0 : 4), little);
    const high = view.getUint32(offset + (little ? 4 : 0), little);
    let value = JSBI.add(JSBI.leftShift(JSBI.BigInt(high), JSBI.BigInt(32)), JSBI.BigInt(low));
    if (signed && high >= 0x80000000) value = JSBI.subtract(value, JSBI.exponentiate(JSBI.BigInt(2), JSBI.BigInt(64)));
    return native ? native(value.toString()) : value;
}
export function write64(view, offset, value, little, signed) {
    const method = signed ? 'setBigInt64' : 'setBigUint64';
    if (native && view[method]) return view[method](offset, value, little);
    const bits = JSBI.asUintN(64, isFallback(value) ? value : JSBI.BigInt(value.toString()));
    const low = JSBI.toNumber(JSBI.bitwiseAnd(bits, JSBI.BigInt('4294967295')));
    const high = JSBI.toNumber(JSBI.signedRightShift(bits, JSBI.BigInt(32)));
    view.setUint32(offset + (little ? 0 : 4), low, little);
    view.setUint32(offset + (little ? 4 : 0), high, little);
}
