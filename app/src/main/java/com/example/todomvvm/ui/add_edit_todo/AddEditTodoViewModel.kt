package com.example.todomvvm.ui.add_edit_todo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Popup
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.todomvvm.data.Todo
import com.example.todomvvm.data.TodoRepository
import com.example.todomvvm.util.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditTodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle // for navigation args
): ViewModel() {

    var todo by mutableStateOf<Todo?>(null)
        private set // can change from viewmodel but visible to others

    var title by mutableStateOf("")
        private set
    var description by mutableStateOf("")
        private set

    //events we send from the viewmodel to ui -> yes, navigate back/popbackstack
    private val _uiEvent = Channel<UiEvent>()
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        //did we open this screen from clicking a todo item(edit) or clicking
        //plus button(add)
       val todoId = savedStateHandle.get<Int>("todoId")!!
       if(todoId != -1){
           viewModelScope.launch {
               todoRepository.getTodoById(todoId)?.let { todo ->
                   title = todo.title
                   description = todo.description ?: ""
                   this@AddEditTodoViewModel.todo = todo
               }
           }
       }
    }

    fun onEvent(event: AddEditTodoEvent){
        when(event){
            is AddEditTodoEvent.onTitleChange -> {
                title = event.title
            }
            is AddEditTodoEvent.onDescriptionChange -> {
                description = event.description
            }
            is AddEditTodoEvent.onSaveTodoClick ->{
                viewModelScope.launch {
                    if(title.isNotBlank()){
                        sendUiEvent(UiEvent.ShowSnackBar("Title cannot be empty"))
                        return@launch
                    }
                    todoRepository.insertTodo(
                        Todo(
                            title = title,
                            description = description,
                            isDone = todo?.isDone ?: false,
                            id = todo?.id
                        )
                    )
                    sendUiEvent(UiEvent.PopBackStack)
                }
            }
        }
    }
    private fun sendUiEvent(event: UiEvent){
        viewModelScope.launch {
            _uiEvent.send(event)
        }
    }
}