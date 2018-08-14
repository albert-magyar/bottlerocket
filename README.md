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
