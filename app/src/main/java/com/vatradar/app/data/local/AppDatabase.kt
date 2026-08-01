package com.vatradar.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AirportEntity::class, ChallengeEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun airportDao(): AirportDao
    abstract fun challengeDao(): ChallengeDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * 공항 테이블은 assets에서 다시 채우면 그만이지만 사용자가 뽑아 둔 경로는
         * 복구할 방법이 없습니다. 스키마를 바꿀 때는 반드시 마이그레이션을 추가하세요.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS challenges (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        origin TEXT NOT NULL,
                        destination TEXT NOT NULL,
                        distanceNm INTEGER NOT NULL,
                        points INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        seenEnroute INTEGER NOT NULL DEFAULT 0,
                        baselinePilotHours REAL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenges_createdAt ON challenges(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenges_status ON challenges(status)")
            }
        }

        /**
         * 포인트 제도를 없애면서 points 컬럼을 걷어냅니다.
         * SQLite는 DROP COLUMN을 오래 지원하지 않았으므로 표준 방식대로
         * 새 테이블을 만들어 옮겨 담습니다. 뽑아 둔 경로는 그대로 살아남습니다.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS challenges_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        origin TEXT NOT NULL,
                        destination TEXT NOT NULL,
                        distanceNm INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        seenEnroute INTEGER NOT NULL DEFAULT 0,
                        baselinePilotHours REAL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO challenges_new
                        (id, origin, destination, distanceNm, status, createdAt,
                         completedAt, seenEnroute, baselinePilotHours)
                    SELECT id, origin, destination, distanceNm, status, createdAt,
                           completedAt, seenEnroute, baselinePilotHours
                    FROM challenges
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE challenges")
                db.execSQL("ALTER TABLE challenges_new RENAME TO challenges")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenges_createdAt ON challenges(createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_challenges_status ON challenges(status)")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "vatradar.db"
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // 다운그레이드(예: 이전 버전 재설치)는 마이그레이션을 쓸 수 없어 재생성합니다.
                .fallbackToDestructiveMigrationOnDowngrade()
                .build().also { instance = it }
        }
    }
}
