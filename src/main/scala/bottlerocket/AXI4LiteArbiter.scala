package bottlerocket

import chisel3._
import chisel3.util.{RegEnable, PriorityEncoder, Queue, log2Up}
import freechips.rocketchip._
import amba.axi4.{AXI4Bundle, AXI4Parameters}
import Params._

class AXI4LiteArbiter(nMasters: Int)(implicit p: config.Parameters) extends Module {
  val io = IO(new Bundle {
    val masters = Vec(nMasters, Flipped(AXI4LiteBundle(axiParams)))
    val slave = AXI4LiteBundle(axiParams)
  })

  // Read arbiter
  val outstandingReadQ = Module(new Queue(UInt(width = log2Up(nMasters).W), 2))
  val outstandingReadIdx = outstandingReadQ.io.deq.bits
  val allowNewReadCmd = outstandingReadQ.io.enq.ready
  val arbitratedReadIdx = PriorityEncoder(io.masters.map(m => (m.ar.valid)))
  outstandingReadQ.io.enq.valid := io.masters(arbitratedReadIdx).ar.fire
  outstandingReadQ.io.enq.bits := arbitratedReadIdx
  outstandingReadQ.io.deq.ready := io.masters(outstandingReadIdx).r.fire

  io.slave.ar.valid := io.masters(arbitratedReadIdx).ar.valid && allowNewReadCmd
  io.slave.ar.bits := io.masters(arbitratedReadIdx).ar.bits
  io.slave.r.ready := outstandingReadQ.io.deq.valid && io.masters(outstandingReadIdx).r.ready

  (0 until nMasters).foreach({ case n =>
    io.masters(n).ar.ready := io.slave.ar.ready && allowNewReadCmd && arbitratedReadIdx === n.U
    io.masters(n).r.valid := io.slave.r.valid && outstandingReadIdx === n.U
    io.masters(n).r.bits := io.slave.r.bits
  })

  // Write arbiter
  val outstandingWriteQ = Module(new Queue(UInt(width = log2Up(nMasters).W), 2))
  val outstandingWriteIdx = outstandingWriteQ.io.deq.bits
  val awSentWUnsent = RegInit(false.B)
  val awSentWUnsentIdx = Reg(UInt())
  val allowNewWriteCmd = outstandingWriteQ.io.enq.ready && !awSentWUnsent
  val arbitratedWriteIdx = Mux(awSentWUnsent, awSentWUnsentIdx, PriorityEncoder(io.masters.map(m => (m.aw.valid))))

  when (io.masters(arbitratedWriteIdx).aw.fire && !io.masters(arbitratedWriteIdx).w.fire) {
    awSentWUnsent := true.B
    awSentWUnsentIdx := arbitratedWriteIdx
  } .elsewhen (io.masters(arbitratedWriteIdx).w.fire) {
    awSentWUnsent := false.B
  }

  outstandingWriteQ.io.enq.valid := io.masters(arbitratedWriteIdx).w.fire
  outstandingWriteQ.io.enq.bits := arbitratedWriteIdx
  outstandingWriteQ.io.deq.ready := io.masters(outstandingWriteIdx).b.fire

  io.slave.aw.valid := io.masters(arbitratedWriteIdx).aw.valid && allowNewWriteCmd
  io.slave.aw.bits := io.masters(arbitratedWriteIdx).aw.bits

  io.slave.w.valid := io.masters(arbitratedWriteIdx).w.valid && (allowNewWriteCmd || awSentWUnsent)
  io.slave.w.bits := io.masters(arbitratedWriteIdx).w.bits

  io.slave.b.ready := outstandingWriteQ.io.deq.valid && io.masters(outstandingWriteIdx).b.ready

  (0 until nMasters).foreach({ case n =>
    io.masters(n).aw.ready := io.slave.aw.ready && allowNewWriteCmd && arbitratedWriteIdx === n.U
    io.masters(n).w.ready := io.slave.w.ready && (allowNewWriteCmd  || awSentWUnsent) && arbitratedWriteIdx === n.U
    io.masters(n).b.valid := io.slave.b.valid && outstandingWriteIdx === n.U
    io.masters(n).b.bits := io.slave.b.bits
  })
}
