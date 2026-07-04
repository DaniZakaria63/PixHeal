package id.my.daniza.local.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.my.daniza.local.PixHealDatabase
import id.my.daniza.local.ProjectDao
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
        ).build()
    }

    @Provides
    fun provideProjectDao(database: PixHealDatabase): ProjectDao {
        return database.projectDao()
    }
}
