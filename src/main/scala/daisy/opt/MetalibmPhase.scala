package daisy
package opt

import java.io._
import scala.concurrent._
import ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.collection.immutable.Seq

import lang.Trees._
import lang.Identifiers._
import tools.FinitePrecision._
import tools._
import lang.Trees.RealLiteral.{zero, one, two}

/*
  This Phase approximates elementary function calls using the Metalibm tool.
 */
object MetalibmPhase extends DaisyPhase with tools.RoundoffEvaluators with tools.Taylor {

  override val name = "Metalibm"
  override val description = "generates elementary functions from metalibm tool"
  override val definedOptions: Set[CmdLineOption[Any]] = Set(
    NumOption("timeout", 120, "Hand over to Daisy when timout is outdated"),
    NumOption("degree", 7, "The maximum degree for polynomials"),
    NumOption("minWidth", 4096, "The minimum width of subdomains: (sup(dom) - inf(dom)) * 1/minWidth"),
    NumOption("tableIndexWidth", 8, "The bitwidth of table indices"),
    NumOption("minimalReductionRatio", 1000, "The minimal range reduction ratio"),
    NumOption("metaSplitMinWidth", 128, "The minimum width of meta-subdomains: (sup(dom) - inf(dom)) * 1/metaSplitMinWidth"),
    NumOption("performExpressionDecomposition", 0, "The maximum level of expression decomposition"),
    StringChoiceOption("adaptWorkingPrecision", Set("true", "false"), "false", "The maximum level of expression decomposition"),
    NumOption("maxDegreeReconstruction", 5, "The maximum degree of a reconstruction polynomial"),
    NumOption("alpha", 8, "Factor of target error"),
    StringOption("extraError", "Additional absolute error which can be tolerated overall (notation e.g. 1e-14)."),
    StringChoiceOption("errorDist", Set("deriv", "equal"), "equal", "How to dsitribute the error budget.")
  )

  override implicit val debugSection = DebugSectionOptimization

  var reporter: Reporter = null
  var timeOut: Int = 0
  val quote = "\""   // there is a bug with Scala's string interpolation
  val dollar = "$"

  val metalibmPath = "metalibm-for-daisy"

  override def runPhase(ctx: Context, prg: Program): (Context, Program) = {
    reporter = ctx.reporter
    timeOut      =  ctx.option[Long]("timeout").toInt
    val precision   =  ctx.option[Precision]("precision")
    val alpha = ctx.option[Long]("alpha").toInt
    val extraError = Rational.fromString(ctx.option[Option[String]]("extraError").getOrElse("-1"))
    val errorDist = ctx.option[String]("errorDist")

    //val targetError  =  precision match { case f @ FloatPrecision(_) => f.machineEpsilon }
    val minWidthToString = "(sup(dom) - inf(dom)) * 1/" + ctx.option[Long]("minWidth").toString
    val metaSplitMinWidthToString = "(sup(dom) - inf(dom)) * 1/" + ctx.option[Long]("metaSplitMinWidth").toString

    // parameters for calling metalibm
    val params = Map("maxDegree"                      ->  ctx.option[Long]("degree").toString,
                    "minWidth"                        ->  minWidthToString,
                    "tableIndexWidth"                 ->  ctx.option[Long]("tableIndexWidth").toString,
                    "minimalReductionRatio"           ->  ctx.option[Long]("minimalReductionRatio").toString,
                    "metaSplitMinWidth"               ->  metaSplitMinWidthToString,
                    "performExpressionDecomposition"  ->  ctx.option[Long]("performExpressionDecomposition").toString,
                    "maxDegreeReconstruction"         ->  ctx.option[Long]("maxDegreeReconstruction").toString,
                    "adaptWorkingPrecision"           ->  ctx.option[String]("adaptWorkingPrecision"),
                    "target"                          -> s"$alpha * 2^(-53 + 1)",   //* sup(abs(f(dom)))
                    "daisyFormat"                     ->  s"$quote$precision$quote"
                   )

    var approxs: Seq[(String, String, String)] = Seq() // approximations generated by Metalibm

    val newProgram = {
      val newDefs = transformConsideredFunctions(ctx,prg){ fnc =>

        val paramsFile = new PrintWriter(new File(s"metalibmParams_${fnc.id}.txt"))
        paramsFile.write(params.map(x => s"${x._1} = ${x._2};").mkString("\n"))
        paramsFile.close()

        val intermRange     =  ctx.intermediateRanges(fnc.id)
        val intermAbsError  =  ctx.intermediateAbsErrors(fnc.id)
	      reporter.info(s"\n*** ${fnc.id} ***\n")

        // get all variables corresponding to elementary functions
        val elemIds = getElementaryVariables(fnc.body.get)
        val inputRanges = lang.TreeOps.allVariablesOf(fnc.body.get).map({
          id => (id -> intermRange(Variable(id), emptyPath))
        }).toMap

        // compute derivatives wrt. those
        val elemFactors: Map[Identifier, Rational] = elemIds.map({ id =>
          // computes derivative of the returned expression
          val deriv = easySimplify(getDerivative(fnc.body.get, id))
          val bound = evalRange[Interval](deriv, inputRanges, Interval.apply)._1
          (id, Interval.maxAbs(bound))
        }).toMap

        // bias the weights
        // val biasedElemFactors = elemFactors.toList.sortBy(_._2).zipWithIndex.map({
        //   case (elem, index) => (elem._1 -> (elem._2 * (index + 1)))
        // }).toMap
        // println("biasedElemFactors: " + biasedElemFactors)

        //normalize
        val sum: Rational = elemFactors.values.fold(Rational.zero)({case (acc, elem) => acc + elem})
        val normElemFactors = elemFactors.mapValues(k => k / sum)

        // val sum: Rational = biasedElemFactors.values.fold(Rational.zero)({case (acc, elem) => acc + elem})
        // val normElemFactors = biasedElemFactors.mapValues(k => k / sum)
        // println("biased normElemFactors: " + normElemFactors)


        val approxError: Rational = if (extraError != -1) {
          extraError
        } else {
          // target specified error - already committed roundoff error
          ctx.specResultErrorBounds(fnc.id) - ctx.resultAbsoluteErrors(fnc.id)
        }

        val (newBody, _approx): (Expr, Seq[(String, String, String)]) = insertApproxNode(fnc.body.get,
          ctx.originalFunctions(fnc.id).body.get, intermRange, intermAbsError,
          params, normElemFactors.toMap, approxError, errorDist)
        approxs = approxs ++ _approx

        // sanity check so we don't benchmark mathh and think it's Metalibm
        if (containsElemFnc(fnc.body.get) && _approx.size == 0) {
          reporter.fatalError("No approximation was generated from Metalibm! Aborting.")
        }

        fnc.copy(body = Some(newBody))
      }


      Program(prg.id, newDefs)
    }

    val wrappers: Seq[String] = generateWrappers(approxs, precision)

    (ctx.copy(metalibmWrapperFunctions=wrappers, metalibmGeneratedFiles=approxs.map(_._1)),
      newProgram)
  }

  def getElementaryVariables(e: Expr): Seq[Identifier] = e match {
    case Let(id, value, body) if (containsElemFnc(value)) =>
      Seq(id) ++ getElementaryVariables(body)

    // must be elementary function
    case Let(id, _, body) =>
      getElementaryVariables(body)

    case _ => Seq()
  }

