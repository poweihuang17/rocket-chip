// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package tile

import Chisel._
import Chisel.ImplicitConversions._
import FPConstants._
import rocket.DecodeLogic
import rocket.Instructions._
import uncore.constants.MemoryOpConstants._
import config._
import util._

case class FPUParams(
  divSqrt: Boolean = true,
  sfmaLatency: Int = 3,
  dfmaLatency: Int = 4
)

object FPConstants
{
  def FCMD_ADD =    BitPat("b0??00")
  def FCMD_SUB =    BitPat("b0??01")
  def FCMD_MUL =    BitPat("b0??10")
  def FCMD_MADD =   BitPat("b1??00")
  def FCMD_MSUB =   BitPat("b1??01")
  def FCMD_NMSUB =  BitPat("b1??10")
  def FCMD_NMADD =  BitPat("b1??11")
  def FCMD_DIV =    BitPat("b?0011")
  def FCMD_SQRT =   BitPat("b?1011")
  def FCMD_SGNJ =   BitPat("b??1?0")
  def FCMD_MINMAX = BitPat("b?01?1")
  def FCMD_CVT_FF = BitPat("b??0??")
  def FCMD_CVT_IF = BitPat("b?10??")
  def FCMD_CMP =    BitPat("b?01??")
  def FCMD_MV_XF =  BitPat("b?11??")
  def FCMD_CVT_FI = BitPat("b??0??")
  def FCMD_MV_FX =  BitPat("b??1??")
  def FCMD_X =      BitPat("b?????")
  val FCMD_WIDTH = 5

  val RM_SZ = 3
  val FLAGS_SZ = 5
}

trait HasFPUCtrlSigs {
  val cmd = Bits(width = FCMD_WIDTH)
  val ldst = Bool()
  val wen = Bool()
  val ren1 = Bool()
  val ren2 = Bool()
  val ren3 = Bool()
  val swap12 = Bool()
  val swap23 = Bool()
  val singleIn = Bool()
  val singleOut = Bool()
  val fromint = Bool()
  val toint = Bool()
  val fastpipe = Bool()
  val fma = Bool()
  val div = Bool()
  val sqrt = Bool()
  val wflags = Bool()
}

class FPUCtrlSigs extends Bundle with HasFPUCtrlSigs

class FPUDecoder(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val inst = Bits(INPUT, 32)
    val sigs = new FPUCtrlSigs().asOutput
  }

  val default =       List(FCMD_X,      X,X,X,X,X,X,X,X,X,X,X,X,X,X,X,X)
  val f =
    Array(FLW      -> List(FCMD_X,      Y,Y,N,N,N,X,X,X,Y,N,N,N,N,N,N,N),
          FSW      -> List(FCMD_MV_XF,  Y,N,N,Y,N,Y,X,N,Y,N,Y,N,N,N,N,N),
          FMV_S_X  -> List(FCMD_MV_FX,  N,Y,N,N,N,X,X,X,Y,Y,N,N,N,N,N,N),
          FCVT_S_W -> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y),
          FCVT_S_WU-> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y),
          FCVT_S_L -> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y),
          FCVT_S_LU-> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,Y,Y,Y,N,N,N,N,N,Y),
          FMV_X_S  -> List(FCMD_MV_XF,  N,N,Y,N,N,N,X,N,Y,N,Y,N,N,N,N,N),
          FCLASS_S -> List(FCMD_MV_XF,  N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,N),
          FCVT_W_S -> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y),
          FCVT_WU_S-> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y),
          FCVT_L_S -> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y),
          FCVT_LU_S-> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,Y,Y,N,Y,N,N,N,N,Y),
          FEQ_S    -> List(FCMD_CMP,    N,N,Y,Y,N,N,N,Y,Y,N,Y,N,N,N,N,Y),
          FLT_S    -> List(FCMD_CMP,    N,N,Y,Y,N,N,N,Y,Y,N,Y,N,N,N,N,Y),
          FLE_S    -> List(FCMD_CMP,    N,N,Y,Y,N,N,N,Y,Y,N,Y,N,N,N,N,Y),
          FSGNJ_S  -> List(FCMD_SGNJ,   N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,N),
          FSGNJN_S -> List(FCMD_SGNJ,   N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,N),
          FSGNJX_S -> List(FCMD_SGNJ,   N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,N),
          FMIN_S   -> List(FCMD_MINMAX, N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,Y),
          FMAX_S   -> List(FCMD_MINMAX, N,Y,Y,Y,N,N,N,Y,Y,N,N,Y,N,N,N,Y),
          FADD_S   -> List(FCMD_ADD,    N,Y,Y,Y,N,N,Y,Y,Y,N,N,N,Y,N,N,Y),
          FSUB_S   -> List(FCMD_SUB,    N,Y,Y,Y,N,N,Y,Y,Y,N,N,N,Y,N,N,Y),
          FMUL_S   -> List(FCMD_MUL,    N,Y,Y,Y,N,N,N,Y,Y,N,N,N,Y,N,N,Y),
          FMADD_S  -> List(FCMD_MADD,   N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y),
          FMSUB_S  -> List(FCMD_MSUB,   N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y),
          FNMADD_S -> List(FCMD_NMADD,  N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y),
          FNMSUB_S -> List(FCMD_NMSUB,  N,Y,Y,Y,Y,N,N,Y,Y,N,N,N,Y,N,N,Y),
          FDIV_S   -> List(FCMD_DIV,    N,Y,Y,Y,N,N,N,Y,Y,N,N,N,N,Y,N,Y),
          FSQRT_S  -> List(FCMD_SQRT,   N,Y,Y,N,N,Y,X,Y,Y,N,N,N,N,N,Y,Y))
  val d =
    Array(FLD      -> List(FCMD_X,      Y,Y,N,N,N,X,X,X,N,N,N,N,N,N,N,N),
          FSD      -> List(FCMD_MV_XF,  Y,N,N,Y,N,Y,X,N,N,N,Y,N,N,N,N,N),
          FMV_D_X  -> List(FCMD_MV_FX,  N,Y,N,N,N,X,X,X,N,Y,N,N,N,N,N,N),
          FCVT_D_W -> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y),
          FCVT_D_WU-> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y),
          FCVT_D_L -> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y),
          FCVT_D_LU-> List(FCMD_CVT_FI, N,Y,N,N,N,X,X,N,N,Y,N,N,N,N,N,Y),
          FMV_X_D  -> List(FCMD_MV_XF,  N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,N),
          FCLASS_D -> List(FCMD_MV_XF,  N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,N),
          FCVT_W_D -> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y),
          FCVT_WU_D-> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y),
          FCVT_L_D -> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y),
          FCVT_LU_D-> List(FCMD_CVT_IF, N,N,Y,N,N,N,X,N,N,N,Y,N,N,N,N,Y),
          FCVT_S_D -> List(FCMD_CVT_FF, N,Y,Y,N,N,N,X,N,Y,N,N,Y,N,N,N,Y),
          FCVT_D_S -> List(FCMD_CVT_FF, N,Y,Y,N,N,N,X,Y,N,N,N,Y,N,N,N,Y),
          FEQ_D    -> List(FCMD_CMP,    N,N,Y,Y,N,N,N,N,N,N,Y,N,N,N,N,Y),
          FLT_D    -> List(FCMD_CMP,    N,N,Y,Y,N,N,N,N,N,N,Y,N,N,N,N,Y),
          FLE_D    -> List(FCMD_CMP,    N,N,Y,Y,N,N,N,N,N,N,Y,N,N,N,N,Y),
          FSGNJ_D  -> List(FCMD_SGNJ,   N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,N),
          FSGNJN_D -> List(FCMD_SGNJ,   N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,N),
          FSGNJX_D -> List(FCMD_SGNJ,   N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,N),
          FMIN_D   -> List(FCMD_MINMAX, N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,Y),
          FMAX_D   -> List(FCMD_MINMAX, N,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,N,Y),
          FADD_D   -> List(FCMD_ADD,    N,Y,Y,Y,N,N,Y,N,N,N,N,N,Y,N,N,Y),
          FSUB_D   -> List(FCMD_SUB,    N,Y,Y,Y,N,N,Y,N,N,N,N,N,Y,N,N,Y),
          FMUL_D   -> List(FCMD_MUL,    N,Y,Y,Y,N,N,N,N,N,N,N,N,Y,N,N,Y),
          FMADD_D  -> List(FCMD_MADD,   N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y),
          FMSUB_D  -> List(FCMD_MSUB,   N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y),
          FNMADD_D -> List(FCMD_NMADD,  N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y),
          FNMSUB_D -> List(FCMD_NMSUB,  N,Y,Y,Y,Y,N,N,N,N,N,N,N,Y,N,N,Y),
          FDIV_D   -> List(FCMD_DIV,    N,Y,Y,Y,N,N,N,N,N,N,N,N,N,Y,N,Y),
          FSQRT_D  -> List(FCMD_SQRT,   N,Y,Y,N,N,Y,X,N,N,N,N,N,N,N,Y,Y))

  val insns = fLen match {
    case 32 => f
    case 64 => f ++ d
  }
  val decoder = DecodeLogic(io.inst, default, insns)
  val s = io.sigs
  val sigs = Seq(s.cmd, s.ldst, s.wen, s.ren1, s.ren2, s.ren3, s.swap12,
                 s.swap23, s.singleIn, s.singleOut, s.fromint, s.toint,
                 s.fastpipe, s.fma, s.div, s.sqrt, s.wflags)
  sigs zip decoder map {case(s,d) => s := d}
}

class FPUCoreIO(implicit p: Parameters) extends CoreBundle()(p) {
  val inst = Bits(INPUT, 32)
  val fromint_data = Bits(INPUT, xLen)

  val fcsr_rm = Bits(INPUT, FPConstants.RM_SZ)
  val fcsr_flags = Valid(Bits(width = FPConstants.FLAGS_SZ))

