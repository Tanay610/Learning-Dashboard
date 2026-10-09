package com.tanay.learningdashboard.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import com.tanay.learningdashboard.data.CourseRepository
import com.tanay.learningdashboard.domain.Course
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface CoursesState { data object Loading : CoursesState; data class Success(val courses: List<Course>, val offline: Boolean = false) : CoursesState; data object Empty : CoursesState; data class Error(val message: String) : CoursesState }
sealed interface CourseState { data object Loading : CourseState; data class Success(val course: Course) : CourseState; data object Missing : CourseState; data class Error(val message: String) : CourseState }

class DashboardViewModel(private val repository: CourseRepository) : ViewModel() {
    private val _state = MutableStateFlow<CoursesState>(CoursesState.Loading)
    val state = _state.asStateFlow()
    private var refreshFailed = false
    init {
        viewModelScope.launch {
            refresh()
            repository.observeCourses().catch { _state.value = CoursesState.Error("Courses could not be loaded. Please try again.") }
                .collect { courses -> _state.value = when { courses.isEmpty() && refreshFailed -> CoursesState.Error("Courses are unavailable while offline."); courses.isEmpty() -> CoursesState.Empty; else -> CoursesState.Success(courses, refreshFailed) } }
        }
    }
    fun retry() { viewModelScope.launch { _state.value = CoursesState.Loading; refresh(); showCourses(repository.observeCourses().first()) } }
    private suspend fun refresh() {
        refreshFailed = try { repository.refreshCourses(); false } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) { true }
    }
    private fun showCourses(courses: List<Course>) { _state.value = when { courses.isEmpty() && refreshFailed -> CoursesState.Error("Courses are unavailable while offline."); courses.isEmpty() -> CoursesState.Empty; else -> CoursesState.Success(courses, refreshFailed) } }
}

class DetailsViewModel(private val repository: CourseRepository, courseId: String) : ViewModel() {
    private val _state = MutableStateFlow<CourseState>(CourseState.Loading)
    val state = _state.asStateFlow()
    init { viewModelScope.launch { repository.observeCourse(courseId).catch { cause -> if (cause is CancellationException) throw cause else _state.value = CourseState.Error("Course details could not be loaded.") }.collect { _state.value = it?.let(CourseState::Success) ?: CourseState.Missing } } }
    fun completeLesson(id: String) { viewModelScope.launch { try { repository.completeLesson(id) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { _state.value = CourseState.Error("Lesson progress could not be saved.") } } }
}

sealed interface LoginState { data object Idle : LoginState; data object Loading : LoginState; data class Error(val message: String) : LoginState; data object Success : LoginState }
class LoginViewModel : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state = _state.asStateFlow()
    fun login(email: String, password: String) {
        val validEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        if (!validEmail) { _state.value = LoginState.Error("Enter a valid email address."); return }
        if (password.length < 6) { _state.value = LoginState.Error("Password must be at least 6 characters."); return }
        viewModelScope.launch { _state.value = LoginState.Loading; delay(700); _state.value = LoginState.Success }
    }
}