  /**
   * Replaces elementary function nodes by approximations.
   * @return (approximated expression, Seq(fileName, fncName, signature))
   */
  def insertApproxNode(expr: Expr, original: Expr, intermRange: Map[(Expr, PathCond), Interval],
    intermAbsError: Map[(Expr, PathCond), Rational], params: Map[String, String],
    elemFactors: Map[Identifier, Rational], extraError: Rational, errorDist: String): (Expr, Seq[(String, String, String)]) = {

     def approx(e: Expr): (Expr, Seq[(String, String, String)]) = (e: @unchecked) match {

      case x @ Let(binder, value, body) if (containsElemFnc(value) && isUnary(value)) =>
        val inputVariable = Variable(lang.TreeOps.allVariablesOf(value).head)
        val domain = intermRange(inputVariable, emptyPath) +/- intermAbsError(inputVariable, emptyPath)

        reporter.info(s"Try to approximate $value in $domain:")
        val fncToApprox = value.toString // the expression that we want to approximate

        var paramsUpdated = params.+(
          "dom" -> domain.toString,
          "f"   -> fncToApprox)

        if (extraError > 0) {
          val totalError = if (errorDist == "deriv") {
            extraError * elemFactors(binder)
          } else { // equal error distribution
            extraError / elemFactors.size
          }

          // compute new local error from total error
          val inlinedValue = replaceForDerivative(expr, value)
          val bodyForDeriv = lang.TreeOps.replace(Map(inlinedValue -> Variable(binder)))(inline(original))
          val deriv = easySimplify(getDerivative(bodyForDeriv, binder))
          val inputRanges = lang.TreeOps.allVariablesOf(bodyForDeriv).map({
            id => (id -> intermRange(Variable(id), emptyPath))
          }).toMap
          //val bound = evalRange[Interval](deriv, inputRanges, Interval.apply)._1
          val bound = evalRange[SMTRange](deriv,
            inputRanges.map({ case (id, int) => (id -> SMTRange(Variable(id), int, BooleanLiteral(true))) }),
            SMTRange.apply(_, BooleanLiteral(true)))._1.toInterval
          val maxDeriv = Interval.maxAbs(bound)

          val localError = totalError / maxDeriv

          // transform into relative error
          val range = intermRange(value, emptyPath)
          val newAlpha = localError / Interval.maxAbs(range)
          println(s"total: $totalError, localError: $localError")

          paramsUpdated = paramsUpdated + ("target" -> newAlpha.toString)
        }

        //val (newValue, approxs) = generateApproxFromMetalibm(paramsUpdated) match {
        val (newValue, approxs) = searchApproxFromMetalibm(paramsUpdated) match {
          case None => (value, Seq())
          case Some((functionName, fileName, implErr, errorMultiplier, signature, timing)) =>
             reporter.info(s"DONE\n")

             val approxNode = Approx(value, inputVariable, implErr, errorMultiplier, functionName,
              signature == "D_TO_DD")
             (approxNode, Seq((fileName, functionName, signature)))
        }

        val (rewrittenBody, approxsBody) = approx(body)
        (Let(binder, newValue, rewrittenBody), approxs ++ approxsBody)

      case x @ Let(binder, value, body)  =>
        //val (rewrittenValue, approxsV) = approx(value)
        val (rewrittenBody, approxsB)  = approx(body)
        (Let(binder, value, rewrittenBody), approxsB)

      case _ => (e, Seq())
     }

     approx(expr)
  }

  def containsElemFnc(e: Expr): Boolean = {
    lang.TreeOps.exists {
      case Sin(_) | Cos(_) | Tan(_) | Exp(_) | Log(_) | Sqrt(_) |
        Atan(_) | Asin(_) | Acos(_) => true
    }(e)
  }

  def isUnary(e: Expr): Boolean = {
    lang.TreeOps.allVariablesOf(e).size == 1
  }

  def replaceForDerivative(expr: Expr, value: Expr): Expr = expr match {
    case Let(id, v, b) if (v == value) => value

    case Let(id, v, b) =>
      val replaced = replaceForDerivative(b, value)
      lang.TreeOps.replace(Map(Variable(id) -> v))(replaced)

    case _ => value  // nothing to replace
  }

  def inline(e: Expr): Expr = e match {
    case Let(id, v, b) =>
      val tmp = inline(b)
      lang.TreeOps.replace(Map(Variable(id) -> v))(tmp)

    case _ => e
  }

  def getSimpleName(expr: Expr): String = expr match {
     case Sqrt(t) => "sqrt"
     case Sin(t)  => "sin"
     case Cos(t)  => "cos"
     case Tan(t)  => "tan"
     case Exp(t)  => "exp"
     case Log(t)  => "log"
     case _       => throw new Exception("Unknown elementary function")
  }

