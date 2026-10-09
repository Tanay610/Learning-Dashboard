package com.tanay.learningdashboard.data

import android.content.Context
import androidx.room.*
import com.tanay.learningdashboard.domain.Course
import com.tanay.learningdashboard.domain.Lesson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "courses")
data class CourseRow(@PrimaryKey val id: String, val name: String, val instructor: String)

@Entity(tableName = "lessons", foreignKeys = [ForeignKey(entity = CourseRow::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE)], indices = [Index("courseId")])
data class LessonRow(@PrimaryKey val id: String, val courseId: String, val title: String, val completed: Boolean)

data class CourseWithLessons(@Embedded val course: CourseRow, @Relation(parentColumn = "id", entityColumn = "courseId") val lessons: List<LessonRow>)

@Dao
interface CourseDao {
    @Query("SELECT COUNT(*) FROM courses") suspend fun courseCount(): Int
    @Transaction @Query("SELECT * FROM courses ORDER BY name") fun observeAll(): Flow<List<CourseWithLessons>>
    @Transaction @Query("SELECT * FROM courses WHERE id = :id") fun observe(id: String): Flow<CourseWithLessons?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCourses(rows: List<CourseRow>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLessons(rows: List<LessonRow>)
    @Query("DELETE FROM courses") suspend fun clearCourses()
    @Transaction suspend fun replaceAll(courses: List<CourseRow>, lessons: List<LessonRow>) {
        clearCourses()
        putCourses(courses)
        putLessons(lessons)
    }
    @Query("UPDATE lessons SET completed = 1 WHERE id = :lessonId") suspend fun completeLesson(lessonId: String)
}

@Database(entities = [CourseRow::class, LessonRow::class], version = 1, exportSchema = false)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun courses(): CourseDao
    companion object { fun create(context: Context) = Room.databaseBuilder(context, LearningDatabase::class.java, "learning-dashboard.db").build() }
}

interface CourseRepository {
    fun observeCourses(): Flow<List<Course>>
    fun observeCourse(id: String): Flow<Course?>
    suspend fun refreshCourses()
    suspend fun completeLesson(id: String)
}

interface CourseRemoteSource { suspend fun fetchCourses(): List<Course> }

interface CourseLocalSource {
    fun observeCourses(): Flow<List<Course>>
    fun observeCourse(id: String): Flow<Course?>
    suspend fun hasCourses(): Boolean
    suspend fun saveCourses(courses: List<Course>)
    suspend fun completeLesson(id: String)
}

class RoomCourseLocalSource(private val dao: CourseDao) : CourseLocalSource {
    override fun observeCourses() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }
    override fun observeCourse(id: String) = dao.observe(id).map { it?.toDomain() }
    override suspend fun hasCourses() = dao.courseCount() > 0
    override suspend fun saveCourses(courses: List<Course>) {
        dao.replaceAll(
            courses.map { CourseRow(it.id, it.name, it.instructor) },
            courses.flatMap { course -> course.lessons.map { LessonRow(it.id, course.id, it.title, it.completed) } }
        )
    }
    override suspend fun completeLesson(id: String) = dao.completeLesson(id)
    private fun CourseWithLessons.toDomain() = Course(course.id, course.name, course.instructor, lessons.sortedBy { it.id.substringAfterLast('-').toIntOrNull() ?: 0 }.map { Lesson(it.id, it.title, it.completed) })
}

class MockCourseRemoteSource : CourseRemoteSource {
    override suspend fun fetchCourses(): List<Course> {
        kotlinx.coroutines.delay(650)
        return listOf(
            makeCourse("python", "Python Programming", "John Smith", 20, 13),
            makeCourse("ai", "Generative AI", "Sarah Williams", 16, 6),
            makeCourse("fullstack", "Full Stack Development", "David Brown", 28, 7)
        )
    }
    private fun makeCourse(id: String, name: String, instructor: String, total: Int, complete: Int): Course {
        val titles = listOf("Introduction", "Variables & Data Types", "Functions", "OOP")
        return Course(id, name, instructor, (0 until total).map { i -> Lesson("$id-$i", titles.getOrElse(i) { "Lesson ${i + 1}" }, i < complete) })
    }
}

class DefaultCourseRepository(private val local: CourseLocalSource, private val remote: CourseRemoteSource) : CourseRepository {
    override fun observeCourses() = local.observeCourses()
    override fun observeCourse(id: String) = local.observeCourse(id)
    override suspend fun refreshCourses() {
        // Always try the source; seed only an empty cache to preserve locally updated lesson state.
        val data = remote.fetchCourses()
        if (!local.hasCourses()) local.saveCourses(data)
    }
    override suspend fun completeLesson(id: String) = local.completeLesson(id)
}