  val store_data = Bits(OUTPUT, fLen)
  val toint_data = Bits(OUTPUT, xLen)

  val dmem_resp_val = Bool(INPUT)
  val dmem_resp_type = Bits(INPUT, 3)
  val dmem_resp_tag = UInt(INPUT, 5)
  val dmem_resp_data = Bits(INPUT, fLen)

  val valid = Bool(INPUT)
  val fcsr_rdy = Bool(OUTPUT)
  val nack_mem = Bool(OUTPUT)
  val illegal_rm = Bool(OUTPUT)
  val killx = Bool(INPUT)
  val killm = Bool(INPUT)
  val dec = new FPUCtrlSigs().asOutput
  val sboard_set = Bool(OUTPUT)
  val sboard_clr = Bool(OUTPUT)
  val sboard_clra = UInt(OUTPUT, 5)
}

class FPUIO(implicit p: Parameters) extends FPUCoreIO ()(p) {
  val cp_req = Decoupled(new FPInput()).flip //cp doesn't pay attn to kill sigs
  val cp_resp = Decoupled(new FPResult())
}

class FPResult(implicit p: Parameters) extends CoreBundle()(p) {
  val data = Bits(width = fLen+1)
  val exc = Bits(width = 5)
}

class FPInput(implicit p: Parameters) extends CoreBundle()(p) with HasFPUCtrlSigs {
  val rm = Bits(width = 3)
  val typ = Bits(width = 2)
  val in1 = Bits(width = fLen+1)
  val in2 = Bits(width = fLen+1)
  val in3 = Bits(width = fLen+1)

  override def cloneType = new FPInput().asInstanceOf[this.type]
}

object ClassifyRecFN {
  def apply(expWidth: Int, sigWidth: Int, in: UInt) = {
    val sign = in(sigWidth + expWidth)
    val exp = in(sigWidth + expWidth - 1, sigWidth - 1)
    val sig = in(sigWidth - 2, 0)

    val code        = exp(expWidth,expWidth-2)
    val codeHi      = code(2, 1)
    val isSpecial   = codeHi === UInt(3)

    val isHighSubnormalIn = exp(expWidth-2, 0) < UInt(2)
    val isSubnormal = code === UInt(1) || codeHi === UInt(1) && isHighSubnormalIn
    val isNormal = codeHi === UInt(1) && !isHighSubnormalIn || codeHi === UInt(2)
    val isZero = code === UInt(0)
    val isInf = isSpecial && !exp(expWidth-2)
    val isNaN = code.andR
    val isSNaN = isNaN && !sig(sigWidth-2)
    val isQNaN = isNaN && sig(sigWidth-2)

    Cat(isQNaN, isSNaN, isInf && !sign, isNormal && !sign,
        isSubnormal && !sign, isZero && !sign, isZero && sign,
        isSubnormal && sign, isNormal && sign, isInf && sign)
  }
}

case class FType(exp: Int, sig: Int) {
  def ieeeWidth = exp + sig
  def recodedWidth = ieeeWidth + 1

  def qNaN = UInt((BigInt(7) << (exp + sig - 3)) + (BigInt(1) << (sig - 2)), exp + sig + 1)
  def isNaN(x: UInt) = x(sig + exp - 1, sig + exp - 3).andR
  def isSNaN(x: UInt) = isNaN(x) && !x(sig - 2)

  // convert between formats, ignoring rounding, range, NaN
	def unsafeConvert(x: UInt, to: FType) = {
    val sign = x(sig + exp)
    val fractIn = x(sig - 2, 0)
    val expIn = x(sig + exp - 1, sig - 1)
    val fractOut = fractIn << to.sig >> sig
    val expOut = {
      val expCode = expIn(exp, exp - 2)
      val commonCase = (expIn + (1 << to.exp)) - (1 << exp)
      Mux(expCode === 0 || expCode >= 6, Cat(expCode, commonCase(to.exp - 3, 0)), commonCase(to.exp, 0))
    }
    Cat(sign, expOut, fractOut)
  }

  def recode(x: UInt) = hardfloat.recFNFromFN(exp, sig, x)
  def ieee(x: UInt) = hardfloat.fNFromRecFN(exp, sig, x)
}

object FType {
  val S = new FType(8, 24)
  val D = new FType(11, 53)

  val all = List(S, D)

  def classify(x: UInt) = all.find(_.recodedWidth == x.getWidth).get
}

/** Format conversion without rounding or NaN handling */
object RecFNToRecFN_noncompliant {
  def apply(in: UInt, inExpWidth: Int, inSigWidth: Int, outExpWidth: Int, outSigWidth: Int) = {
  }
}

trait HasFPUParameters {
  val fLen: Int
  val (sExpWidth, sSigWidth) = (FType.S.exp, FType.S.sig)
  val (dExpWidth, dSigWidth) = (FType.D.exp, FType.D.sig)
  val floatTypes = FType.all.filter(_.ieeeWidth <= fLen)
  val maxType = floatTypes.last
  val maxExpWidth = maxType.exp
  val maxSigWidth = maxType.sig

