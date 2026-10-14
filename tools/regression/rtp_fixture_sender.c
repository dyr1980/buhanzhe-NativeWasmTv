/* Replay actual FFmpeg RTP datagrams with their captured arrival cadence. */
#include <arpa/inet.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

static uint64_t now_us(void) {
    struct timespec value;
    clock_gettime(CLOCK_MONOTONIC,&value);
    return (uint64_t)value.tv_sec*1000000+value.tv_nsec/1000;
}
int main(int argc,char **argv) {
    if(argc!=3)return 2;
    char *end;long port=strtol(argv[2],&end,10);
    if(*end || port<1024 || port>65535)return 2;
    FILE *file=fopen(argv[1],"rb");if(!file)return 3;
    int fd=socket(AF_INET,SOCK_DGRAM,0);if(fd<0)return 4;
    struct sockaddr_in address={0};address.sin_family=AF_INET;
    address.sin_port=htons((uint16_t)port);address.sin_addr.s_addr=htonl(INADDR_LOOPBACK);
    uint64_t start=now_us(),at;uint32_t length;unsigned char data[65536];unsigned count=0;
    while(fread(&at,sizeof(at),1,file)==1) {
        if(fread(&length,sizeof(length),1,file)!=1 || length<12 || length>sizeof(data)
                || fread(data,1,length,file)!=length)return 5;
        uint64_t target=start+at;
        for(;;) {
            uint64_t current=now_us();
            if(current>=target)break;
            uint64_t remaining=target-current;
            struct timespec delay={remaining/1000000,(remaining%1000000)*1000};
            nanosleep(&delay,0);
        }
        if(sendto(fd,data,length,0,(struct sockaddr *)&address,sizeof(address))!=(int)length)return 6;
        ++count;
    }
    printf("sent=%u elapsed_us=%llu\n",count,(unsigned long long)(now_us()-start));
    fclose(file);close(fd);return count?0:7;
}
