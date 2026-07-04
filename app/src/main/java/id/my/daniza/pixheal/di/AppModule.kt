package id.my.daniza.pixheal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import id.my.daniza.litert.LitertBridge
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideLitertBridge(): LitertBridge {
        return LitertBridge()
    }
}
