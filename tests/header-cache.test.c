#include <assert.h>
#include <stdio.h>
#include "../tools/ijk/ntv_header_cache.h"
#ifdef NDEBUG
#error Header cache regression requires enabled assertions
#endif
static unsigned calls;
static int parse(const uint8_t *data, unsigned size, NtvHevcColor *out)
{
    ++calls;
    if (!data || !size || !data[0]) return 0;
    out->depth = data[0]; out->transfer = data[size-1]; return 1;
}
int main(void)
{
    NtvHeaderCache cache = {0}; NtvHevcColor out = {0}; uint8_t data[1025] = {8,1,16};
    for (unsigned n=0; n<10000; ++n)
        assert(ntv_cached_header(&cache,1,data,3,parse,&out) && out.depth==8 && out.transfer==16);
    assert(calls==1);
    data[2]=18;
    assert(ntv_cached_header(&cache,1,data,3,parse,&out) && out.transfer==18 && calls==2);
    data[0]=10;
    assert(ntv_cached_header(&cache,1,data,3,parse,&out) && out.depth==10 && calls==3);
    assert(ntv_cached_header(&cache,2,data,3,parse,&out) && calls==4);
    assert(ntv_cached_header(&cache,2,data,2,parse,&out) && calls==5);
    data[0]=0;
    assert(!ntv_cached_header(&cache,2,data,3,parse,&out) && calls==6);
    assert(!ntv_cached_header(&cache,2,data,3,parse,&out) && calls==6);
    data[0]=8;
    assert(ntv_cached_header(&cache,2,data,3,parse,&out) && calls==7);
    assert(ntv_cached_header(&cache,2,data,sizeof(data),parse,&out) && calls==8);
    data[1024]=18;
    assert(ntv_cached_header(&cache,2,data,sizeof(data),parse,&out) && out.transfer==18 && calls==9);
    assert(ntv_cached_header(NULL,2,data,3,parse,&out) && calls==10);
    assert(!ntv_cached_header(&cache,2,NULL,3,parse,&out) && calls==11);
    puts("PASS unchanged headers parsed once; in-place HDR/depth, codec, size, invalid/repaired, oversized headers and uncached calls remain correct");
    return 0;
}
