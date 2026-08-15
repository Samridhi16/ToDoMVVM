package com.example.todomvvm.ui.add_edit_todo

import android.content.ClipDescription
import com.example.todomvvm.data.Todo

sealed class AddEditTodoEvent {
    data class onTitleChange(val title: String): AddEditTodoEvent()
    data class onDescriptionChange(val description: String): AddEditTodoEvent()
    object onSaveTodoClick: AddEditTodoEvent()
}