package com.uade.hmig1.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** Historial persistido localmente (misiones, alertas, comandos y conexión). */
@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val title: String,
    val detail: String,
    val missionId: String?,
)

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events ORDER BY timestamp DESC, id DESC LIMIT 300")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("DELETE FROM events")
    suspend fun clear()
}

@Database(entities = [EventEntity::class], version = 1, exportSchema = false)
abstract class EventDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao

    companion object {
        fun create(context: Context): EventDatabase =
            Room.databaseBuilder(context, EventDatabase::class.java, "hmi_events.db").build()
    }
}
