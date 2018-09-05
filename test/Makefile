# Copyright 2017 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

RISCV_TEST_BASE = ../third_party/rocket-chip/riscv-tools/riscv-tests
GEN_SRC_DIR = ../generated-src

RISCV_GCC = riscv32-unknown-elf-gcc
RISCV_GCC_OPTS = -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles
RISCV_OBJDUMP = riscv32-unknown-elf-objdump

# Options: ncverilog, vcs
SIM = vcs

NCVERILOG_OPTS = +incdir+$(GEN_SRC_DIR) +sv +nctimescale+1ns/1ns +access+r +nclicq
VCS_OPTS = -full64 -debug_pp -sverilog +incdir+$(GEN_SRC_DIR)

ifeq ($(SIM),vcs)
SIM_COMMAND = ./simv
SIM_PREREQS = simv
else
SIM_COMMAND = ncverilog $(NCVERILOG_OPTS)
endif

# fence_i not supported for Harvard architecture
RV32UI_TESTS = addi \
               add \
               andi \
               and \
               auipc \
               beq \
               bge \
               bgeu \
               blt \
               bltu \
               bne \
               jalr \
               jal \
               lb \
               lbu \
               lh \
               lhu \
               lui \
               lw \
               ori \
               or \
               sb \
               sh \
               simple \
               slli \
               sll \
               slti \
               sltiu \
               slt \
               sltu \
               srai \
               sra \
               srli \
               srl \
               sub \
               sw \
               xori \
               xor

RV32MI_TESTS = breakpoint \
               csr \
               illegal \
               ma_addr \
               ma_fetch \
               mcsr \
               sbreak \
               scall \
               shamt

TESTS = $(RV32UI_TESTS) # $(RV32MI_TESTS)

TEST_LOGS = $(addprefix test-outputs/, $(addsuffix .log, $(TESTS)))

JTAG_TEST_LOGS = $(addprefix test-outputs/, $(addsuffix _jtag_test.log, $(TESTS)))

default: $(TEST_LOGS)

jtag-test: $(JTAG_TEST_LOGS)

simv: BottleRocketCoreTB.v MockAXI4LiteSRAM.v $(GEN_SRC_DIR)/*.v
	vcs $(VCS_OPTS) $<

test-outputs:
	mkdir -p test-outputs

test-outputs/%.log: test-outputs/%.hex test-outputs/%.elf $(GEN_SRC_DIR)/*.v *.v $(SIM_PREREQS) | test-outputs
	$(RISCV_OBJDUMP) -D test-outputs/$*.elf > test-outputs/$*.objdump
	$(SIM_COMMAND) +image=$* BottleRocketCoreTB.v &> test-outputs/$*.log

test-outputs/%.hex: test-outputs/%.elf | test-outputs
	elf2hex 4 32768 $< > $@

test-outputs/%.elf: $(RISCV_TEST_BASE)/isa/rv32ui/%.S | test-outputs
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(RISCV_TEST_BASE)/env/p -I$(RISCV_TEST_BASE)/isa/macros/scalar -Ttest.ld -o $@ $<

test-outputs/%.elf: $(RISCV_TEST_BASE)/isa/rv32mi/%.S | test-outputs
	$(RISCV_GCC) $(RISCV_GCC_OPTS) -I$(RISCV_TEST_BASE)/env/p -I$(RISCV_TEST_BASE)/isa/macros/scalar -Ttest.ld -o $@ $<

test-outputs/%_jtag_test.log: test-outputs/%.hex $(GEN_SRC_DIR)/*.v *.v package-sim | test-outputs
	./package-sim +image=$* &> $@

package-sim: BottleRocketPackageTB.v JTAGDTM.v MockAsyncResetReg.v MockAXI4LiteSRAM.v $(GEN_SRC_DIR)/*.v
	vcs -full64 +incdir+../generated-src -sverilog -debug_pp MockAsyncResetReg.v BottleRocketPackageTB.v -o $@

.SECONDARY:

.PHONY: clean

clean:
	rm -rf *.hex *.elf *.log ncverilog* INCA_libs data *.log *.dsn *.trn *.dump.d *.objdump *.vpd *.daidir simv test-outputs
