# BottleRocket RV32IMC Core

This is not an officially supported Google product.

## Overview

BottleRocket is a 32-bit, RISC-V microcontroller-class processor core that is
built as a customized microarchitecture from components of the Free Chips
Project Rocket core. It is implemented in the Chisel HDL, and it consists of a
basic, 3-stage pipeline with separate instruction and data ARM AMBA AXI4Lite
buses. It has an assortment of features that are designed to support typical use
as a control processor for memory-mapped devices.

## Features

* RV32IMC ISA, Privileged Architecture v1.10
   * 32-bit RISC-V base instruction set (‘RV32I’)
   * Hardware integer multiplier/divider (‘M’ standard extension)
   * 16-bit compressed instruction support (‘C’ standard extension)
* Machine (‘M’) and user (‘U’) privilege modes

## Design Rationale

The BottleRocket core is designed to be as simple as possible to allow for easy,
application-specific changes. It uses several key components from Rocket Chip,
an open-source RISC-V chip generator framework, including the instruction
decoder and control & status register (CSR) finite state machine. These two
components are responsible for implementing the majority of the nuanced features
of the user ISA and the privileged architecture, respectively. This approach has
several key advantages.

* Rocket Chip is the reference implementation of the RISC-V ISA. It is largely
  produced by the primary authors of the ISA specifications, and it is by far
  the most spec-compliant hardware implementation.

* However, Rocket Chip is quite complex. It has many performance-oriented
  microarchitectural features (integrated non-blocking data cache, branch
  prediction)

* Rocket Chip is a very heavily metaprogrammed framework for generating
  symmetric multiprocessor (SMP) systems with interconnect supporting multiple
  coherent caches. In order to use the core in a simpler context, creating a
  simpler top-level module would be desirable for readability purposes.

* The “Rocket” core that is used in Rocket Chip is largely composed of
  well-factored, reasonably large, and highly-reusable sub-blocks. These blocks
  have been used in multiple projects to create different core microarchitectures
  or pipelines with relatively low effort (BOOM, ZScale)

* The instruction decoder is implemented as a reusable combinational block that
  is essentially universally applicable to any RISC-V core where decode happens
  within a single stage. It is well-verified and supports all of the RISC-V
  standard extensions in their latest incarnations.

* The RISC-V compressed (RVC) expander is also implemented as a reusable
  combinational block.  Because every RVC instruction maps onto a normal 32-bit
  encoding, this universal expander handles all of the RVC extension, aside from
  the extra complication of designing fetch logic to handle 16-bit aligned
  program counters.

* The CSR file implements essentially all of the privileged architecture as a
  state machine.

* Building around Rocket components allows BottleRocket to be a fully
  spec-compliant RV32IMC core with machine and user modes while occupying a few
  hundred lines of total new Chisel code.

## Building and Running

The first step to using BottleRocket is making sure that the work environment is
ready to support RISC-V development. It is helpful to follow the convention that
the RISCV environment variable points to the RISC-V toolchain installation.

1. Add the following to your environment using configuration files and/or a
   script

   ```bash
   $ export RISCV=<desired path>
   $ export PATH=$PATH:$RISCV/bin
   ```

2. Clone and install the RV32IMC toolchain. Note, this requires changing the
   meta-build script that calls configure and make in each process, as shown
   with the sed invocation below.

   ```bash
   $ git clone https://github.com/riscv/riscv-tools
   $ cd riscv-tools
   $ sed 's/ima/imc/g' <build-rv32ima.sh >build-rv32imc.sh
   $ chmod +x build-rv32imc.sh
   $ ./build-rv32imc.sh
   ```

3. Enter the BottleRocket directory and run the standalone tests. NOTE: you may
   need to modify `test/Makefile` to target an appropriate Verilog simulator for
   your environment.

   ```bash
   $ cd <path to BottleRocket top dir>
   $ make test
   ```

4. The generated Verilog is in `generated-src/BottleRocketCore.v` -- this
   contains the top level BottleRocketCore module that can be instantiated in a
   Verilog design.


