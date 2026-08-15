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
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed interface LoginEvent {
    data class OnEmailChanged(val email: String) : LoginEvent
    data class OnPasswordChanged(val password: String) : LoginEvent
    data object OnLoginClicked : LoginEvent
}

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<User>
}
~~~

## Optional file packs

### A. Network/API pack

Add this for login, feed, search, payments, weather, products, or any server-backed feature.

~~~text
data/remote/
├── FeatureApi.kt          # Retrofit endpoint declarations
├── FeatureRequest.kt      # Request body
├── FeatureResponse.kt     # Server JSON model
└── FeatureMapper.kt       # Optional: response to app model
~~~

~~~kotlin
interface FeatureApi {
    @POST("feature/action")
    suspend fun performAction(
        @Body request: FeatureRequest
    ): FeatureResponse
}
~~~

### B. Local persistence/Room pack

Add this only when data needs to survive app restarts: Todo, notes, favorites, offline cache, cart, or history.

~~~text
data/local/
├── FeatureEntity.kt
├── FeatureDao.kt
└── AppDatabase.kt
~~~

~~~kotlin
@Dao
interface FeatureDao {
    @Query("SELECT * FROM feature")
    fun observeAll(): Flow<List<FeatureEntity>>

    @Upsert
    suspend fun upsert(item: FeatureEntity)

    @Delete
    suspend fun delete(item: FeatureEntity)
}
~~~

### C. Navigation pack

Add this when the app has more than one destination.

~~~text
util/Routes.kt              # Route constants
MainActivity.kt             # NavHost and destinations
~~~

~~~kotlin
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}
~~~

### D. Dependency-injection pack

Add Hilt whenever you have shared dependencies such as a repository, API client, database, or DataStore.

~~~text
App.kt                      # @HiltAndroidApp
di/AppModule.kt             # Provides/Binds dependencies
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

## The memory sentence

**Screen shows state. Events describe intent. ViewModel decides. Repository gets data. Effects happen once. Optional files exist only when the feature needs them.**
