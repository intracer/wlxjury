package controllers

trait Instrumented extends nl.grons.metrics.scala.InstrumentedBuilder {
  val metricRegistry = Global.metrics
}