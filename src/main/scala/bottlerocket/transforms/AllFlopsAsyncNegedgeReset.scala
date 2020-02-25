package bottlerocket
package transforms

import firrtl._
import firrtl.ir._
import firrtl.Mappers._

import collection.mutable

case class ResetFlopParams(tpe: GroundType, resetVal: BigInt)

object AResetNFlop {
  import Utils._

  val ModelName = "AResetNFlop"

  def bbTpe(tpe: GroundType): BundleType = {
    BundleType(Seq(
      Field("rst", Flip, BoolType),
      Field("clk", Flip, ClockType),
      Field("en", Flip, BoolType),
      Field("d", Flip, tpe),
      Field("q", Default, tpe)))
  }

  def bundleToPorts(tpe: BundleType): Seq[Port] = tpe.fields.map {
    case Field(name, Default, tpe) => Port(NoInfo, name, Output, tpe)
    case Field(name, Flip, tpe) => Port(NoInfo, name, Input, tpe)
  }

  def getField(e: Expression, field: String): WSubField = get_field(e.tpe, field) match {
    case Field(name, Default, tpe) => WSubField(e, name, tpe, gender(e))
    case Field(name, Flip, tpe) => WSubField(e, name, tpe, swap(gender(e)))
  }
}

class AResetNFlop(name: String, params: ResetFlopParams) {
  import AResetNFlop._
  val wParam = IntParam("WIDTH", bitWidth(params.tpe))
  val rstValParam = IntParam("RESET_VAL", params.resetVal)
  val refTpe = bbTpe(params.tpe)
  val defn = ExtModule(NoInfo, name, bundleToPorts(refTpe), ModelName, Seq(wParam, rstValParam))
}

class AllFlopsAsyncNegedgeReset extends Transform {
  import AResetNFlop._

  val inputForm = LowForm
  val outputForm = LowForm

  private val Zero = UIntLiteral(0, IntWidth(1))

  private def transformRegRefs(isReset: collection.Set[String])(expr: Expression): Expression = {
    expr match {
      case WRef(name, tpe: GroundType, RegKind, MALE) if (isReset(name)) =>
        val iRef = WRef(name, bbTpe(tpe), InstanceKind, MALE)
        getField(iRef, "q")
      case e => e.map(transformRegRefs(isReset))
    }
  }

  private def updateRefs(isReset: collection.Set[String])(stmt: Statement): Statement = {
    stmt.map(updateRefs(isReset)).map(transformRegRefs(isReset))
  }

  private def transformRegs(ns: Namespace, regBBs: mutable.Map[ResetFlopParams, AResetNFlop], isReset: mutable.Set[String])(stmt: Statement): Statement = {
    stmt match {
      case reg @ DefRegister(_, _, _, _, Zero, _) => reg
      case DefRegister(info, name, tpe: GroundType, clock, reset, resetVal: Literal) =>
        val params = ResetFlopParams(tpe, resetVal.value)
        val bb = regBBs.getOrElseUpdate(params, new AResetNFlop(ns.newName(ModelName), params))
        val inst = WDefInstance(info, name, bb.defn.name, bb.refTpe)
        val iRef = WRef(name, bb.refTpe, InstanceKind, MALE)
        val clockConn = Connect(info, getField(iRef, "clk"), clock)
        val enConn = Connect(info, getField(iRef, "en"), UIntLiteral(1, IntWidth(1)))
        val resetConn = Connect(info, getField(iRef, "rst"), reset)
        isReset += name
        Block(Seq(inst, clockConn, enConn, resetConn))
      case Connect(info, WRef(name, tpe: GroundType, RegKind, FEMALE), rhs) if (isReset(name)) =>
        val iRef = WRef(name, bbTpe(tpe), InstanceKind, MALE)
        Connect(info, getField(iRef, "d"), rhs)
      case wi @ WDefInstance(_, _, "AsyncResetReg", BundleType(fields)) =>
        val params = ResetFlopParams(Utils.BoolType, 0)
        val bb = regBBs.getOrElseUpdate(params, new AResetNFlop(ns.newName(ModelName), params))
        wi.copy(module = bb.defn.name, tpe = bb.refTpe)
      case s => s.map(transformRegs(ns, regBBs, isReset))
    }
  }

  def execute(state: CircuitState): CircuitState = {
    val moduleNS = Namespace(state.circuit)
    val regBBs = new mutable.LinkedHashMap[ResetFlopParams, AResetNFlop]

    def onModule(m: DefModule): DefModule = {
      val isReset = new mutable.LinkedHashSet[String]
      val intermediate = m.map(transformRegs(moduleNS, regBBs, isReset))
      intermediate.map(updateRefs(isReset))
    }

    val transformedCircuit = state.circuit.map(onModule)

    val models = regBBs.map { case (k, v) => v.defn }
    val newCircuit = transformedCircuit.copy(modules = transformedCircuit.modules ++ models)
    state.copy(circuit = newCircuit)
  }
}