  // Performs a search over the polynomial degree, trying to find the optimal one
  def searchApproxFromMetalibm(params: Map[String, String]): Option[(String, String, Rational, Rational, String, Rational)] = {

    val (noiseTolerance, degrees) =
      if (params("f").contains("sin") || params("f").contains("cos")) {
        (1.0, Seq(12, 16, 20, 24))
        //(1.0, Seq(12))
      } else {
        (0.05, Seq(4, 8, 12, 16))
        //(0.05, Seq(4))
      }

    var currDegree = degrees.head
    var currTiming = Rational.zero
    var currResult: Option[(String, String, Rational, Rational, String, Rational)] = None

    // start from lowest degree
    val paramsUpdated = params + ("maxDegree" -> currDegree.toString)

    generateApproxFromMetalibm(paramsUpdated) match {
      case x @ Some((_, _, _, _, _, time)) =>
        currTiming = time
        currResult = x
        reporter.info(s"generated approximation for degree: $currDegree, timing: $currTiming")
      case None =>
        reporter.info(s"approximation for degree: $currDegree timed out")
        currDegree = degrees.last
    }

    var bestDegree = currDegree
    // go up in steps of 4, until the time becomes larger, then stop, return the last seen
    while (currDegree < degrees.last) {
      currDegree = currDegree + 4

      val paramsUpdated = params + ("maxDegree" -> currDegree.toString)

      generateApproxFromMetalibm(paramsUpdated) match {
        case x @ Some((_, _, _, _, _, time)) if (time < currTiming) =>
          currTiming = time
          currResult = x
          bestDegree = currDegree
          reporter.info(s"generated approximation for degree: $currDegree, timing: $currTiming")

        case x @ Some((_, _, _, _, _, time)) if (time < currTiming * (1.0 + noiseTolerance)) =>
          reporter.info(s"generated approximation for degree: $currDegree, timing: $currTiming (not best, but continue)")

        case x @ Some((_, _, _, _, _, time)) =>
          reporter.info(s"generated approximation for degree: $currDegree, timing: $time (time higher)")
          currDegree = degrees.last
        case _ =>
          currDegree = degrees.last
      }
    }
    reporter.info(s"best approximation found for degree: $bestDegree, timing: $currTiming")
    currResult
  }

  /**
   * Calls Metalibm in order to generate an approximation.
   * @param params Metalibm parameters
   * @return (functionName, implementationFile, implementationError, errorMultiplier, generatedSignature, timing)
   */
  def generateApproxFromMetalibm(params: Map[String, String]): Option[(String, String, Rational, Rational, String, Rational)] = {

    /* Write the problem definition */
    val timestamp: Long = System.currentTimeMillis / 1000
    val problemDefName = s"problemdefForDaisy_$timestamp.sollya"
    val problemdef = new PrintWriter(new File(metalibmPath + "/" + problemDefName))
    problemdef.write(params.map({case (k, v) => s"$k = $v"}).mkString("", ";\n", ";"))
    problemdef.close()

    /* Run the problemdef results and return None if a time out occurs*/
    var lastline: String    = ""

    val f: Future[Unit] = Future {
      val problemDefRes = Runtime.getRuntime().exec(s"./metalibm.sollya $problemDefName",
        null, new File(metalibmPath))  //run in metalibm4daisy directory

      /* Read the problemDefRes */
      val stdInput =  new BufferedReader(new InputStreamReader(problemDefRes.getInputStream))
      var readResLine: String = ""
      readResLine = stdInput.readLine()

      /* Recover the last line where all the information we need is */
      while(readResLine != null){
        lastline = readResLine.toString
        readResLine = stdInput.readLine()
      }

      problemDefRes.getInputStream().close()
    }

    try {
      Await.result(f, timeOut.second)

    } catch {
      case e: java.util.concurrent.TimeoutException =>
        reporter.warning(s"Metalibm has timed out after $timeOut seconds.");
        return None
      case e: java.io.IOException =>
        reporter.warning(e.getMessage())
        return None
      case e: Exception =>
         reporter.warning("Something went wrong. Here's the stack trace:");
         e.printStackTrace
         return None
    }

    /* Extract information from the last line */
    val output = lastline.replaceAll("\\s+", "").split(Array('|','='))

    /* output(1) is true when everything went fine */
    if (output.length > 2 && output(1) == "true") {
      val functionName = output(3)
      val implErr = output(5)
      val implementationFile = output(7)
      val errorMultiplier = output(9)
      val signature = output(11)
      val timing = output(13)
      println(s"target error: ${params("target")}, error returned: ${Rational.fromString(implErr)}")

      Some((functionName, implementationFile, Rational.fromString(implErr),
        Rational.fromString(errorMultiplier), signature, Rational.fromString(timing)))
     } else {
      reporter.warning("Metalibm couldn't compute the approximation.")
      None
     }
  }


  def generateWrappers(generatedFunctions: Seq[(String, String, String)], precision: Precision): Seq[String] = {
    //val precString = precision match {
    //  case FloatPrecision(32) => "float"
    //  case FloatPrecision(64) => "double"
    //}

    val prototypes = generatedFunctions.map({ case (_, fncName, signature) => signature match {
      case "D_TO_D" =>
        //s"${precString} $fncName(double *, double);"
        s"static inline void $fncName(double *, double);"
      case "D_TO_DD" =>
        //s"${precString} $fncName(double *, double *, double);"
        s"static inline void $fncName(double *, double *, double);"
      }
    }) :+ "\n"

    prototypes
  }

