package fi.aalto.cs.aplus.courseconfig.scala

import fi.aalto.cs.aplus.courseconfig.CourseConfig as Wire

import _root_.scala.collection.immutable.ListMap
import _root_.scala.jdk.CollectionConverters.*

/** Shared grading model. Unknown style strings are preserved. */
type Grading = Wire.Grading

/** Grade reached and the next grade's requirements per category. */
type GradeProgress = Wire.GradeProgress

extension (grading: Grading)
  /** Grade reached with points earned per category. None for invalid rules, unknown styles or missing O1 earned categories. */
  def progress(earned: Map[String, Int]): Option[GradeProgress] =
    Option(grading.progress(earned.map((category, points) => category -> Int.box(points)).asJava))

object Grading:
  object O1:
    /** O1 grade progression, lowest to highest. Category thresholds must not decrease. */
    def apply(thresholds: Seq[Threshold]): Grading =
      create("o1", thresholds.map(t => t.grade -> Map("A" -> t.a, "B" -> t.b, "C" -> t.c)))

    /** Minimum effective points. Surplus B/C fills A, then remaining C fills B. */
    final case class Threshold(grade: String, a: Int, b: Int, c: Int)
  end O1

  object Total:
    /** Total-points grade progression, lowest to highest. Thresholds must not decrease. */
    def apply(thresholds: Seq[Threshold]): Grading =
      create("total", thresholds.map(t => t.grade -> Map("total" -> t.points)))

    /** Minimum total score. Grades may use text labels. */
    final case class Threshold(grade: String, points: Int)
  end Total

  private def create(style: String, thresholds: Seq[(String, Map[String, Int])]): Grading =
    require(thresholds.map(_._1).distinct.size == thresholds.size, "grading: duplicate grade labels")
    val points = ListMap.from(thresholds.map { (grade, categories) =>
      grade -> categories.map { (category, points) => category -> Int.box(points) }.asJava
    }).asJava
    val grading = new Wire.Grading(style, points)
    grading.validate()
    grading

