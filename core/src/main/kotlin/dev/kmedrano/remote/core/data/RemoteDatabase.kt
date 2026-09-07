package dev.kmedrano.remote.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DeviceEntity::class], version = 1, exportSchema = false)
abstract class RemoteDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile private var instance: RemoteDatabase? = null

        fun getInstance(context: Context): RemoteDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    RemoteDatabase::class.java,
                    "universal-remote.db",
                ).build().also { instance = it }
            }
    }
}