  def tagWidth = log2Ceil(floatTypes.size)
  def tagPos = maxType.sig - 1
  def tagMask = UInt(((BigInt(1) << tagWidth) - 1) << tagPos, maxType.recodedWidth)

  def box(x: UInt): UInt = {
    val xType = FType.classify(x)
    val xTag = floatTypes.indexOf(xType)
    if (xType == maxType) {
      // For max-width NaNs, fill in the tag.
      Mux(xType.isNaN(x), (x & ~tagMask) | (xTag << tagPos), x)
    } else {
      // Otherwise, box it.
      val exp = BigInt(15) << (maxType.exp + maxType.sig - 3)
      val sig = (BigInt(1) << (maxType.sig - 2)) - (BigInt(1) << xType.recodedWidth)
      UInt(exp | sig | (xTag << tagPos), maxType.recodedWidth) | x
    }
  }
  def box(x: UInt, tag: UInt): UInt = {
    val opts = floatTypes.map(t => box(x(t.recodedWidth-1, 0)))
    opts(tag)
  }
  def tag(x: UInt): UInt = {
    require(x.getWidth == maxType.recodedWidth)
    Mux(maxType.isNaN(x), x(tagPos + tagWidth - 1, tagPos), UInt(floatTypes.size - 1))
  }
  def unbox(x: UInt, t: FType): UInt = Mux(tag(x) === floatTypes.indexOf(t), x, x | t.qNaN)
  def unbox(x: UInt, tag: UInt): UInt = {
    val opts = floatTypes.map(t => unbox(x, t))
    opts(tag)
  }

  def recode(x: UInt, tag: UInt): UInt = {
    val recoded = floatTypes.map(t => box(t.recode(x)))
    var res = recoded.head
    for (((t, prev), r) <- floatTypes.tail zip floatTypes zip recoded)
      res = Mux(x(t.ieeeWidth-1, prev.ieeeWidth).andR, r, res)
    res
  }
  def ieee(x: UInt): UInt = {
    val unrec = floatTypes.map(t => t.ieee(x) | UInt((BigInt(1) << fLen) - (BigInt(1) << t.ieeeWidth)))
    unrec(tag(x))
  }
}

abstract class FPUModule(implicit p: Parameters) extends CoreModule()(p) with HasFPUParameters

class FPToInt(implicit p: Parameters) extends FPUModule()(p) {
  class Output extends Bundle {
    val lt = Bool()
    val store = Bits(width = fLen)
    val toint = Bits(width = xLen)
    val exc = Bits(width = 5)
    override def cloneType = new Output().asInstanceOf[this.type]
  }
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val as_double = new FPInput().asOutput
    val out = Valid(new Output)
  }

  val in = Reg(new FPInput)
  val valid = Reg(next=io.in.valid)

  when (io.in.valid) {
    in := io.in.bits
    if (fLen > 32) when (io.in.bits.singleIn) {
      in.in1 := FType.S.unsafeConvert(io.in.bits.in1, maxType)
      in.in2 := FType.S.unsafeConvert(io.in.bits.in2, maxType)
    }
  }

  val classify_s = ClassifyRecFN(sExpWidth, sSigWidth, in.in1)
  val classify_out = fLen match {
    case 32 => classify_s
    case 64 =>
      val classify_d = ClassifyRecFN(dExpWidth, dSigWidth, in.in1)
      Mux(in.singleIn, classify_s, classify_d)
  }

  val dcmp = Module(new hardfloat.CompareRecFN(maxExpWidth, maxSigWidth))
  dcmp.io.a := in.in1
  dcmp.io.b := in.in2
  dcmp.io.signaling := !in.rm(1)

  io.out.bits.store := ieee(in.in1)
  io.out.bits.toint := Mux(in.rm(0), classify_out, io.out.bits.store)
  io.out.bits.exc := Bits(0)

  when (in.cmd === FCMD_CMP) {
    io.out.bits.toint := (~in.rm & Cat(dcmp.io.lt, dcmp.io.eq)).orR
    io.out.bits.exc := dcmp.io.exceptionFlags
  }
  when (in.cmd === FCMD_CVT_IF) {
    val minXLen = 32
    val n = log2Ceil(xLen/minXLen) + 1
    for (i <- 0 until n) {
      val conv = Module(new hardfloat.RecFNToIN(maxExpWidth, maxSigWidth, minXLen << i))
      conv.io.in := in.in1
      conv.io.roundingMode := in.rm
      conv.io.signedOut := ~in.typ(0)
      when (in.typ.extract(log2Ceil(n), 1) === i) {
        io.out.bits.toint := conv.io.out.sextTo(xLen)
        io.out.bits.exc := Cat(conv.io.intExceptionFlags(2, 1).orR, UInt(0, 3), conv.io.intExceptionFlags(0))
      }
    }
  }

  io.out.valid := valid
  io.out.bits.lt := dcmp.io.lt
  io.as_double := in
}

class IntToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
  }

  val in = Pipe(io.in)

  val mux = Wire(new FPResult)
  mux.exc := Bits(0)
  mux.data := recode(in.bits.in1, !in.bits.singleOut)
  mux.data := hardfloat.recFNFromFN(sExpWidth, sSigWidth, in.bits.in1)
  if (fLen > 32) when (!in.bits.singleOut) {
    mux.data := hardfloat.recFNFromFN(dExpWidth, dSigWidth, in.bits.in1)
  }

  val intValue = {
    val minXLen = 32
    val n = log2Ceil(xLen/minXLen) + 1
    val res = Wire(init = in.bits.in1.asSInt)
    for (i <- 0 until n-1) {
      val smallInt = in.bits.in1((minXLen << i) - 1, 0)
      when (in.bits.typ.extract(log2Ceil(n), 1) === i) {
        res := Mux(in.bits.typ(0), smallInt.zext, smallInt.asSInt)
      }
    }
    res.asUInt
  }

  when (in.bits.cmd === FCMD_CVT_FI) {
    val l2s = Module(new hardfloat.INToRecFN(xLen, sExpWidth, sSigWidth))
    l2s.io.signedIn := ~in.bits.typ(0)
    l2s.io.in := intValue
    l2s.io.roundingMode := in.bits.rm
    mux.data := l2s.io.out
    mux.exc := l2s.io.exceptionFlags

    fLen match {
      case 32 =>
      case 64 =>
        val l2d = Module(new hardfloat.INToRecFN(xLen, dExpWidth, dSigWidth))
        l2d.io.signedIn := ~in.bits.typ(0)
        l2d.io.in := intValue
        l2d.io.roundingMode := in.bits.rm
        mux.data := Cat(l2d.io.out >> l2s.io.out.getWidth, l2s.io.out)
        when (!in.bits.singleOut) {
          mux.data := l2d.io.out
          mux.exc := l2d.io.exceptionFlags
        }
    }
  }

  io.out <> Pipe(in.valid, mux, latency-1)
}

class FPToFP(val latency: Int)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
    val lt = Bool(INPUT) // from FPToInt
  }

  val in = Pipe(io.in)

  val signNum = Mux(in.bits.rm(1), in.bits.in1 ^ in.bits.in2, Mux(in.bits.rm(0), ~in.bits.in2, in.bits.in2))
  val fsgnj = Cat(signNum(fLen), in.bits.in1(fLen-1, 0))

  val mux = Wire(new FPResult)
  mux.exc := UInt(0)
  mux.data := fsgnj

  when (in.bits.cmd === FCMD_MINMAX) {
    val isnan1 = maxType.isNaN(in.bits.in1)
    val isnan2 = maxType.isNaN(in.bits.in2)
    val isInvalid = maxType.isSNaN(in.bits.in1) || maxType.isSNaN(in.bits.in2)
    val isNaNOut = isInvalid || (isnan1 && isnan2)
    val isLHS = isnan2 || in.bits.rm(0) =/= io.lt && !isnan1
    mux.exc := isInvalid << 4
    mux.data := Mux(isNaNOut, maxType.qNaN, Mux(isLHS, in.bits.in1, in.bits.in2))
  }

  fLen match {
    case 32 =>
    case 64 =>
      when (in.bits.cmd === FCMD_CVT_FF) {
        val d2s = Module(new hardfloat.RecFNToRecFN(dExpWidth, dSigWidth, sExpWidth, sSigWidth))
        d2s.io.in := in.bits.in1
        d2s.io.roundingMode := in.bits.rm

        val s2d = Module(new hardfloat.RecFNToRecFN(sExpWidth, sSigWidth, dExpWidth, dSigWidth))
        s2d.io.in := in.bits.in1
        s2d.io.roundingMode := in.bits.rm

        when (in.bits.singleOut) {
          mux.data := Cat(s2d.io.out >> d2s.io.out.getWidth, d2s.io.out)
          mux.exc := d2s.io.exceptionFlags
        }.otherwise {
          mux.data := s2d.io.out
          mux.exc := s2d.io.exceptionFlags
        }
      }
  }

  io.out <> Pipe(in.valid, mux, latency-1)
}

class FPUFMAPipe(val latency: Int, t: FType)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new Bundle {
    val in = Valid(new FPInput).flip
    val out = Valid(new FPResult)
  }

  val width = t.sig + t.exp
  val one = UInt(1) << (width-1)
  val zero = (io.in.bits.in1(width) ^ io.in.bits.in2(width)) << width

  val valid = Reg(next=io.in.valid)
  val in = Reg(new FPInput)
  when (io.in.valid) {
    val cmd_fma = io.in.bits.ren3
    val cmd_addsub = io.in.bits.swap23
    in := io.in.bits
    when (cmd_addsub) { in.in2 := one }
    when (!(cmd_fma || cmd_addsub)) { in.in3 := zero }
    in.cmd := Cat(io.in.bits.cmd(1) & (cmd_fma || cmd_addsub), io.in.bits.cmd(0))
  }

  val fma = Module(new hardfloat.MulAddRecFN(t.exp, t.sig))
  fma.io.op := in.cmd
  fma.io.roundingMode := in.rm
  fma.io.a := in.in1
  fma.io.b := in.in2
  fma.io.c := in.in3

  val res = Wire(new FPResult)
  res.data := fma.io.out
  res.exc := fma.io.exceptionFlags
  io.out := Pipe(valid, res, latency-1)
}

