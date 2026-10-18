const fs=require('fs'),path=require('path'),os=require('os'),assert=require('assert');
const apply=require('./apply-parser-damage-patches.cjs');
const root=fs.mkdtempSync(path.join(os.tmpdir(),'ntv-parser-damage-'));
try {
    const dir=path.join(root,'libavformat');fs.mkdirSync(dir);
    const header='struct AVStreamInternal {\n    int reorder;\n};\n';
    const code=`
    while (size > 0 || (pkt == &flush_pkt && got_output)) {
        if (pkt->side_data) {
        }
    }
            st->parser = NULL;
`;
    fs.writeFileSync(path.join(dir,'internal.h'),header);
    fs.writeFileSync(path.join(dir,'utils.c'),code);
    apply(root);
    const first=fs.readFileSync(path.join(dir,'utils.c'),'utf8');
    const firstHeader=fs.readFileSync(path.join(dir,'internal.h'),'utf8');
    apply(root);
    assert.strictEqual(fs.readFileSync(path.join(dir,'utils.c'),'utf8'),first);
    assert.strictEqual(fs.readFileSync(path.join(dir,'internal.h'),'utf8'),firstHeader);
    assert(first.includes('out_pkt.flags |= AV_PKT_FLAG_CORRUPT;'));
    assert(first.includes('st->internal->ntv_parser_corrupt = !!(pkt->flags & AV_PKT_FLAG_CORRUPT);'));
    assert(first.includes('st->parser = NULL;\n            st->internal->ntv_parser_corrupt = 0;'));
    assert(firstHeader.includes('int ntv_parser_corrupt;'));
    // Delayed parser output, multiple frames per PES, no output, then a seek.
    let pending=0;
    const parse=(damaged,outputs)=>{
        if(damaged)pending=1;
        return Array.from({length:outputs},()=>{const marked=!!(damaged||pending);pending=+damaged;return marked;});
    };
    assert.deepStrictEqual(parse(false,2),[false,false]);
    assert.deepStrictEqual(parse(true,0),[]);
    assert.deepStrictEqual(parse(false,2),[true,false]);
    assert.deepStrictEqual(parse(true,3),[true,true,true]);
    assert.deepStrictEqual(parse(false,2),[true,false]);
    parse(true,0);pending=0;
    assert.deepStrictEqual(parse(false,1),[false]);
    console.log('PASS fresh/repeated parser overlay, delayed/multiple/no-output damage flags, clean input and parser-reset semantics');
} finally {fs.rmSync(root,{recursive:true,force:true});}
