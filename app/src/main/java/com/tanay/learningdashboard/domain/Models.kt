package com.tanay.learningdashboard.domain

data class Lesson(val id: String, val title: String, val completed: Boolean)
data class Course(val id: String, val name: String, val instructor: String, val lessons: List<Lesson>) {
    val progress: Int get() = if (lessons.isEmpty()) 0 else lessons.count { it.completed } * 100 / lessons.size
}