class FPU(cfg: FPUParams)(implicit p: Parameters) extends FPUModule()(p) {
  val io = new FPUIO

  val ex_reg_valid = Reg(next=io.valid, init=Bool(false))
  val req_valid = ex_reg_valid || io.cp_req.valid
  val ex_reg_inst = RegEnable(io.inst, io.valid)
  val ex_cp_valid = io.cp_req.fire()
  val mem_reg_valid = Reg(next=ex_reg_valid && !io.killx || ex_cp_valid, init=Bool(false))
  val mem_reg_inst = RegEnable(ex_reg_inst, ex_reg_valid)
  val mem_cp_valid = Reg(next=ex_cp_valid, init=Bool(false))
  val killm = (io.killm || io.nack_mem) && !mem_cp_valid
  val wb_reg_valid = Reg(next=mem_reg_valid && (!killm || mem_cp_valid), init=Bool(false))
  val wb_cp_valid = Reg(next=mem_cp_valid, init=Bool(false))

  val fp_decoder = Module(new FPUDecoder)
  fp_decoder.io.inst := io.inst

  val cp_ctrl = Wire(new FPUCtrlSigs)
  cp_ctrl <> io.cp_req.bits
  io.cp_resp.valid := Bool(false)
  io.cp_resp.bits.data := UInt(0)

  val id_ctrl = fp_decoder.io.sigs
  val ex_ctrl = Mux(ex_cp_valid, cp_ctrl, RegEnable(id_ctrl, io.valid))
  val mem_ctrl = RegEnable(ex_ctrl, req_valid)
  val wb_ctrl = RegEnable(mem_ctrl, mem_reg_valid)

  // load response
  val load_wb = Reg(next=io.dmem_resp_val)
  val load_wb_double = RegEnable(io.dmem_resp_type(0), io.dmem_resp_val)
  val load_wb_data = RegEnable(io.dmem_resp_data, io.dmem_resp_val)
  val load_wb_tag = RegEnable(io.dmem_resp_tag, io.dmem_resp_val)

  // regfile
  val regfile = Mem(32, Bits(width = fLen+1))
  when (load_wb) {
    regfile(load_wb_tag) := recode(load_wb_data, load_wb_double)
    if (enableCommitLog)
      printf("f%d p%d 0x%x\n", load_wb_tag, load_wb_tag + 32, load_wb_data)
  }

  val ex_ra1::ex_ra2::ex_ra3::Nil = List.fill(3)(Reg(UInt()))
  when (io.valid) {
    when (id_ctrl.ren1) {
      when (!id_ctrl.swap12) { ex_ra1 := io.inst(19,15) }
      when (id_ctrl.swap12) { ex_ra2 := io.inst(19,15) }
    }
    when (id_ctrl.ren2) {
      when (id_ctrl.swap12) { ex_ra1 := io.inst(24,20) }
      when (id_ctrl.swap23) { ex_ra3 := io.inst(24,20) }
      when (!id_ctrl.swap12 && !id_ctrl.swap23) { ex_ra2 := io.inst(24,20) }
    }
    when (id_ctrl.ren3) { ex_ra3 := io.inst(31,27) }
  }
  val ex_rm = Mux(ex_reg_inst(14,12) === Bits(7), io.fcsr_rm, ex_reg_inst(14,12))

  val req = Wire(new FPInput)
  def readAndUnbox(addr: UInt) = unbox(regfile(addr), !ex_ctrl.singleIn)
  req := ex_ctrl
  req.rm := ex_rm
  req.in1 := readAndUnbox(ex_ra1)
  req.in2 := readAndUnbox(ex_ra2)
  req.in3 := readAndUnbox(ex_ra3)
  req.typ := ex_reg_inst(21,20)
  when (ex_cp_valid) {
    req := io.cp_req.bits
    when (io.cp_req.bits.swap23) {
      req.in2 := io.cp_req.bits.in3
      req.in3 := io.cp_req.bits.in2
    }
  }

  val sfma = Module(new FPUFMAPipe(cfg.sfmaLatency, FType.S))
  sfma.io.in.valid := req_valid && ex_ctrl.fma && ex_ctrl.singleOut
  sfma.io.in.bits := req

