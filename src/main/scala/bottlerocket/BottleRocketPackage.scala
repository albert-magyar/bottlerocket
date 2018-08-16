// Copyright 2017 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Top-level pipeline for the BottleRocket core

package bottlerocket

import chisel3._
import chisel3.util.{RegEnable}
import chisel3.core.withClock
import freechips.rocketchip._
import rocket._
import jtag.JTAGIO
import devices.debug.{DMIIO, DebugModuleParams, DebugTransportModuleJTAG, JtagDTMKey}
import amba.axi4.{AXI4Bundle, AXI4Parameters}
import Params._

class BottleRocketPackage(options: BROptions)(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val iBus = AXI4LiteBundle(axiParams)
    val dBus = AXI4LiteBundle(axiParams)
    val nmi = Input(Bool())
    val eip = Input(Bool())
    val wfisleep = Output(Bool())
    val jtag = Flipped(new JTAGIO(hasTRSTn = false))
    val jtag_reset = Input(Bool())
  })

  val core = Module(new BottleRocketCore(options))
  val jtagDTM = Module(new DebugTransportModuleJTAG(p(DebugModuleParams).nDMIAddrSize, p(JtagDTMKey)))

  core.io.constclk := io.clk
  io.iBus <> core.io.iBus
  io.dBus <> core.io.dBus
  core.io.nmi := io.nmi
  core.io.eip := io.eip
  core.io.dmi <> jtagDTM.io.dmi
  io.wfisleep := core.io.wfisleep

  jtagDTM.io.jtag <> io.jtag
  jtagDTM.clock := io.jtag.TCK
  jtagDTM.reset := jtagDTM.io.fsmReset
  jtagDTM.io.jtag_reset  := io.jtag_reset
  jtagDTM.io.jtag_mfr_id := 42.U // Set MFG ID here
}