  /**
   * Computes partial derivative w.r.t. passed parameter
   * @param e expression for which derivative is computed
   * @param wrt Delta id w.r.t. which derivative is computed
   * @return expression
   */
  private def getDerivative(e: Expr, wrt: Identifier): Expr = e match {
    case x @ Variable(id) if wrt.equals(id) => one
    case x @ Variable(id) => zero
    case x @ RealLiteral(r) => zero
    case x @ UMinus(in) => UMinus(getDerivative(in, wrt))

    case z @ Plus(x, y) =>
      Plus(getDerivative(x, wrt), getDerivative(y, wrt))

    case z @ Minus(x, y) =>
      Minus(getDerivative(x, wrt), getDerivative(y, wrt))

    case z @ Times(x, y) if containsVariables(x, wrt) && containsVariables(y, wrt) =>
      Plus(Times(x, getDerivative(y, wrt)), Times(getDerivative(x, wrt), y))

    case z @ Times(x, y) if containsVariables(x, wrt) =>
      // y is constant
      Times(getDerivative(x, wrt), y)

    case z @ Times(x, y) if containsVariables(y, wrt) =>
      // x is constant
      Times(x, getDerivative(y, wrt))

    case z @ Times(x, y) =>
      // x, y are both constants
      zero

    case z @ Division(x, y) if containsVariables(x, wrt) && containsVariables(y, wrt) =>
      Division(Minus(Times(getDerivative(x, wrt), y), Times(getDerivative(y, wrt), x)),
        Times(y, y))

    case z @ Division(x, y) if containsVariables(x, wrt) =>
      // y is constant
      Times(Division(one, y), getDerivative(x, wrt))

    case z @ Division(x, y) if containsVariables(y, wrt) =>
      // x is constant
      // (1/y)' = -y' / y^2
      Times(x, Division(UMinus(getDerivative(y, wrt)), Times(y, y)))

    case z @ Division(x, y) => zero

    case z @ IntPow(x, n) if containsVariables(x, wrt) =>
      assert(n > 1)
      // assert(n.isValidInt)
      if (n == 2) {
        getDerivative(x, wrt)
      } else {
        Times(RealLiteral(Rational(n)),
          IntPow(getDerivative(x, wrt), n-1))
      }

    // case z @ IntPow(x, n) => zero

    case z @ Sqrt(x) if containsVariables(x, wrt) =>
      Division(getDerivative(x, wrt), Times(two, Sqrt(x)))
    case z @ Sqrt(x) => zero

    case z @ Sin(x) if containsVariables(x, wrt) =>
      Times(getDerivative(x, wrt), Cos(x))
    case z @ Sin(x) => zero

    case z @ Cos(x) if containsVariables(x, wrt) =>
      Times(getDerivative(x, wrt), UMinus(Sin(x)))
    case z @ Cos(x) => zero

    case z @ Tan(x) if containsVariables(x, wrt) =>
      Times(getDerivative(x, wrt), Plus(one, Times(Tan(x), Tan(x))))
    case z @ Tan(x) => zero

    case z @ Exp(x) if containsVariables(x, wrt) =>
      Times(getDerivative(x, wrt), Exp(x))
    case z @ Exp(x) => zero

    case z @ Log(x) if containsVariables(x, wrt) =>
      Division(getDerivative(x, wrt), x)
    case z @ Log(x) => zero

    case z @ Atan(x) if containsVariables(x, wrt) =>
      Division(getDerivative(x, wrt), Plus(one, Times(x, x)))
    case z @ Atan(x) => zero

    case z @ Asin(x) if containsVariables(x, wrt) =>
      Division(getDerivative(x, wrt), Sqrt(Minus(one, Times(x, x))))
    case z @ Asin(x) => zero

    case z @ Acos(x) if containsVariables(x, wrt) =>
      UMinus(Division(getDerivative(x, wrt), Sqrt(Minus(one, Times(x, x)))))
    case z @ Acos(x) => zero

    case z @ Let(x, value, body) if containsVariables(body, wrt) =>
      getDerivative(body, wrt)

    case z @ Let(x, value, body) => zero

    case z => throw new IllegalArgumentException(s"Unknown expression $z. Computing derivative failed")
  }

  private def containsVariables(e: Expr, wrt: Identifier): Boolean = lang.TreeOps.exists{
    case Variable(`wrt`) => true
  }(e)
}
