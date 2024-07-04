import play.sbt.routes.RoutesKeys.routes
import sbt.Keys.{compile, test}
import sbt.{AutoPlugin, Compile, Setting, Test}
import wartremover.WartRemover.autoImport.{wartremoverErrors, wartremoverExcluded, wartremoverWarnings}
import wartremover.{Wart, Warts}

object WartRemoverSettings extends AutoPlugin {

  override def trigger = allRequirements

  private val compileErrorOn: Seq[Wart] =
    Warts.allBut(
      Wart.Equals,
      Wart.ImplicitParameter,
      Wart.DefaultArguments,
      Wart.Throw,
      Wart.Nothing,
      Wart.ToString,
      Wart.StringPlusAny,
      Wart.FinalCaseClass,
      Wart.AsInstanceOf,
      Wart.Overloading,
      Wart.OptionPartial,
      Wart.PlatformDefault,
      Wart.MutableDataStructures,
      Wart.LeakingSealed,
      Wart.SeqApply,
      Wart.Var,
      Wart.Any,
      Wart.Null
    )

  private val compileWarnOn: Seq[Wart] =
    Warts.allBut(
      Wart.Equals,
      Wart.ToString,
      Wart.ImplicitParameter,
      Wart.DefaultArguments,
      Wart.Throw,
      Wart.Nothing,
      Wart.Overloading,
      Wart.FinalCaseClass,
      Wart.SeqApply
    )

  private val testErrorOn: Seq[Wart] =
    Warts.allBut(
      Wart.Equals,
      Wart.ToString,
      Wart.GlobalExecutionContext,
      Wart.JavaNetURLConstructors,
      Wart.ImplicitParameter,
      Wart.DefaultArguments,
      Wart.Nothing,
      Wart.Null,
      Wart.Overloading,
      Wart.FinalCaseClass,
      Wart.SeqApply,
      Wart.AsInstanceOf,
      Wart.IsInstanceOf,
      Wart.IterableOps,
      Wart.OptionPartial,
      Wart.Option2Iterable,
      Wart.PlatformDefault
    )

  private val testWarnOn: Seq[Wart] =
    Warts.allBut(
      Wart.Equals,
      Wart.ToString,
      Wart.GlobalExecutionContext,
      Wart.ImplicitParameter,
      Wart.DefaultArguments,
      Wart.Nothing,
      Wart.Overloading,
      Wart.FinalCaseClass,
      Wart.SeqApply,
      Wart.PlatformDefault
    )

  override lazy val projectSettings: Seq[Setting[?]] = Seq(
    wartremoverExcluded ++= (Compile / routes).value,
    // Compile main sources
    Compile / compile / wartremoverErrors ++= compileErrorOn,
    Compile / compile / wartremoverWarnings ++= compileWarnOn,
    // Test and it/Test sources
    Test / wartremoverErrors ++= testErrorOn,
    Test / wartremoverWarnings ++= testWarnOn
  )

}
