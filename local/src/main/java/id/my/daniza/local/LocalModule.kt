package id.my.daniza.local

import android.content.Context
import id.my.daniza.local.project.ProjectDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PixHealDatabase {
        return Room.databaseBuilder(
            context,
            PixHealDatabase::class.java,
            "pixheal.db"
        ).fallbackToDestructiveMigration(true).build()
    }

    @Provides
    fun provideProjectDao(database: PixHealDatabase): ProjectDao {
        return database.projectDao()
    }
}