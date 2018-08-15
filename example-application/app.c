int main(int argc, char **argv) {
  volatile int *special_reg = (int *) 0x4444;
  *special_reg = 0xdeadbeef;
  return 0;
}