  val fpiu = Module(new FPToInt)
  fpiu.io.in.valid := req_valid && (ex_ctrl.toint || ex_ctrl.div || ex_ctrl.sqrt || ex_ctrl.cmd === FCMD_MINMAX)
  fpiu.io.in.bits := req
  io.store_data := fpiu.io.out.bits.store
  io.toint_data := fpiu.io.out.bits.toint
  when(fpiu.io.out.valid && mem_cp_valid && mem_ctrl.toint){
    io.cp_resp.bits.data := fpiu.io.out.bits.toint
    io.cp_resp.valid := Bool(true)
  }

  val ifpu = Module(new IntToFP(2))
  ifpu.io.in.valid := req_valid && ex_ctrl.fromint
  ifpu.io.in.bits := req
  ifpu.io.in.bits.in1 := Mux(ex_cp_valid, io.cp_req.bits.in1, io.fromint_data)

  val fpmu = Module(new FPToFP(2))
  fpmu.io.in.valid := req_valid && ex_ctrl.fastpipe
  fpmu.io.in.bits := req
  fpmu.io.lt := fpiu.io.out.bits.lt

  val divSqrt_wen = Reg(next=Bool(false))
  val divSqrt_inReady = Wire(init=Bool(false))
  val divSqrt_waddr = Reg(UInt(width = 5))
  val divSqrt_single = Reg(Bool())
  val divSqrt_wdata = Wire(UInt(width = fLen+1))
  val divSqrt_flags = Wire(UInt(width = 5))
  val divSqrt_in_flight = Reg(init=Bool(false))
  val divSqrt_killed = Reg(Bool())

  // writeback arbitration
  case class Pipe(p: Module, lat: Int, cond: (FPUCtrlSigs) => Bool, res: FPResult)
  val pipes = List(
    Pipe(fpmu, fpmu.latency, (c: FPUCtrlSigs) => c.fastpipe, fpmu.io.out.bits),
    Pipe(ifpu, ifpu.latency, (c: FPUCtrlSigs) => c.fromint, ifpu.io.out.bits),
    Pipe(sfma, sfma.latency, (c: FPUCtrlSigs) => c.fma && c.singleOut, sfma.io.out.bits)) ++
    (fLen > 32).option({
          val dfma = Module(new FPUFMAPipe(cfg.dfmaLatency, FType.D))
          dfma.io.in.valid := req_valid && ex_ctrl.fma && !ex_ctrl.singleOut
          dfma.io.in.bits := req
          Pipe(dfma, dfma.latency, (c: FPUCtrlSigs) => c.fma && !c.singleOut, dfma.io.out.bits)
        })
  def latencyMask(c: FPUCtrlSigs, offset: Int) = {
    require(pipes.forall(_.lat >= offset))
    pipes.map(p => Mux(p.cond(c), UInt(1 << p.lat-offset), UInt(0))).reduce(_|_)
  }
  def pipeid(c: FPUCtrlSigs) = pipes.zipWithIndex.map(p => Mux(p._1.cond(c), UInt(p._2), UInt(0))).reduce(_|_)
  val maxLatency = pipes.map(_.lat).max
  val memLatencyMask = latencyMask(mem_ctrl, 2)

  class WBInfo extends Bundle {
    val rd = UInt(width = 5)
    val single = Bool()
    val cp = Bool()
    val pipeid = UInt(width = log2Ceil(pipes.size))
    override def cloneType: this.type = new WBInfo().asInstanceOf[this.type]
  }

  val wen = Reg(init=Bits(0, maxLatency-1))
  val wbInfo = Reg(Vec(maxLatency-1, new WBInfo))
  val mem_wen = mem_reg_valid && (mem_ctrl.fma || mem_ctrl.fastpipe || mem_ctrl.fromint)
  val write_port_busy = RegEnable(mem_wen && (memLatencyMask & latencyMask(ex_ctrl, 1)).orR || (wen & latencyMask(ex_ctrl, 0)).orR, req_valid)

  for (i <- 0 until maxLatency-2) {
    when (wen(i+1)) { wbInfo(i) := wbInfo(i+1) }
  }
  wen := wen >> 1
  when (mem_wen) {
    when (!killm) {
      wen := wen >> 1 | memLatencyMask
    }
    for (i <- 0 until maxLatency-1) {
      when (!write_port_busy && memLatencyMask(i)) {
        wbInfo(i).cp := mem_cp_valid
        wbInfo(i).single := mem_ctrl.singleOut
        wbInfo(i).pipeid := pipeid(mem_ctrl)
        wbInfo(i).rd := mem_reg_inst(11,7)
      }
    }
  }

  val waddr = Mux(divSqrt_wen, divSqrt_waddr, wbInfo(0).rd)
  val wdouble = Mux(divSqrt_wen, !divSqrt_single, !wbInfo(0).single)
  val wdata = box(Mux(divSqrt_wen, divSqrt_wdata, (pipes.map(_.res.data): Seq[UInt])(wbInfo(0).pipeid)), wdouble)
  val wexc = (pipes.map(_.res.exc): Seq[UInt])(wbInfo(0).pipeid)
  when ((!wbInfo(0).cp && wen(0)) || divSqrt_wen) {
    regfile(waddr) := wdata
    if (enableCommitLog) {
      printf("f%d p%d 0x%x\n", waddr, waddr + 32, ieee(wdata))
    }
  }
  when (wbInfo(0).cp && wen(0)) {
    io.cp_resp.bits.data := wdata
    io.cp_resp.valid := Bool(true)
  }
  io.cp_req.ready := !ex_reg_valid

