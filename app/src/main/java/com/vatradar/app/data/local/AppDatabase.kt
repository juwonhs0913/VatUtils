package com.vatradar.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [AirportEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun airportDao(): AirportDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vatradar.db"
            )
                // 이 DB는 assets에서 다시 채울 수 있는 파생 데이터만 담습니다.
                // 사용자 데이터는 DataStore에 있으므로 스키마 변경 시 재생성이 안전합니다.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
