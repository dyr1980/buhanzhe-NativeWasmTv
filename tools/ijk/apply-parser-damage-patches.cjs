// LGPL-2.1-or-later. FFmpeg 3.4 drops demuxer corruption flags when parsing.
const fs=require('fs'),path=require('path');
function apply(root) {
    const header=path.join(root,'libavformat/internal.h');
    let h=fs.readFileSync(header,'utf8').replace(/\r\n/g,'\n');
    if(!h.includes('int ntv_parser_corrupt;')) {
        const start=h.indexOf('struct AVStreamInternal {'),end=h.indexOf('\n};',start);
        if(start<0||end<0)throw new Error('Missing private parser state');
        h=h.slice(0,end)+'\n    /* Damage may still be buffered inside the picture parser. */\n    int ntv_parser_corrupt;\n'+h.slice(end);
        fs.writeFileSync(header,h);
    }
    const file=path.join(root,'libavformat/utils.c');
    let s=fs.readFileSync(file,'utf8').replace(/\r\n/g,'\n');
    function edit(before,after) {
        if(s.includes(after))return;
        if(s.split(before).length!==2)throw new Error('Unexpected parser damage hook');
        s=s.replace(before,after);
    }
    edit('    while (size > 0 || (pkt == &flush_pkt && got_output)) {',`
    if (pkt->flags & AV_PKT_FLAG_CORRUPT)
        st->internal->ntv_parser_corrupt = 1;
    while (size > 0 || (pkt == &flush_pkt && got_output)) {`);
    edit('        if (pkt->side_data) {',`
        /* parse2 can emit the preceding picture and retain the damaged one.
         * Preserve the flag for every output from this packet plus the next
         * buffered picture. Do not turn damaged input into an apparently clean IDR. */
        if ((pkt->flags & AV_PKT_FLAG_CORRUPT) || st->internal->ntv_parser_corrupt)
            out_pkt.flags |= AV_PKT_FLAG_CORRUPT;
        st->internal->ntv_parser_corrupt = !!(pkt->flags & AV_PKT_FLAG_CORRUPT);
        if (pkt->side_data) {`);
    if(!s.includes('st->parser = NULL;\n            st->internal->ntv_parser_corrupt = 0;'))
        s=s.replaceAll('st->parser = NULL;','st->parser = NULL;\n            st->internal->ntv_parser_corrupt = 0;');
    fs.writeFileSync(file,s);
}
module.exports=apply;
if(require.main===module)apply(process.argv[2]);
