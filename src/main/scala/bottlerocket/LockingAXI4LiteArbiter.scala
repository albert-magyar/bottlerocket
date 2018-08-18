package bottlerocket

import chisel3._
import chisel3.util.{RegEnable, PriorityEncoder}
import freechips.rocketchip._
import amba.axi4.{AXI4Bundle, AXI4Parameters}
import Params._

class LockingAXI4LiteArbiter(nMasters: Int)(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val masters = Vec(nMasters, Flipped(AXI4LiteBundle(axiParams)))
    val slave = AXI4LiteBundle(axiParams)
  })

  val s_idle :: s_reqWait :: s_wWait :: s_respWait :: Nil = chisel3.util.Enum(UInt(), 4)
  val arbitratedBusIdx = PriorityEncoder(io.masters.map(m => (m.ar.valid || m.aw.valid || m.ar.valid)))
  val state = RegInit(s_idle)
  val next_state = Wire(UInt())
  val activeBusIdx = Reg(UInt())
  val activeIsWrite = Reg(Bool())
  val selectedBusIdx = Mux(state === s_idle, arbitratedBusIdx, activeBusIdx)
  val selectedBus = io.masters(selectedBusIdx)
  next_state := state
  when (state === s_idle || state === s_reqWait) {
    when (selectedBus.ar.fire || (selectedBus.aw.fire && selectedBus.w.fire)) {
      next_state := s_respWait
    } .elsewhen (selectedBus.aw.fire) {
      next_state := s_wWait
    } .elsewhen (selectedBus.ar.valid || selectedBus.aw.valid) {
      next_state := s_reqWait
    }
  } .elsewhen (state === s_wWait) {
    when (selectedBus.w.fire) {
      next_state := s_respWait
    }
  } .otherwise {
    when (selectedBus.r.fire || selectedBus.b.fire) {
      next_state := s_idle
    }
  }
  state := next_state

  when (state === s_idle && next_state =/= s_idle) {
    activeBusIdx := arbitratedBusIdx
    activeIsWrite := selectedBus.aw.valid
  }

  val reqOpen = state === s_idle || state === s_reqWait
  val wdataOpen = reqOpen || state === s_wWait
  io.masters.zipWithIndex.foreach({
    case (bus, i) =>
      when (selectedBusIdx === i.U) {
        bus.ar.ready := reqOpen && !bus.aw.valid
        bus.aw.ready := reqOpen
        bus.w.ready := wdataOpen
        bus.r.valid := io.slave.r.valid
        bus.b.valid := io.slave.b.valid
      } .otherwise {
        bus.ar.ready := false.B
        bus.aw.ready := false.B
        bus.w.ready := false.B
        bus.r.valid := false.B
        bus.b.valid := false.B
      }
      bus.r.bits := io.slave.r.bits
      bus.b.bits := io.slave.b.bits
  })

  io.slave.ar.valid := reqOpen && selectedBus.ar.valid && !selectedBus.aw.valid
  io.slave.ar.bits := selectedBus.ar.bits
  io.slave.aw.valid := reqOpen && selectedBus.aw.valid
  io.slave.aw.bits := selectedBus.aw.bits
  io.slave.w.valid := wdataOpen && selectedBus.w.valid
  io.slave.w.bits := selectedBus.w.bits
  io.slave.r.ready := selectedBus.r.ready
  io.slave.b.ready := selectedBus.b.ready
}
