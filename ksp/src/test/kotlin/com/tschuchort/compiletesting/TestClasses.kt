package com.tschuchort.compiletesting

enum class AnnotationEnumValue {
  ONE,
  TWO,
  THREE,
}

annotation class AnotherAnnotation(val input: String)

annotation class ClasspathTestAnnotation(
  val enumValue: AnnotationEnumValue,
  val enumValueArray: Array<AnnotationEnumValue>,
  val anotherAnnotation: AnotherAnnotation,
  val anotherAnnotationArray: Array<AnotherAnnotation>,
)
