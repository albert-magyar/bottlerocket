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

// Module to allow debug system bus access

package bottlerocket

import chisel3._
import chisel3.util.{Enum, Cat, Queue, MuxCase, RegEnable}
import freechips.rocketchip._
import devices.debug.{DMIReq, DMIResp, DMIIO, DMIConsts, DebugModuleParams}
import amba.axi4.{AXI4Bundle, AXI4Parameters}
import Params._

class DebugBusAccessFSM()(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val dmiReadActive = Input(Bool())
    val dmiWriteActive = Input(Bool())
    val dmiReqAddr = Input(UInt(width = p(DebugModuleParams).nDMIAddrSize.W))
    val dmiReqData = Input(UInt(width = xBitwidth))
    val dmiRespValid = Output(Bool())
    val dmiRespData = Output(UInt(width = xBitwidth))
    val bus = AXI4LiteBundle(axiParams)
  })

  val s_idle :: s_reqWait :: s_wWait :: s_respWait :: Nil = chisel3.util.Enum(UInt(), 4)
  val state = Reg(init = s_idle)
  val nextState = Wire(UInt())
  val errorState = Reg(init = UInt(0, width = 3.W))

  val stale = Reg(init = Bool(true))
  val d0_reg = Reg(UInt(width = xBitwidth))
  val a0_reg = Reg(UInt(width = xBitwidth))

  val isCSAddr = io.dmiReqAddr === "h38".U
  val isA0Addr = io.dmiReqAddr === "h39".U
  val isD0Addr = io.dmiReqAddr === "h3c".U

  // Control/Status register management
  val accessSize = Reg(init = UInt(2, width = 3.W))
  val addrIncr = UInt(1) << accessSize
  val autoIncrement = Reg(init = Bool(false))
  val autoRead = Reg(init = Bool(false))
  val busSize = UInt(32, width = 7.W)
  val sizeMask = UInt(7, width = 5.W)
  val csReadVal = Cat(
    UInt(0, width = 12.W),
    accessSize,
    autoIncrement,
    autoRead,
    errorState,
    busSize,
    sizeMask)

  // Update control and status fields
  when (io.dmiWriteActive && isCSAddr) {
    accessSize := io.dmiReqData(19,17)
    autoIncrement := io.dmiReqData(16)
    autoRead := io.dmiReqData(15)
  }

  // Determine if the host is trying to start a new request
  val triggerSingleRead = io.dmiWriteActive && isCSAddr && io.dmiReqData(20)
  val triggerAutoRead = autoRead && io.dmiReadActive && isD0Addr
  val triggerSingleWrite = io.dmiWriteActive && isD0Addr
  val trigger = triggerSingleRead || triggerAutoRead || triggerSingleWrite

  // Record GBX fields upon entering transaction
  val latchRequest = state === s_idle && nextState =/= s_idle
  val reqWrite = RegEnable(next = triggerSingleWrite, enable = latchRequest, init = Bool(false))
  val reqBytes = RegEnable(next = 1.U << accessSize, enable = latchRequest)

  // Error tracking
  val misaligned = (a0_reg & (addrIncr - UInt(1))) =/= UInt(0)
  when (state =/= s_idle && (io.dmiWriteActive || io.dmiReadActive) && (isA0Addr || isD0Addr)) {
    errorState := UInt(4)
  } .elsewhen (state === s_idle && trigger && misaligned) {
    errorState := UInt(3)
  } .elsewhen (io.bus.b.valid && (io.bus.b.bits.resp === AXI4Parameters.RESP_SLVERR || io.bus.b.bits.resp === AXI4Parameters.RESP_DECERR)) {
    errorState := UInt(3)
  } .elsewhen (io.bus.r.valid && (io.bus.r.bits.resp === AXI4Parameters.RESP_SLVERR || io.bus.r.bits.resp === AXI4Parameters.RESP_DECERR)) {
    errorState := UInt(2)
  } .elsewhen (io.dmiWriteActive && isCSAddr) {
    errorState := errorState & (~io.dmiReqData(14,12))
  }

  // State management
  nextState := state
  when (state === s_idle) {
    when (trigger && !misaligned && errorState === UInt(0)) {
      nextState := s_reqWait
    }
  } .elsewhen (state === s_wWait) {
    when (reqWrite && io.bus.aw.ready) {
      nextState := Mux(io.bus.w.ready, s_respWait, s_wWait)
    } .elsewhen (!reqWrite && io.bus.ar.ready) {
      nextState := s_respWait
    }
  } .otherwise {
    val done = Mux(reqWrite, io.bus.b.valid, io.bus.r.valid)
    nextState := Mux(done, s_idle, s_respWait)
  }
  state := nextState

  val byteOffsetInBits = a0_reg(1,0) << UInt(3)

  // Data register management
  val ctrlRegWriteable = state === s_idle && errorState === UInt(0)
  when (io.bus.r.valid) {
    stale := Bool(false)
    d0_reg := io.bus.r.bits.data >> byteOffsetInBits
  } .elsewhen ((io.dmiReadActive || io.dmiWriteActive) && isD0Addr) {
    stale := Bool(true)
    when (io.dmiWriteActive && ctrlRegWriteable) {
      d0_reg := io.dmiReqData
    }
  }

  // Address register management
  val triggerAutoIncrement = autoIncrement && io.bus.b.valid
  when (io.dmiWriteActive && isA0Addr && ctrlRegWriteable) {
    a0_reg := io.dmiReqData
  } .elsewhen (triggerAutoIncrement) {
    a0_reg := a0_reg + addrIncr
  }

  io.bus.ar.valid       := state === s_reqWait && !reqWrite
  io.bus.ar.bits.addr   := a0_reg
  io.bus.ar.bits.cache  := AXI4Parameters.CACHE_BUFFERABLE
  io.bus.ar.bits.prot   := AXI4Parameters.PROT_PRIVILEDGED

  io.bus.r.ready        := Bool(true)

  io.bus.aw.valid       := state === s_reqWait && reqWrite
  io.bus.aw.bits.addr   := a0_reg
  io.bus.aw.bits.cache  := AXI4Parameters.CACHE_BUFFERABLE
  io.bus.aw.bits.prot   := AXI4Parameters.PROT_PRIVILEDGED

  io.bus.w.valid        := io.bus.aw.valid || state === s_wWait
  io.bus.w.bits.data    := d0_reg << byteOffsetInBits
  io.bus.w.bits.strb    := ((1.U << reqBytes) - 1.U) << a0_reg(1,0)

  io.bus.b.ready        := Bool(true)

  // Read mapping
  io.dmiRespValid := (io.dmiReadActive || io.dmiWriteActive) && (isCSAddr || isA0Addr || isD0Addr)
  when (isCSAddr) {
    io.dmiRespData := csReadVal
  } .elsewhen (isA0Addr) {
    io.dmiRespData := a0_reg
  } .otherwise {
    io.dmiRespData := d0_reg
  }

}
