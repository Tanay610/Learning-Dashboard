package com.tanay.learningdashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tanay.learningdashboard.data.*
import com.tanay.learningdashboard.domain.Course
import com.tanay.learningdashboard.presentation.*
import com.tanay.learningdashboard.ui.theme.LearningDashboardTheme

class MainActivity : ComponentActivity() {
    private val repository by lazy { DefaultCourseRepository(RoomCourseLocalSource(LearningDatabase.create(applicationContext).courses()), MockCourseRemoteSource()) }
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge(); setContent { LearningDashboardTheme { App(repository) } } }
}

@Composable private fun App(repository: CourseRepository) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "login") {
        composable("login") { val vm: LoginViewModel = viewModel(); LoginScreen(vm) { nav.navigate("dashboard") { popUpTo("login") { inclusive = true } } } }
        composable("dashboard") { val vm: DashboardViewModel = viewModel(factory = factory { DashboardViewModel(repository) }); DashboardScreen(vm) { id -> nav.navigate("course/$id") } }
        composable("course/{courseId}", arguments = listOf(navArgument("courseId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("courseId").orEmpty()
            val vm: DetailsViewModel = viewModel(key = id, factory = factory { DetailsViewModel(repository, id) })
            DetailsScreen(vm) { nav.popBackStack() }
        }
    }
}

private fun <T : ViewModel> factory(create: () -> T) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
}

@Composable private fun LoginScreen(vm: LoginViewModel, onSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    val state by vm.state.collectAsState()
    LaunchedEffect(state) { if (state == LoginState.Success) onSuccess() }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Learning Dashboard", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp)); Text("Sign in to continue learning")
        Spacer(Modifier.height(24.dp)); OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp)); OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (state is LoginState.Error) Text((state as LoginState.Error).message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(20.dp)); Button(onClick = { vm.login(email, password) }, enabled = state != LoginState.Loading, modifier = Modifier.fillMaxWidth()) { if (state == LoginState.Loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Login") }
        Text("Demo: any valid email and password (6+ characters)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DashboardScreen(vm: DashboardViewModel, open: (String) -> Unit) {
    val state by vm.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("My Courses") }) }) { padding ->
        when (val value = state) {
            CoursesState.Loading -> Center { CircularProgressIndicator() }
            CoursesState.Empty -> Center { Text("No courses available yet.") }
            is CoursesState.Error -> Center { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(value.message); Button(onClick = vm::retry) { Text("Retry") } } }
            is CoursesState.Success -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (value.offline) item { Text("Showing saved courses while offline.", color = MaterialTheme.colorScheme.secondary) }
                items(value.courses, key = { it.id }) { CourseCard(it) { open(it.id) } }
            }
        }
    }
}

@Composable private fun CourseCard(course: Course, open: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(course.name, style = MaterialTheme.typography.titleLarge); Text("Instructor: ${course.instructor}")
        Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { course.progress / 100f }, modifier = Modifier.fillMaxWidth())
        Text("${course.progress}% complete · ${course.lessons.size} lessons", modifier = Modifier.padding(top = 6.dp))
        Button(onClick = open, modifier = Modifier.align(Alignment.End)) { Text("Continue") }
    } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun DetailsScreen(vm: DetailsViewModel, back: () -> Unit) {
    val state by vm.state.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text("Course Details") }, navigationIcon = { TextButton(onClick = back) { Text("Back") } }) }) { padding ->
        when (val value = state) {
            CourseState.Loading -> Center { CircularProgressIndicator() }
            CourseState.Missing -> Center { Text("This course is no longer available.") }
            is CourseState.Error -> Center { Text(value.message) }
            is CourseState.Success -> LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(value.course.name, style = MaterialTheme.typography.headlineSmall); Text("${value.course.progress}% complete"); LinearProgressIndicator(progress = { value.course.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)); Text("Lessons", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp)) }
                items(value.course.lessons, key = { it.id }) { lesson -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(lesson.title); Text(if (lesson.completed) "Completed" else "Pending", style = MaterialTheme.typography.bodySmall) }; if (!lesson.completed) Button(onClick = { vm.completeLesson(lesson.id) }) { Text("Mark complete") } else Text("Done") } } }
            }
        }
    }
}

@Composable private fun Center(content: @Composable () -> Unit) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }


