package com.example.app6mwt.di

import com.example.app6mwt.bluetooth.BluetoothService
import com.example.app6mwt.bluetooth.BluetoothServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothServiceModule {

    @Binds // @Binds es más eficiente que @Provides cuando solo queremos decir "usa esta implementación para esta interfaz"
    @Singleton // El servicio será un Singleton
    abstract fun bindBluetoothService(
        bluetoothServiceImpl: BluetoothServiceImpl // Hilt sabrá cómo crear BluetoothServiceImpl
    ): BluetoothService // Cuando alguien pida BluetoothService, Hilt proveerá BluetoothServiceImpl
}
