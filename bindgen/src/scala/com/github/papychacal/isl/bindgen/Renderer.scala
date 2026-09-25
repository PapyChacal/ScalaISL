package com.github.papychacal.isl.bindgen

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.mutable

final case class RenderedFile(name: String, contents: String)

final case class RenderingResult(files: Vector[RenderedFile], diagnostics: Vector[String])

object ScalaRenderer:
  private final case class Callback(name: String, cType: CType)

  def render(model: IslModel, projectRoot: Path): RenderingResult =
    val diagnostics = mutable.ArrayBuffer.empty[String]
    val callbacks = collectCallbacks(model)
    val callbackNames = callbacks.map(callback => callback.cType.canonical -> callback.name).toMap
    val rawFunctions = model.functions.flatMap { function =>
      rawSignature(function, callbackNames) match
        case Right(signature) => Some(function -> signature)
        case Left(reason) =>
          diagnostics += s"unsupported raw declaration ${function.signature}: $reason"
          None
    }
    val functionParts = rawFunctions.grouped(200).zipWithIndex.flatMap { (group, index) =>
      group.map(_._1.name -> index)
    }.toMap

    val unsafe = renderUnsafe(model, rawFunctions, callbacks, functionParts, projectRoot)
    val api = renderApi(model, rawFunctions.map(_._1), callbackNames, functionParts, diagnostics, projectRoot)
    RenderingResult(
      Vector(RenderedFile("unsafe.scala", unsafe), RenderedFile("api.scala", api)),
      diagnostics.toVector.sorted
    )

  def write(result: RenderingResult, output: Path): Unit =
    Files.createDirectories(output)
    result.files.foreach(file => Files.writeString(output.resolve(file.name), file.contents, StandardCharsets.UTF_8))
    Files.writeString(
      output.resolve("generation-report.txt"),
      if result.diagnostics.isEmpty then "All declarations were mapped.\n"
      else result.diagnostics.mkString("", "\n", "\n"),
      StandardCharsets.UTF_8
    )

  private def collectCallbacks(model: IslModel): Vector[Callback] =
    model.functions.iterator.flatMap(_.parameters.iterator.map(_.cType).filter(_.isCallback))
      .toVector.distinctBy(_.canonical).sortBy(_.canonical).zipWithIndex
      .map((cType, index) => Callback(s"Callback${index + 1}", cType))

  private def renderUnsafe(
      model: IslModel,
      functions: Vector[(FunctionDecl, String)],
      callbacks: Vector[Callback],
      functionParts: Map[String, Int],
      projectRoot: Path
  ): String =
    val callbackText = callbacks.map { callback =>
      val result = rawType(callback.cType.callbackResult.get, Map.empty).getOrElse("Unit")
      val params = callback.cType.callbackParameters.zipWithIndex.map { (parameter, index) =>
        s"arg${index + 1}: ${rawType(parameter, Map.empty).getOrElse("Pointer")}" 
      }.mkString(", ")
      s"""trait ${callback.name}:
         |  @Delegate def invoke($params): $result
         |""".stripMargin
    }.mkString("\n")
    val libraries = functions.groupBy((function, _) => functionParts(function.name)).toVector.sortBy(_._1).map { (part, declarations) =>
      val methods = declarations.map { (function, signature) =>
        val docs = documentation(function)
        s"$docs  $signature"
      }.mkString("\n\n")
      s"trait ISLLibrary$part:\n$methods"
    }.mkString("\n\n")
    val instances = functionParts.values.toSet.toVector.sorted.map { part =>
      s"  lazy val part$part: ISLLibrary$part = LibraryLoader.create(classOf[ISLLibrary$part]).load(NativeLibraryResource.path)"
    }.mkString("\n")
    s"""package com.github.papychacal.isl.unsafe
       |
       |import java.nio.file.{Files, StandardCopyOption}
       |import jnr.ffi.{LibraryLoader, Pointer}
       |import jnr.ffi.annotations.Delegate
       |
       |$callbackText
       |$libraries
       |
       |private object NativeLibraryResource:
       |  lazy val path: String =
       |    val stream = Option(getClass.getResourceAsStream("/libisl.so"))
       |      .getOrElse(throw UnsatisfiedLinkError("bundled native library /libisl.so was not found"))
       |    val file = Files.createTempFile("scalaisl-", "-libisl.so")
       |    try Files.copy(stream, file, StandardCopyOption.REPLACE_EXISTING)
       |    finally stream.close()
       |    file.toFile.deleteOnExit()
       |    file.toAbsolutePath.toString
       |
       |object ISLLibrary:
       |$instances
       |""".stripMargin

  private def rawSignature(function: FunctionDecl, callbackNames: Map[String, String]): Either[String, String] =
    for
      result <- rawType(function.result, callbackNames)
      parameters <- sequence(function.parameters.map { parameter =>
        rawType(parameter.cType, callbackNames).map(tpe => s"${scalaIdentifier(parameter.name)}: $tpe")
      })
    yield s"def ${scalaIdentifier(function.name)}(${parameters.mkString(", ")}): $result"

  private def rawType(cType: CType, callbackNames: Map[String, String]): Either[String, String] =
    if cType.isCallback then callbackNames.get(cType.canonical).toRight(s"unregistered callback ${cType.spelling}")
    else if cType.isPointer && cType.pointee.exists(t => normalize(t.canonical) == "char") then Right("String")
    else if cType.isPointer then Right("Pointer")
    else
      normalize(cType.canonical) match
        case "void" => Right("Unit")
        case "_Bool" | "bool" => Right("Boolean")
        case "float" => Right("Float")
        case "double" | "long double" => Right("Double")
        case value if value.startsWith("enum ") => Right("Int")
        case value if integer32(value) => Right("Int")
        case value if integer64(value) => Right("Long")
        case value => Left(s"unsupported by-value C type '$value'")

  private def renderApi(
      model: IslModel,
      functions: Vector[FunctionDecl],
      callbackNames: Map[String, String],
      functionParts: Map[String, Int],
      diagnostics: mutable.ArrayBuffer[String],
      projectRoot: Path
  ): String =
    val objectTypes = model.objectTypes.toVector.sorted
    val freeFunctions = functions.filter(f => f.name.endsWith("_free") && f.parameters.size == 1)
      .flatMap(f => Model.objectType(f.parameters.head.cType).map(_ -> f)).toMap
    val copyFunctions = functions.filter(f => f.name.endsWith("_copy") && f.parameters.size == 1)
      .flatMap(f => Model.objectType(f.parameters.head.cType).map(_ -> f)).toMap

    val specialEnums = renderResultConventions
    val enums = model.enums.filterNot(e => Set("isl_bool", "isl_stat").contains(e.name))
      .filter(_.values.nonEmpty).map(renderEnum).mkString("\n")

    val classes = objectTypes.map { objectType =>
      val className = scalaTypeName(objectType)
      val instanceCandidates = functions.filter(f => f.parameters.headOption.flatMap(p => Model.objectType(p.cType)).contains(objectType))
        .filterNot(f => f.name.endsWith("_free") || f.name.endsWith("_copy"))
      val instanceMethods = renderPublicMethods(instanceCandidates, Some(objectType), hasReceiver = true, model, callbackNames, functionParts, copyFunctions, diagnostics, projectRoot)
      val companionCandidates = functions.filter(f => Model.objectType(f.result).contains(objectType))
        .filterNot(f => f.parameters.headOption.flatMap(p => Model.objectType(p.cType)).contains(objectType))
      val companionMethods = renderPublicMethods(companionCandidates, Some(objectType), hasReceiver = false, model, callbackNames, functionParts, copyFunctions, diagnostics, projectRoot)
      val free = freeFunctions.get(objectType).map(f => s"pointer => ${nativeLibrary(f.name, functionParts)}.${scalaIdentifier(f.name)}(pointer)")
        .getOrElse("_ => ()")
      val copyMethod = copyFunctions.get(objectType).map { function =>
        s"""  /** Return an independently owned native copy. */
           |  def copy(): $className = $className.owned(${nativeLibrary(function.name, functionParts)}.${scalaIdentifier(function.name)}(handle.pointer))
           |""".stripMargin
      }.getOrElse("")
      s"""final class $className private[isl] (private[isl] val handle: NativeHandle):
         |  private[isl] def pointer: Pointer = handle.pointer
         |$copyMethod$instanceMethods
         |
         |object $className:
         |  private[isl] def owned(pointer: Pointer): $className = new $className(NativeHandle.owned(pointer, $free))
         |  private[isl] def borrowed(pointer: Pointer): $className = new $className(NativeHandle.borrowed(pointer))
         |$companionMethods
         |""".stripMargin
    }.mkString("\n")

    val objectFunctionNames = objectTypes.flatMap { objectType =>
      functions.filter(f => f.parameters.headOption.flatMap(p => Model.objectType(p.cType)).contains(objectType)).map(_.name) ++
        functions.filter(f => Model.objectType(f.result).contains(objectType)).map(_.name)
    }.toSet
    val globals = renderPublicMethods(functions.filterNot(f => objectFunctionNames.contains(f.name)), None, hasReceiver = false, model,
      callbackNames, functionParts, copyFunctions, diagnostics, projectRoot)

    s"""package com.github.papychacal.isl
       |
       |import java.lang.ref.Cleaner
       |import jnr.ffi.Pointer
       |import com.github.papychacal.isl.{unsafe => native}
       |
       |$specialEnums
       |$enums
       |private final class NativeState(pointer: Pointer, release: Pointer => Unit) extends Runnable:
       |  override def run(): Unit = release(pointer)
       |
       |private[isl] final class NativeHandle private (val pointer: Pointer)
       |
       |private[isl] object NativeHandle:
       |  private val cleaner = Cleaner.create()
       |  def owned(pointer: Pointer, release: Pointer => Unit): NativeHandle =
       |    val handle = NativeHandle(pointer)
       |    cleaner.register(handle, NativeState(pointer, release))
       |    handle
       |  def borrowed(pointer: Pointer): NativeHandle = NativeHandle(pointer)
       |
       |final class CallbackRegistration[+A] private[isl] (val value: A, private val retained: List[AnyRef])
       |
       |private[isl] object CallbackRegistration:
       |  def apply[A](value: A, retained: List[AnyRef]): CallbackRegistration[A] =
       |    new CallbackRegistration(value, retained)
       |
       |$classes
       |object ISL:
       |$globals
       |""".stripMargin

  private def renderPublicMethods(
      functions: Vector[FunctionDecl],
      owner: Option[String],
      hasReceiver: Boolean,
      model: IslModel,
      callbackNames: Map[String, String],
      functionParts: Map[String, Int],
      copyFunctions: Map[String, FunctionDecl],
      diagnostics: mutable.ArrayBuffer[String],
      projectRoot: Path
  ): String =
    val rendered = functions.flatMap { function =>
      renderPublicMethod(function, owner, hasReceiver, model, callbackNames, functionParts, copyFunctions, projectRoot) match
        case Right(method) => Some(method)
        case Left(reason) =>
          diagnostics += s"unsupported Scala API declaration ${function.signature}: $reason"
          None
    }
    // Type erasure can make distinct C signatures collide. Keep one binding and report every collision.
    rendered.groupBy(_._1).toVector.sortBy(_._1).map { (signature, choices) =>
      if choices.size > 1 then
        diagnostics += s"Scala signature collision '$signature': ${choices.map(_._2).mkString(", ")}"
      choices.head._3
    }.mkString

  private def renderPublicMethod(
      function: FunctionDecl,
      owner: Option[String],
      hasReceiver: Boolean,
      model: IslModel,
      callbackNames: Map[String, String],
      functionParts: Map[String, Int],
      copyFunctions: Map[String, FunctionDecl],
      projectRoot: Path
  ): Either[String, (String, String, String)] =
    if function.result.isCallback then
      return Left("returning a native callback requires an explicit lifetime policy")
    val dropped = if hasReceiver then 1 else 0
    val visibleParameters = function.parameters.drop(dropped)
    for
      resultType <- publicType(function.result, model, callbackNames, isResult = true)
      parameterTypes <- sequence(visibleParameters.map(p => publicType(p.cType, model, callbackNames, isResult = false)))
      arguments <- sequence(function.parameters.zipWithIndex.map { (parameter, index) =>
        if hasReceiver && index == 0 then
          parameter.ownership match
            case Some(Ownership.Take) =>
              Model.objectType(parameter.cType).flatMap(copyFunctions.get)
                .map(copy => s"${nativeLibrary(copy.name, functionParts)}.${scalaIdentifier(copy.name)}(handle.pointer)")
                .toRight(s"receiver is __isl_take but has no copy function")
            case _ => Right("handle.pointer")
        else renderArgument(function, parameter, scalaIdentifier(parameter.name), model, callbackNames, functionParts, copyFunctions)
      })
      persistent = persistentCallback(function)
      callbackBindings =
        if persistent then function.parameters.zip(arguments).zipWithIndex.collect {
          case ((parameter, expression), index) if parameter.cType.isCallback => (index, s"_callback${index + 1}", expression)
        }
        else Vector.empty
      callArguments = arguments.zipWithIndex.map { (argument, index) =>
        callbackBindings.find(_._1 == index).map(_._2).getOrElse(argument)
      }
      effectiveOwnership = function.resultOwnership.orElse(
        Option.when(function.name.endsWith("_alloc") || function.constructor)(Ownership.Give)
      )
      converted <- renderResult(function.result, effectiveOwnership, resultType,
        s"${nativeLibrary(function.name, functionParts)}.${scalaIdentifier(function.name)}(${callArguments.mkString(", ")})", model)
    yield
      val methodName =
        if !hasReceiver && owner.nonEmpty && isCompanionConstructor(function, owner.get) then "apply"
        else publicMethodName(function.name, owner)
      val parameters = visibleParameters.zip(parameterTypes).map { (parameter, tpe) => s"${scalaIdentifier(parameter.name)}: $tpe" }.mkString(", ")
      val finalResultType = if persistent then s"CallbackRegistration[$resultType]" else resultType
      val signature = s"$methodName(${parameterTypes.mkString(",")}):$finalResultType"
      val callbackDoc = Option.when(persistent)("The returned registration retains native callback objects while it remains reachable.").toSeq
      val docs = documentation(function, extra = callbackDoc)
      val method =
        if persistent then
          val bindings = callbackBindings.map((_, variable, expression) => s"    val $variable = $expression").mkString("\n")
          val retained = callbackBindings.map(_._2).mkString("List(", ", ", ")")
          s"$docs  def $methodName($parameters): $finalResultType =\n$bindings\n    CallbackRegistration($converted, $retained)\n"
        else s"$docs  def $methodName($parameters): $finalResultType = $converted\n"
      (signature, function.name, method)

  private def renderArgument(
      function: FunctionDecl,
      parameter: Parameter,
      name: String,
      model: IslModel,
      callbackNames: Map[String, String],
      functionParts: Map[String, Int],
      copyFunctions: Map[String, FunctionDecl]
  ): Either[String, String] =
    Model.objectType(parameter.cType) match
      case Some(objectType) =>
        parameter.ownership match
          case Some(Ownership.Take) => copyFunctions.get(objectType)
            .map(copy => s"${nativeLibrary(copy.name, functionParts)}.${scalaIdentifier(copy.name)}($name.handle.pointer)")
            .toRight(s"${parameter.name} is __isl_take but $objectType has no copy function")
          case _ => Right(s"$name.handle.pointer")
      case None if parameter.cType.isCallback =>
        val callbackName = callbackNames(parameter.cType.canonical)
        val argumentTypes = parameter.cType.callbackParameters.map(t => rawType(t, Map.empty).getOrElse("Pointer"))
        val taken = CallbackOwnershipRules.takenArguments.getOrElse((function.name, parameter.name), Set.empty)
        val ownedBindings = parameter.cType.callbackParameters.zipWithIndex.collect {
          case (cType, index) if taken.contains(index) && Model.objectType(cType).nonEmpty =>
            val variable = s"_ownedArg${index + 1}"
            (index, variable, s"val $variable = ${scalaTypeName(Model.objectType(cType).get)}.owned(arg${index + 1})")
        }
        val convertedArguments = parameter.cType.callbackParameters.zipWithIndex.map { (cType, index) =>
          ownedBindings.find(_._1 == index).map(_._2).getOrElse(callbackArgument(cType, s"arg${index + 1}"))
        }.mkString(", ")
        val invocation = s"$name($convertedArguments)"
        val convertedResult = callbackResult(parameter.cType.callbackResult.get, invocation)
        val body =
          if ownedBindings.isEmpty then convertedResult
          else
            val bindings = ownedBindings.map(_._3).mkString("; ")
            s"{ $bindings; $convertedResult }"
        Right(s"new native.$callbackName { def invoke(${argumentTypes.zipWithIndex.map((t, i) => s"arg${i + 1}: $t").mkString(", ")}): ${rawType(parameter.cType.callbackResult.get, Map.empty).getOrElse("Unit")} = $body }")
      case None =>
        normalize(parameter.cType.canonical) match
          case "isl_bool" => Right(s"$name.nativeValue")
          case "isl_stat" => Right(s"$name.nativeValue")
          case "isl_size" => Right(s"$name.raw")
          case value if value.startsWith("enum isl_") => Right(s"$name.nativeValue")
          case _ => Right(name)

  private def callbackArgument(cType: CType, name: String): String =
    Model.objectType(cType) match
      case Some(objectType) => s"${scalaTypeName(objectType)}.borrowed($name)"
      case None => normalize(cType.canonical) match
        case "isl_bool" => s"IslBool.fromNative($name)"
        case "isl_stat" => s"IslStat.fromNative($name)"
        case "isl_size" => s"IslSize($name)"
        case value if value.startsWith("enum isl_") =>
          s"${scalaTypeName(value.stripPrefix("enum "))}.fromNative($name).getOrElse(throw IllegalArgumentException(\"invalid native enum value\"))"
        case _ => name

  private def callbackResult(cType: CType, invocation: String): String =
    Model.objectType(cType) match
      case Some(_) => s"$invocation.map(_.handle.pointer).orNull"
      case None => normalize(cType.canonical) match
        case "isl_bool" | "isl_stat" => s"$invocation.nativeValue"
        case "isl_size" => s"$invocation.raw"
        case value if value.startsWith("enum isl_") => s"$invocation.map(_.nativeValue).getOrElse(-1)"
        case _ => invocation

  private def renderResult(cType: CType, ownership: Option[Ownership], publicName: String, call: String, model: IslModel): Either[String, String] =
    Model.objectType(cType) match
      case Some(objectType) =>
        val wrap = if ownership.contains(Ownership.Give) then "owned" else "borrowed"
        Right(s"Option($call).map(${scalaTypeName(objectType)}.$wrap)")
      case None =>
        normalize(cType.canonical) match
          case "void" => Right(call)
          case "isl_bool" => Right(s"IslBool.fromNative($call)")
          case "isl_stat" => Right(s"IslStat.fromNative($call)")
          case "isl_size" => Right(s"IslSize($call)")
          case value if value.startsWith("enum isl_") =>
            Right(s"${scalaTypeName(value.stripPrefix("enum "))}.fromNative($call)")
          case _ => Right(call)

  private def publicType(cType: CType, model: IslModel, callbackNames: Map[String, String], isResult: Boolean): Either[String, String] =
    Model.objectType(cType) match
      case Some(objectType) => Right((if isResult then "Option[" else "") + scalaTypeName(objectType) + (if isResult then "]" else ""))
      case None if cType.isCallback =>
        for
          result <- publicType(cType.callbackResult.get, model, callbackNames, isResult = true)
          parameters <- sequence(cType.callbackParameters.map(publicType(_, model, callbackNames, isResult = false)))
        yield s"(${parameters.mkString(", ")}) => $result"
      case None if cType.isPointer && cType.pointee.exists(t => normalize(t.canonical) == "char") => Right("String")
      case None if cType.isPointer => Right("Pointer")
      case None =>
        normalize(cType.canonical) match
          case "void" => Right("Unit")
          case "isl_bool" => Right("IslBool")
          case "isl_stat" => Right("IslStat")
          case "isl_size" => Right("IslSize")
          case value if value.startsWith("enum isl_") =>
            val enumName = scalaTypeName(value.stripPrefix("enum "))
            Right(if isResult then s"Option[$enumName]" else enumName)
          case "_Bool" | "bool" => Right("Boolean")
          case "float" => Right("Float")
          case "double" | "long double" => Right("Double")
          case value if integer32(value) => Right("Int")
          case value if integer64(value) => Right("Long")
          case value => Left(s"unsupported Scala type '$value'")

  private def renderResultConventions: String =
    """enum IslBool(val nativeValue: Int):
      |  case Error extends IslBool(-1)
      |  case False extends IslBool(0)
      |  case True extends IslBool(1)
      |
      |object IslBool:
      |  def fromNative(value: Int): IslBool = value match
      |    case -1 => Error
      |    case 0 => False
      |    case 1 => True
      |    case other => throw IllegalArgumentException(s"invalid isl_bool ABI value: $other")
      |
      |enum IslStat(val nativeValue: Int):
      |  case Error extends IslStat(-1)
      |  case Ok extends IslStat(0)
      |
      |object IslStat:
      |  def fromNative(value: Int): IslStat = value match
      |    case -1 => Error
      |    case 0 => Ok
      |    case other => throw IllegalArgumentException(s"invalid isl_stat ABI value: $other")
      |
      |opaque type IslSize = Long
      |
      |object IslSize:
      |  val Error: IslSize = -1L
      |  def apply(raw: Long): IslSize = raw
      |  extension (size: IslSize)
      |    def raw: Long = size
      |    def isError: Boolean = size == Error
      |    def toLongOption: Option[Long] = Option.unless(isError)(size)
      |""".stripMargin

  private def renderEnum(enumDecl: EnumDecl): String =
    val typeName = scalaTypeName(enumDecl.name)
    val unique = enumDecl.values.distinctBy(_.name)
    val cases = unique.map { value =>
      val prefix = enumDecl.name + "_"
      val short = value.name.stripPrefix(prefix)
      s"  case ${enumCaseName(short)} extends $typeName(${value.value})"
    }.mkString("\n")
    val lookups = unique.distinctBy(_.value).map { value =>
      val prefix = enumDecl.name + "_"
      s"    case ${value.value} => Some(${enumCaseName(value.name.stripPrefix(prefix))})"
    }.mkString("\n")
    s"""enum $typeName(val nativeValue: Int):
       |$cases
       |
       |object $typeName:
       |  def fromNative(value: Int): Option[$typeName] = value match
       |$lookups
       |    case _ => _root_.scala.None
       |""".stripMargin

  private def documentation(function: FunctionDecl, extra: Seq[String] = Seq.empty): String =
    ScaladocFormatter.format(
      function.comment.toSeq ++ extra ++ Seq(s"Native symbol: `${function.name}`."),
      "  "
    )

  private def persistentCallback(function: FunctionDecl): Boolean =
    function.parameters.exists(_.cType.isCallback) &&
      (function.name.startsWith("isl_ast_build_set_") ||
        function.name == "isl_access_info_set_restrict" ||
        function.name == "isl_access_info_alloc")

  private def nativeLibrary(functionName: String, functionParts: Map[String, Int]): String =
    s"native.ISLLibrary.part${functionParts(functionName)}"

  private object CallbackOwnershipRules:
    // libclang's function-pointer type erases annotations on nested parameters.
    // Keep these source-reviewed exceptions explicit until the model reads tokens for nested declarators.
    val takenArguments: Map[(String, String), Set[Int]] = Map(
      ("isl_set_foreach_point", "fn") -> Set(0),
      ("isl_union_set_foreach_point", "fn") -> Set(0)
    )

  private def publicMethodName(cName: String, receiver: Option[String]): String =
    val prefix = receiver.map(_ + "_").getOrElse("isl_")
    scalaIdentifier(lowerCamel(cName.stripPrefix(prefix)))

  private def isCompanionConstructor(function: FunctionDecl, owner: String): Boolean =
    function.constructor || function.name == s"${owner}_alloc" || function.name == s"${owner}_read_from_str"

  private def scalaTypeName(cName: String): String =
    cName.stripPrefix("enum ").stripPrefix("isl_").split('_').filter(_.nonEmpty)
      .map(part => part.head.toUpper + part.tail).mkString match
        case "Bool" => "IslBool"
        case "Stat" => "IslStat"
        case "Size" => "IslSize"
        case other => other

  private def enumCaseName(name: String): String =
    val candidate = name.split('_').filter(_.nonEmpty).map(part => part.head.toUpper + part.tail.toLowerCase).mkString
    if candidate.headOption.exists(_.isDigit) then s"Value$candidate" else candidate

  private def lowerCamel(value: String): String =
    val parts = value.split('_').filter(_.nonEmpty)
    if parts.isEmpty then "apply" else parts.head + parts.drop(1).map(p => p.head.toUpper + p.tail).mkString

  private val reserved = Set("type", "val", "var", "def", "object", "class", "trait", "enum", "given", "using", "match", "case", "then", "else", "if", "for", "while", "do", "try", "catch", "finally", "throw", "return", "new", "this", "super", "extends", "with", "export", "import", "package", "private", "protected", "override", "abstract", "final", "sealed", "implicit", "inline", "opaque", "end", "derives")
  private def scalaIdentifier(value: String): String =
    if reserved.contains(value) || value.headOption.exists(_.isDigit) then s"`$value`" else value

  private def normalize(value: String): String =
    value.replace("const ", "").replace("volatile ", "").replace("restrict ", "").trim

  private def integer32(value: String): Boolean =
    Set("char", "signed char", "unsigned char", "short", "short int", "unsigned short", "unsigned short int",
      "int", "signed int", "unsigned int", "int32_t", "uint32_t", "isl_bool", "isl_stat").contains(value)

  private def integer64(value: String): Boolean =
    Set("long", "long int", "unsigned long", "unsigned long int", "long long", "long long int",
      "unsigned long long", "unsigned long long int", "int64_t", "uint64_t", "size_t", "ssize_t", "isl_size").contains(value)

  private def sequence[A](values: Vector[Either[String, A]]): Either[String, Vector[A]] =
    values.foldLeft[Either[String, Vector[A]]](Right(Vector.empty)) { (acc, value) =>
      for existing <- acc; next <- value yield existing :+ next
    }
