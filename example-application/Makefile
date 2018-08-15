RISCV_LD = riscv32-unknown-elf-ld
RISCV_LD_OPTS = -static -nostdlib -nostartfiles
RISCV_GCC = riscv32-unknown-elf-gcc
RISCV_GCC_OPTS = -mcmodel=medany -fvisibility=hidden
RISCV_OBJDUMP = riscv32-unknown-elf-objdump

default: app.hex

app.elf: app.ld app.o init.o
	$(RISCV_LD) -o $@ -Tapp.ld init.o app.o

%.hex: %.elf
	elf2hex 4 32768 $< > $@

%.o: %.S
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -c -o $@ $<

%.o: %.c
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -c -o $@ $<

.SECONDARY: $(addsuffix .elf, $(TESTS)) $(addsuffix .hex, $(TESTS))

.PHONY: clean

clean:
	rm -rf *.hex *.elf *.log ncverilog* INCA_libs data *.log *.dsn *.trn *.dump.d
