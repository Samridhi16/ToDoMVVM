# Universal Android MVVM Blueprint

This is a structure for any feature: login, signup, profile, settings, feed, search, checkout, chat, Todo, or a simple API screen. Start with the Core files. Add Optional files only when the feature needs them.

## Core files: use these for every feature

~~~text
ui/feature_name/
├── FeatureScreen.kt       # UI: renders state and sends events
├── FeatureViewModel.kt    # Logic: state, events, effects, actions
└── FeatureContract.kt     # FeatureUiState, FeatureEvent, FeatureEffect
~~~

You can use FeatureContract.kt as one file at first. Split it into three files later if it grows.

## The universal data flow

~~~mermaid
flowchart LR
    USER["User"] --> SCREEN["FeatureScreen"]
    SCREEN -->|"FeatureEvent"| VM["FeatureViewModel"]
    VM -->|"FeatureUiState"| SCREEN
    VM -->|"FeatureEffect\nonly once"| SCREEN
    VM -->|"optional"| REPO["Repository"]
    REPO -->|"optional"| SOURCE["API / Room / Preferences"]
~~~

- State is what the screen can draw again: text fields, loading, content, validation errors.
- Event is what the user did: tap, type, submit, retry, select.
- Effect is a one-time instruction: navigate, show snackbar, open browser, request permission.

## 1. FeatureContract.kt

This is the feature’s vocabulary. Rename the fields and events to match the screen.

~~~kotlin
data class FeatureUiState(
    val fieldOne: String = "",
    val fieldTwo: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface FeatureEvent {
    data class OnFieldOneChanged(val value: String) : FeatureEvent
    data class OnFieldTwoChanged(val value: String) : FeatureEvent
    data object OnPrimaryActionClicked : FeatureEvent
    data object OnRetryClicked : FeatureEvent
}

sealed interface FeatureEffect {
    data class Navigate(val route: String) : FeatureEffect
    data class ShowSnackbar(val message: String) : FeatureEffect
    data object GoBack : FeatureEffect
}
~~~

For a login screen, fieldOne becomes email, fieldTwo becomes password, and the primary action becomes OnLoginClicked. For a search screen, use query and OnSearchClicked. For settings, use toggle/change events.

## 2. FeatureViewModel.kt

Every ViewModel follows this shape. It owns state, handles events, runs work in viewModelScope, and emits one-time effects.

~~~kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val repository: FeatureRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeatureUiState())
    val uiState = _uiState.asStateFlow()

    private val _effect = Channel<FeatureEffect>()
    val effect = _effect.receiveAsFlow()

    fun onEvent(event: FeatureEvent) {
        when (event) {
            is FeatureEvent.OnFieldOneChanged -> {
                updateState { copy(fieldOne = event.value, error = null) }
            }
            is FeatureEvent.OnFieldTwoChanged -> {
                updateState { copy(fieldTwo = event.value, error = null) }
            }
            FeatureEvent.OnPrimaryActionClicked -> performPrimaryAction()
            FeatureEvent.OnRetryClicked -> performPrimaryAction()
        }
    }

    private fun performPrimaryAction() = viewModelScope.launch {
        val state = uiState.value
        if (state.fieldOne.isBlank()) {
            sendEffect(FeatureEffect.ShowSnackbar("First field is required"))
            return@launch
        }

        updateState { copy(isLoading = true, error = null) }

        repository.performAction(state.fieldOne, state.fieldTwo)
            .onSuccess {
                updateState { copy(isLoading = false) }
                sendEffect(FeatureEffect.Navigate("next_screen"))
            }
            .onFailure { throwable ->
                updateState {
                    copy(isLoading = false, error = throwable.message ?: "Something went wrong")
                }
            }
    }

    private fun updateState(
        transform: FeatureUiState.() -> FeatureUiState
    ) {
        _uiState.update(transform)
    }

    private fun sendEffect(effect: FeatureEffect) = viewModelScope.launch {
        _effect.send(effect)
    }
}
~~~
## The 10-second placement rule

| If you are writing... | Put it in... |
| --- | --- |
| TextField, Button, LazyColumn, Card | Screen or item Composable |
| A click, typing, retry, submit action | FeatureEvent |
| Form text, list data, loading, visible error | FeatureUiState |
| Validation, deciding what happens after a click | ViewModel |
| Navigation, snackbar, permission/open-browser command | FeatureEffect |
| A contract the ViewModel calls | Repository interface |
| Retrofit, Room, DataStore, Firebase details | Repository implementation and data source |
