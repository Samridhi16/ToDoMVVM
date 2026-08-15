# Universal Android MVVM Blueprint

This is a structure for any feature: login, signup, profile, settings, feed, search, checkout, chat, Todo, or a simple API screen. Start with the Core files. Add Optional files only when the feature needs them.

## First question: which files do I actually need?

~~~mermaid
flowchart TD
    START["New feature"] --> UI["Always: Screen + ViewModel + Contract"]
    UI --> Q1{"Needs data or business action?"}
    Q1 -->|Yes| REPO["Add Repository interface + implementation"]
    Q1 -->|No| SIMPLE["Keep logic in ViewModel"]
    REPO --> Q2{"Data comes from network?"}
    Q2 -->|Yes| API["Add API service + DTOs"]
    Q2 -->|No| Q3{"Data must persist locally?"}
    Q3 -->|Yes| ROOM["Add Entity + DAO + Room database"]
    Q3 -->|No| MEMORY["Repository can use memory/preferences"]
    UI --> Q4{"Needs another screen?"}
    Q4 -->|Yes| NAV["Add route + NavHost destination"]
    Q4 -->|No| DONE["Feature is complete"]
    API --> DONE
    ROOM --> DONE
    MEMORY --> DONE
    NAV --> DONE
    SIMPLE --> DONE
~~~

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