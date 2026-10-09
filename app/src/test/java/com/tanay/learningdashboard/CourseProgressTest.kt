package com.tanay.learningdashboard

import com.tanay.learningdashboard.data.CourseLocalSource
import com.tanay.learningdashboard.data.CourseRemoteSource
import com.tanay.learningdashboard.data.DefaultCourseRepository
import com.tanay.learningdashboard.domain.Course
import com.tanay.learningdashboard.domain.Lesson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CourseProgressTest {
    @Test fun progressIsCalculatedFromCompletedLessons() {
        val course = course(completed = false)
        assertEquals(25, course.progress)
    }

    @Test fun remoteFailureLeavesCachedCourseAndLessonProgressAvailable() = runBlocking {
        val cached = course(completed = false)
        val local = FakeLocalSource(listOf(cached))
        val repository = DefaultCourseRepository(local, object : CourseRemoteSource {
            override suspend fun fetchCourses(): List<Course> = error("Simulated offline")
        })

        try { repository.refreshCourses() } catch (_: IllegalStateException) { }
        assertEquals(25, repository.observeCourses().first().single().progress)
        repository.completeLesson("lesson-1")
        assertEquals(50, repository.observeCourse("course").first()!!.progress)
        assertTrue(repository.observeCourse("course").first()!!.lessons[1].completed)
    }

    private fun course(completed: Boolean) = Course("course", "Course", "Instructor", listOf(
        Lesson("lesson-0", "Introduction", true), Lesson("lesson-1", "Variables", completed),
        Lesson("lesson-2", "Functions", false), Lesson("lesson-3", "OOP", false)
    ))

    private class FakeLocalSource(initial: List<Course>) : CourseLocalSource {
        private val courses = MutableStateFlow(initial)
        override fun observeCourses(): Flow<List<Course>> = courses
        override fun observeCourse(id: String): Flow<Course?> = MutableStateFlow(courses.value.firstOrNull { it.id == id })
        override suspend fun hasCourses() = courses.value.isNotEmpty()
        override suspend fun saveCourses(courses: List<Course>) { this.courses.value = courses }
        override suspend fun completeLesson(id: String) {
            courses.value = courses.value.map { course -> course.copy(lessons = course.lessons.map { if (it.id == id) it.copy(completed = true) else it }) }
        }
    }
}

