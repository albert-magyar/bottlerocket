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
import freechips.rocketchip.util.AsyncDecoupledCrossing
import jtag.JTAGIO
import devices.debug.{DMIIO, DebugModuleParams, DebugTransportModuleJTAG, JtagDTMKey}
import amba.axi4.{AXI4Bundle, AXI4Parameters}
import Params._

class BottleRocketPackage(options: BROptions)(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val clk = Input(Clock())
    val bus = AXI4LiteBundle(axiParams)
    val nmi = Input(Bool())
    val eip = Input(Bool())
    val wfisleep = Output(Bool())
    val jtag = Flipped(new JTAGIO(hasTRSTn = false))
    val jtag_reset = Input(Bool())
  })

  val core = Module(new BottleRocketCore(options))
  val arbiter = Module(new LockingAXI4LiteArbiter(3))
  val jtagDTM = Module(new DebugTransportModuleJTAG(p(DebugModuleParams).nDMIAddrSize, p(JtagDTMKey)))

  io.bus <> arbiter.io.slave
  arbiter.io.masters(0) <> core.io.sbaBus
  arbiter.io.masters(1) <> core.io.dBus
  arbiter.io.masters(2) <> core.io.iBus

  core.io.constclk := io.clk
  core.io.nmi := io.nmi
  core.io.eip := io.eip
  io.wfisleep := core.io.wfisleep

  val dmiReqSync = AsyncDecoupledCrossing(io.jtag.TCK, jtagDTM.io.fsmReset, jtagDTM.io.dmi.req, core.io.constclk, core.reset, 1)
  val dmiRespSync = AsyncDecoupledCrossing(core.io.constclk, core.reset, core.io.dmi.resp, io.jtag.TCK, jtagDTM.io.fsmReset, 1)
  core.io.dmi.req <> dmiReqSync
  jtagDTM.io.dmi.resp <> dmiRespSync

  jtagDTM.io.jtag <> io.jtag
  jtagDTM.clock := io.jtag.TCK
  jtagDTM.reset := jtagDTM.io.fsmReset
  jtagDTM.io.jtag_reset  := io.jtag_reset
  jtagDTM.io.jtag_mfr_id := 42.U // Set MFG ID here
}
