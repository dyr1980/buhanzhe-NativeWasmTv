import {transformSync} from '@babel/core';
import {fileURLToPath} from 'node:url';
const helper = fileURLToPath(new URL('./compat-bigint.js', import.meta.url)).replaceAll('\\', '/');
const operators = new Set(['+','-','*','/','%','**','&','|','^','<<','>>','>>>','===','!==','==','!=','<','<=','>','>=']);
export function lowerMetadataBigInt(code, file) {
    const arithmetic = file.includes('/node_modules/music-metadata/lib/') && /\bBigInt\b|\b\d+n\b/.test(code);
    const views = file.includes('/node_modules/token-types/') && /(?:get|set)Big(?:Uint|Int)64/.test(code);
    if (!arithmetic && !views) return null;
    return transformSync(code, {filename:file,configFile:false,babelrc:false,plugins:[({types:t})=>({
        visitor:{
            Program:{exit(p){p.unshiftContainer('body',t.importDeclaration([t.importNamespaceSpecifier(t.identifier('__ntvBigInt'))],t.stringLiteral(helper)));}},
            BigIntLiteral(p){const raw=p.node.extra?.raw||code.slice(p.node.start,p.node.end);p.replaceWith(t.callExpression(t.memberExpression(t.identifier('__ntvBigInt'),t.identifier('from')),[t.stringLiteral(raw.replace(/n$/,'').replaceAll('_',''))]));},
            BinaryExpression:{exit(p){if(arithmetic&&operators.has(p.node.operator))p.replaceWith(t.callExpression(t.memberExpression(t.identifier('__ntvBigInt'),t.identifier('binary')),[t.stringLiteral(p.node.operator),p.node.left,p.node.right]));}},
            UnaryExpression:{exit(p){if(arithmetic&&['-','~'].includes(p.node.operator))p.replaceWith(t.callExpression(t.memberExpression(t.identifier('__ntvBigInt'),t.identifier('unary')),[t.stringLiteral(p.node.operator),p.node.argument]));}},
            AssignmentExpression:{exit(p){const op=p.node.operator.slice(0,-1);if(!arithmetic||p.node.operator==='='||!operators.has(op))return;
                const left=p.node.left;
                const stableMember=t.isMemberExpression(left)&&(t.isIdentifier(left.object)||t.isThisExpression(left.object))&&(!left.computed||t.isNumericLiteral(left.property)||t.isStringLiteral(left.property)||t.isIdentifier(left.property));
                if(!t.isIdentifier(left)&&!stableMember)throw p.buildCodeFrameError('Review new compound metadata assignment');
                p.replaceWith(t.assignmentExpression('=',p.node.left,t.callExpression(t.memberExpression(t.identifier('__ntvBigInt'),t.identifier('binary')),[t.stringLiteral(op),t.cloneNode(p.node.left),p.node.right])));
            }},
            CallExpression:{exit(p){const n=p.node;
                if(arithmetic&&t.isIdentifier(n.callee)&&['BigInt','Number'].includes(n.callee.name)&&n.arguments.length===1){p.replaceWith(t.callExpression(t.memberExpression(t.identifier('__ntvBigInt'),t.identifier(n.callee.name==='BigInt'?'from':'toNumber')),n.arguments));return;}
                if(views&&t.isMemberExpression(n.callee)&&!n.callee.computed&&/^(get|set)Big(Uint|Int)64$/.test(n.callee.property.name)){
                    const name=n.callee.property.name,write=name.startsWith('set'),args=[n.callee.object,...n.arguments];
                    if(args.length<(write?4:3))args.push(t.booleanLiteral(false));args.push(t.booleanLiteral(name.includes('BigInt')));
                    p.replaceWith(t.callExpression(t.memberExpression(t.identifier('__ntvBigInt'),t.identifier(write?'write64':'read64')),args));
                }
            }},
        },
    })]}).code;
}
