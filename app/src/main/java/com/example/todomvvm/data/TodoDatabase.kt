package com.example.todomvvm.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Todo::class],
    version = 1
)
//abstract so that room handles all the class generation and stuff
abstract class TodoDatabase: RoomDatabase() {
    abstract val dao: TodoDao
}