5. Try running sbt (“Simple Build Tool,” the most popular build tool for Scala
   projects) manually. This allows more options for building the core. The
   following command will print all available command-line options (number of
   interrupt lines, target directory for Verilog generation, etc.).

   ```bash
   $ sbt "runMain bottlerocket.BottleRocketGenerator --help"
   ```
## A Tour of the Source Code

There are 2 main categories of design units in BottleRocket: subcomponents that are borrowed from Rocket Chip and custom elements of the BottleRocket core.

### BottleRocket Components

Located in ./src/main/scala/bottlerocket/*

* __BottleRocketGenerator.scala__ The top-level generator consists of Chisel generation boilerplate and command line argument management.

* __BottleRocketCore.scala__ This is the implementation of the 3-stage core. Most control logic is factored into large modules, so the top-level module contains mostly pipeline registers, connections, and pieces of control logic that touch many signals in multiple stages, such as hazard detection.
  
* __FrontendBuffer.scala__ This module interfaces with the instruction bus and manages outstanding transactions. It is decoupled from the core, which makes it easier to manage events like misaligned 32-bit accesses (arising from RVC compatibility) and dropping outstanding requests that are rendered useless upon a branch or other redirect.

* __DebugModuleFSM.scala__ This module implements the Debug Module Interface (DMI) for the SiFive Debug Specification, v0.13

* __DebugStepper.scala__ This test module attaches to the DMI and repeatedly halts and single-steps the core, stress-testing debug actions.

* __Constants.scala__ This contains non-ISA-defined constants, such as microarchitectural control signal values.

* __ExceptionCause.scala__ This defines a format for encoding the set of exceptions caused by a particular instruction’s execution, so that they may be passed along with the instruction through the pipeline and recorded at commit time.

* __LoadExtender.scala__ The load extender turns bus replies into writeback values, based on the type of load and desired extension (signed or unsigned).

* __AXI4Lite.scala__ Contains a definition of the ARM AMBA AXI4Lite interface as a Chisel `Bundle`.

### Components Borrowed from Rocket Chip

Located in ./rocket-chip/src/main/scala/rocket/*

* __Instructions.scala__ This contains ISA-specified encoding information for instructions, interrupt causes, CSRs, and anything else from the base or privileged ISA.

* __CSR.scala__ This contains the implementation of the Control and Status Register (CSR) file. The CSR file is the most complicated part of implementing a RISC-V core, and manages essentially every aspect of the architecture related to the privileged architecture, including interrupt handling and wfi instructions.

* __PMP.scala__ This contains the checker logic that enforces Physical Memory Protect violations.

* __Multiplier.scala__ A multi-cycle hardware iterative multiplier/divider that is parametrizable to take a variable number of cycles. By default, it takes 8 cycles to perform a multiplication or division, and it takes constant time, with the no-early-out feature enabled.

* __Consts.scala__ This contains the encoding of microarchitectural control signals, such as memory operation identifiers and ALU operations. Unlike the values in "Instructions," these values are not specified by the ISA.

* __ALU.scala__ Rocket includes a simple ALU that is parameterizable for 32-bit or 64-bit width.

## Core Microarchitecture Description

The BottleRocket core uses a simple, 3-stage pipeline with a one-cycle branch penalty. It is designed to be used with an instruction cache or similar memory bus connection with a short delay and no wait states for cache hits.

### Simplified pipeline diagram (some signals omitted for clarity)

Yellow blocks are library components found in the Rocket Chip repository. Blue blocks and all pipeline registers are defined in the BottleRocket-specific source files.

*TODO: insert pipeline diagram*
![Pipeline diagram](https://raw.githubusercontent.com/albert-magyar/bottlerocket/master/pipeline.png)

## Physical Memory Protection

BottleRocket implements the Physical Memory Protection (PMP) scheme outlined in Section 3.6 of the RISC-V privileged architecture, version 1.10. It uses a configurable number of PMP regions, which is controlled via the `nPMPs` parameter in the __Params.scala__ file in the bottlerocket package. This parameter may be set to zero, which has the effect of disabling the PMP feature.

Regardless of the number of regions, the core employs two `PMPChecker` modules (borrowed from Rocket): one for checking fetch accesses, and one for checking load/store accesses. In addition to receiving metadata about memory transactions and producing potential exceptions, these checkers are connected to the PMP­related outputs from the CSR file, where the PMP configuration CSRs are housed. Since BottleRocket has a very shallow pipeline, enabling PMP will add some delay to some memory-related paths; if this poses a physical design issue, PMP may be disabled.

### Omitting Physical Memory Protection

Setting the number of PMP regions to zero eliminates all logic related to PMP registers in the CSR file. Furthermore, the Rocket PMP checker implementation is parameterized by the number of PMP regions, and adds no logic or delay to the pipeline when the number of PMP regions is set to zero. Therefore, setting `nPMPs` to zero is equivalent to omitting all PMP-related logic and implementation cost from the design.

## Interrupt Architecture

The BottleRocket core supports all of the features of 'M' mode and 'U' mode for the RISC-V privileged architecture. This core privileged specification contains only three interrupt types: a timer interrupt, a software interrupt (set by software to signal other harts), and a generic external interrupt. In order to support an arbitrary number of device interrupts, BottleRocket includes a programmable interrupt controller peripheral that is not specified by the RISC-V ISA.

### RISC-V Interrupt Model

The full description of interrupts in the base RISC-V ISA is found in the RISC-V privileged architecture specification. However, there are some key aspects to note for programmers accustomed to other architectures.

* There is a two-entry hardware stack of interrupt-enable and privilege mode bits in the `mstatus` register. The current context's entry is on top of the stack, while the previous context’s entry is at the bottom, accessible in the previous privilege and previous interrupt enable fields.

* When an interrupt is taken, an entry representing machine mode with interrupts disabled is pushed onto the stack, the address of the interrupted instruction is moved into `mepc`, and the machine jumps to the handler address stored in `mtvec`.

* No general-purpose registers are saved on interrupt entry or restored on interrupt return.

* Interrupt handlers are non-preemptible by default. If preemption is desired, software must manage the preservation of state so that interrupts may be re-enabled in the handler.

## External Debug

BottleRocket implements [version 0.13.7 of the SiFive proposed debug spec](https://www.sifive.com/documentation/risc-v/risc-v-external-debug-support/). It is extremely likely that the ratified debug specification will not differ in any meaningful way, since many elements of the debug spec are optional, and the required core that BottleRocket implements is stable across current revisions. Furthermore, much of the spec deals with the ability to debug multiple RISC-V hardware threads (harts); these details are not relevant for a system with one RISC-V core.

The full debug spec provides an excessively large amount of information for the required subset implemented in BottleRocket, so the following is a condensed description of the debug architecture.

### Debug Module Registers

#### `Abstract data 0 (0x04)`

*TODO: insert bitfield table*

The abstract data 0 register is used for two purposes: to hold argument data for the next abstract command, and to hold result data from the last abstract command. Typically, a command would begin by writing arguments to the data registers before writing to the abstract command / control / status registers to initiate the command. Although the RISC-V debug spec defines a larger number of optional abstract data registers, none of the commands supported by BottleRocket require more than 32 bits of argument or result data.

#### `Debug Module Control (0x10)`

*TODO: insert bitfield table*

The halt request bit will initiate a halt state in the RISC-V core. It make take a variable amount of time to enter the halt state, so it is necessary to check the `allhalted` bit in the `dmstatus` register to see whether the core is actually halted. Similarly, raising `resumereq` requires the programmer to check `resumeack` in `dmstatus` to see whether the core has actually resumed. Neither bit will clear itself, and having both request bits high will result in undefined behavior (per the RISC-V debug spec), so it is necessary to clear a halt request before resuming a halted hart and vice-versa.

#### `Debug Module Status (0x11)`

*TODO: insert bitfield table*

#### `Abstract Control and Status (0x16)`

*TODO: insert bitfield table*

#### `Abstract Command (0x17)`

*TODO: insert bitfield table*
