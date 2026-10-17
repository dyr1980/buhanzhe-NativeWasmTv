#include <assert.h>
#include <math.h>
#include <stdio.h>
#include "ntv_adaptive_probe.h"
int main(void) {
 double rates[]={24000./1001,25,30000./1001,50,60000./1001};
 for(unsigned i=0;i<sizeof(rates)/sizeof(*rates);i++) {
  int64_t ticks=(int64_t)(90000.*4/rates[i]+.5);
  assert(ntv_probe_timing_ready(4,0,ticks,4,1./90000,rates[i]));
  assert(ntv_probe_timing_ready(4,-ticks,0,4,1./90000,rates[i]));
  assert(!ntv_probe_timing_ready(3,0,ticks,4,1./90000,rates[i]));
  assert(!ntv_probe_timing_ready(4,0,ticks*2,4,1./90000,rates[i]));
 }
 assert(!ntv_probe_timing_ready(4,INT64_MIN,14400,4,1./90000,25));
 assert(!ntv_probe_timing_ready(4,1,1,4,1./90000,25));
 assert(!ntv_probe_timing_ready(4,100,0,4,1./90000,25));
 assert(!ntv_probe_timing_ready(4,0,14400,0,1./90000,25));
 assert(!ntv_probe_timing_ready(4,0,14400,4,0,25));
 assert(!ntv_probe_timing_ready(4,0,14400,4,NAN,25));
 assert(!ntv_probe_timing_ready(4,0,14400,4,1./90000,NAN));
 assert(!ntv_probe_timing_ready(4,0,14400,4,1./90000,0));
 puts("PASS verified integer/fractional clocks, negative DTS; short/unknown/discontinuous/inconsistent clocks retain normal probe");
 return 0;
}
