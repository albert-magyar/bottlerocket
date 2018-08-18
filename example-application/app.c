//#include "address_map.h"
#define SPI_REG_SPICMD      (*((volatile int *)0x00002008))
#define SPI_REG_SPIADR      (*((volatile int *)0x0000200C))

int SPI_WRITE(int command);
int plus1(int num);

int main(int argc, char **argv) {

    //This works
    SPI_REG_SPICMD = 0xbabecafe;

    //This writes 2 instead of 9
    SPI_REG_SPICMD = plus1(8);

    //This writes 0 instead of 5 
    SPI_WRITE(5);
    
    return 0;
}

int plus1(int num){
    int result = num + 1;
    return result;
}

int SPI_WRITE(int command){
    SPI_REG_SPIADR = command;
    return 1;
}