  val wb_toint_valid = wb_reg_valid && wb_ctrl.toint
  val wb_toint_exc = RegEnable(fpiu.io.out.bits.exc, mem_ctrl.toint)
  io.fcsr_flags.valid := wb_toint_valid || divSqrt_wen || wen(0)
  io.fcsr_flags.bits :=
    Mux(wb_toint_valid, wb_toint_exc, UInt(0)) |
    Mux(divSqrt_wen, divSqrt_flags, UInt(0)) |
    Mux(wen(0), wexc, UInt(0))

  val units_busy = mem_reg_valid && (mem_ctrl.div || mem_ctrl.sqrt) && (!divSqrt_inReady || wen.orR)
  io.fcsr_rdy := !(ex_reg_valid && ex_ctrl.wflags || mem_reg_valid && mem_ctrl.wflags || wb_reg_valid && wb_ctrl.toint || wen.orR || divSqrt_in_flight)
  io.nack_mem := units_busy || write_port_busy || divSqrt_in_flight
  io.dec <> fp_decoder.io.sigs
  def useScoreboard(f: ((Pipe, Int)) => Bool) = pipes.zipWithIndex.filter(_._1.lat > 3).map(x => f(x)).fold(Bool(false))(_||_)
  io.sboard_set := wb_reg_valid && !wb_cp_valid && Reg(next=useScoreboard(_._1.cond(mem_ctrl)) || mem_ctrl.div || mem_ctrl.sqrt)
  io.sboard_clr := !wb_cp_valid && (divSqrt_wen || (wen(0) && useScoreboard(x => wbInfo(0).pipeid === UInt(x._2))))
  io.sboard_clra := waddr
  // we don't currently support round-max-magnitude (rm=4)
  io.illegal_rm := io.inst(14,12).isOneOf(5, 6) || io.inst(14,12) === 7 && io.fcsr_rm >= 5

  divSqrt_wdata := 0
  divSqrt_flags := 0
  if (cfg.divSqrt) {
    require(fLen == 64)
    val divSqrt_rm = Reg(Bits())
    val divSqrt_flags_double = Reg(Bits())
    val divSqrt_wdata_double = Reg(Bits())

    val divSqrt = Module(new hardfloat.DivSqrtRecF64)
    divSqrt_inReady := Mux(divSqrt.io.sqrtOp, divSqrt.io.inReady_sqrt, divSqrt.io.inReady_div)
    val divSqrt_outValid = divSqrt.io.outValid_div || divSqrt.io.outValid_sqrt
    divSqrt.io.inValid := mem_reg_valid && (mem_ctrl.div || mem_ctrl.sqrt) && !divSqrt_in_flight
    divSqrt.io.sqrtOp := mem_ctrl.sqrt
    divSqrt.io.a := fpiu.io.as_double.in1
    divSqrt.io.b := fpiu.io.as_double.in2
    divSqrt.io.roundingMode := fpiu.io.as_double.rm

    when (divSqrt.io.inValid && divSqrt_inReady) {
      divSqrt_in_flight := true
      divSqrt_killed := killm
      divSqrt_single := mem_ctrl.singleOut
      divSqrt_waddr := mem_reg_inst(11,7)
      divSqrt_rm := divSqrt.io.roundingMode
    }

    when (divSqrt_outValid) {
      divSqrt_wen := !divSqrt_killed
      divSqrt_wdata_double := divSqrt.io.out
      divSqrt_in_flight := false
      divSqrt_flags_double := divSqrt.io.exceptionFlags
    }

    val divSqrt_toSingle = Module(new hardfloat.RecFNToRecFN(11, 53, 8, 24))
    divSqrt_toSingle.io.in := divSqrt_wdata_double
    divSqrt_toSingle.io.roundingMode := divSqrt_rm
    divSqrt_wdata := Mux(divSqrt_single, Cat(divSqrt_wdata_double >> divSqrt_toSingle.io.out.getWidth, divSqrt_toSingle.io.out), divSqrt_wdata_double)
    divSqrt_flags := divSqrt_flags_double | Mux(divSqrt_single, divSqrt_toSingle.io.exceptionFlags, Bits(0))
  } else {
    when (id_ctrl.div || id_ctrl.sqrt) { io.illegal_rm := true }
  }
}

/** Mix-ins for constructing tiles that may have an FPU external to the core pipeline */
trait CanHaveSharedFPU extends HasTileParameters

trait CanHaveSharedFPUModule {
  val outer: CanHaveSharedFPU
  val fpuOpt = outer.tileParams.core.fpu.map(params => Module(new FPU(params)(outer.p)))
  // TODO fpArb could go here instead of inside LegacyRoccComplex
}
