import scala.deriving._
import scala.quoted._
import scala.quoted.matching._
import scala.compiletime.{erasedValue, summonFrom, constValue}

object Inspect {
  inline def inspect[T]: String = ${ labelImpl[T] }
  def labelImpl[T: Type](given qctx: QuoteContext): Expr[String] = {
    InspectMacro.apply[T]
    Expr("")
  }
}

class Inspector(shift: Int)(given qctx: QuoteContext) {
  import qctx.tasty.{Type => TType, given, _}

  def logStart(s: String) = println(" " * shift + s)
  def log(s: String) = println(" " * shift + " -> " + s)
  private def next(): Inspector = new Inspector(shift + 1)

  private def inspectToB(tpe: TypeOrBounds): Unit = {
    tpe match {
      case t: TypeBounds =>
        ???
      case t: TType =>
        inspectTType(t)
    }
  }

  private def inspectTType(tpe2: TType): Unit = {
    tpe2 match {
      case a: AppliedType =>
        log(s"APPLIED: ${a.tycon};; ${a.args}")
        next().inspectSymbol(a.tycon.typeSymbol.asInstanceOf)
        a.args.map {
          a =>
            next().inspectToB(a.asInstanceOf)
        }
      case l: TypeLambda =>
        log(s"LAMBDA: ${l.paramNames} ${l.resType}")
        next().inspectTType(l.resType.asInstanceOf)
      case t: ParamRef =>
        log(s"PARAM REF: $t ")
      case r: TypeRef =>
        //log(s"TYPEREF: ${r.name} ${r.translucentSuperType}")
        log(s"TYPEREF: ${r.name} ${r.typeSymbol}")
        next().inspectSymbol(r.typeSymbol.asInstanceOf)
      case o =>
        log(s"TTYPE, UNSUPPORTED: $o")
    }
  }

  private def inspectTree(uns: TypeTree): Unit = {
      val symbol = uns.symbol
      val tpe2 = uns.tpe
      logStart(s"INSPECT: $uns: ${uns.getClass}")

      // println(s"TPE: ${tpe}: ${tpe.getClass}\n  * UNSEALED: ${uns}: ${uns.getClass}")
      // println(s"SYMBOL: ${symbol}: ${symbol.getClass}")
      // println(s"SYMBOL: ${symbol.show}")
      // println(s"TREETPE: ${tpe2}: ${tpe2.getClass}")
      // println(s"TREETPE: ${tpe2.show}")

      if (symbol.isNoSymbol) {
        inspectTType(tpe2)
      } else {
        inspectSymbol(symbol)
      }
  }
  private def inspectSymbol(symbol: Symbol) = {
    log(s"SYMTPE: ${symbol}; ${symbol.flags.is(Flags.Covariant)}")
    symbol.tree match {
      case c: ClassDef =>
        log(s"CLASSDEF, parents: ${c.parents}")
        c.parents.map {
          a =>
            next().inspectTree(a.asInstanceOf)
        }
      case t: TypeDef =>
        log(s"TYPEDEF: $t")
        next().inspectTree(t.rhs.asInstanceOf)
      case o =>
        log(s"SYMBOL, UNSUPPORTED: $o")
    }
  }

  def inspect[T](tpe: Type[T]): Unit = {
      val uns = tpe.unseal
      inspectTree(uns)
      println("--------")
  }

  private def tpeAttrs[T](uns: TypeTree): Unit = {
      val symbol = uns.symbol

      println(s"Attrs[$uns]: type=${symbol.isType}, term=${symbol.isTerm}, packageDef=${symbol.isPackageDef}, classDef=${symbol.isClassDef}, typeDef=${symbol.isValDef}, defdef=${symbol.isDefDef}, bind=${symbol.isBind}, nosymbol=${symbol.isNoSymbol}")
  }
}

object InspectMacro {
  def apply[T](given qctx: QuoteContext, tpe: Type[T]): Unit = new Inspector(0).inspect(tpe)
}
