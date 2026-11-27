package fi.aalto.cs.aplus.courseconfig.scala

import fi.aalto.cs.aplus.courseconfig.CourseConfig as Wire

import _root_.scala.jdk.CollectionConverters.*

/** Factory argument accepting a value, Some(value), or None. Empty strings remain present. */
type Optional[A] = A | Option[A]

private def nullable[A <: AnyRef](value: Optional[A]): A = value match
  case option: Option[?] => option.orNull.asInstanceOf[A]
  case present => present.asInstanceOf[A]

/** Shared model types, also usable in Scala type annotations. */
type Document = Wire.Document
type Module = Wire.Module
type Resources = Wire.Resources
type RequiredPlugin = Wire.RequiredPlugin
type ScalaRepl = Wire.ScalaRepl
type Callbacks = Wire.Callbacks

/** Factories return the shared Kotlin manifest types. */
object Document:
  /** Validates the completed manifest before returning it.
   *
   * @param id                    A+ course ID
   * @param modules               downloadable modules in display order. Student-created modules need no entry.
   * @param exerciseModules       exercise API IDs mapped to language codes and module names,
   *                              including student-created modules
   * @param requiredPluginVersion required plugin version, serialized as `version`
   * @param autoInstall           names of downloadable modules to install during setup
   */
  def apply(
             id: String,
             name: String,
             modules: Seq[Module] = Seq.empty,
             exerciseModules: Map[Long, Map[String, String]] = Map.empty,
             aPlusUrl: Optional[String] = None,
             languages: Seq[String] = Seq.empty,
             requiredPluginVersion: String = "1.0",
             resources: Optional[Resources] = None,
             requiredPlugins: Seq[RequiredPlugin] = Seq.empty,
             vmOptions: Map[String, String] = Map.empty,
             optionalCategories: Seq[String] = Seq.empty,
             autoInstall: Seq[String] = Seq.empty,
             scalaRepl: Optional[ScalaRepl] = None,
             callbacks: Optional[Callbacks] = None,
             hiddenElements: Seq[Long] = Seq.empty,
             grading: Optional[Wire.Grading] = None,
             alwaysShowGroups: Boolean = false
           ): Document =
    val document = new Wire.Document(
      id = id,
      name = name,
      modules = modules.asJava,
      exerciseModules = exerciseModules.iterator.map { (id, mappings) =>
        Long.box(id) -> mappings.asJava
      }.toMap.asJava,
      aPlusUrl = nullable(aPlusUrl),
      languages = languages.asJava,
      version = requiredPluginVersion,
      resources = nullable(resources),
      requiredPlugins = requiredPlugins.asJava,
      vmOptions = vmOptions.asJava,
      optionalCategories = optionalCategories.asJava,
      autoInstall = autoInstall.asJava,
      scalaRepl = nullable(scalaRepl),
      callbacks = nullable(callbacks),
      hiddenElements = hiddenElements.map(Long.box).asJava,
      grading = nullable(grading),
      alwaysShowGroups = alwaysShowGroups
    )
    Wire.validate(document)
    document

object Module:
  /** Versions use major.minor format. None supports all course languages. */
  def apply(name: String, url: String, version: String = "1.0",
            language: Optional[String] = None, changelog: Optional[String] = None,
            sbt: Boolean = false): Module =
    new Wire.Module(
      name = name, url = url, version = version,
      language = nullable(language), changelog = nullable(changelog), sbt = sbt
    )

object Resources:
  /** Platform-specific IDE settings take precedence over ideSettings. */
  def apply(ideSettings: Optional[String] = None, ideSettingsMac: Optional[String] = None,
            projectSettings: Optional[String] = None, customProperties: Optional[String] = None,
            feedbackCss: Optional[String] = None, ideSettingsLinux: Optional[String] = None,
            ideSettingsWindows: Optional[String] = None): Resources =
    new Wire.Resources(
      ideSettings = nullable(ideSettings),
      ideSettingsMac = nullable(ideSettingsMac),
      projectSettings = nullable(projectSettings),
      customProperties = nullable(customProperties),
      feedbackCss = nullable(feedbackCss),
      ideSettingsLinux = nullable(ideSettingsLinux),
      ideSettingsWindows = nullable(ideSettingsWindows)
    )

object RequiredPlugin:
  /** IntelliJ plugin's display name and marketplace/plugin ID. */
  def apply(name: String, id: String): RequiredPlugin = new Wire.RequiredPlugin(name = name, id = id)

object ScalaRepl:
  /** REPL commands keyed by module name, and optional command-line arguments. */
  def apply(initialCommands: Map[String, Seq[String]] = Map.empty,
            arguments: Optional[String] = None): ScalaRepl =
    new Wire.ScalaRepl(
      initialCommands = initialCommands.map { (module, commands) => module -> commands.asJava }.asJava,
      arguments = nullable(arguments)
    )

object Callbacks:
  /** Callback identifiers interpreted by the plugin after a module download. */
  def apply(postDownloadModule: Seq[String] = Seq.empty): Callbacks =
    new Wire.Callbacks(postDownloadModule = postDownloadModule.asJava)

/** Module name usable in exercise mappings, auto-install lists and REPL settings. */
extension (module: Module)
  def name: String = module.getName
