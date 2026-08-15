# ToDoMVVM

A Jetpack Compose Todo app that stores tasks locally with Room. It supports listing tasks, creating a task, editing a task, marking it complete, deleting it, and undoing a deletion. Hilt provides the database and repository; Navigation Compose switches between the list and add/edit screens.

[Screen_recording_20260815_153711.webm](https://github.com/user-attachments/assets/c56583f2-a798-4564-83cf-47b71d238062)


## What the app currently does

| Screen | Current behavior |
| --- | --- |
| Todo list | Reads all todos from Room as a Flow, renders them in a LazyColumn, and provides add, edit, delete, undo, and completion-toggle actions. |
| Add/edit todo | Opens empty for a new todo or loads a todo by todoId for editing. It saves with Room’s REPLACE conflict strategy, then returns to the list. |
| Local storage | Persists Todo(title, description, isDone, id) records in the Room database named todo_db. |

## System design

~~~mermaid
flowchart LR
    APP["TodoApp\n@HiltAndroidApp"]
    ACTIVITY["MainActivity\nNavHost + routes"]
    LIST["TodoScreen\nLazyColumn + Snackbar"]
    EDIT["AddEditTodoScreen\nForm + Save FAB"]
    LISTVM["TodoListViewModel"]
    EDITVM["AddEditTodoViewModel"]
    REPO["TodoRepository\ninterface"]
    IMPL["TodoRepositoryImpl"]
    DAO["TodoDao"]
    DB[("Room database\ntodo_db")]

    APP -->|"enables Hilt"| ACTIVITY
    ACTIVITY -->|"todo_list"| LIST
    ACTIVITY -->|"add_edit_todo?todoId={todoId}"| EDIT
    LIST -->|"TodoListEvent"| LISTVM
    EDIT -->|"AddEditTodoEvent"| EDITVM
    LISTVM --> REPO
    EDITVM --> REPO
    REPO --> IMPL
    IMPL --> DAO
    DAO <--> DB
    LISTVM -->|"UiEvent"| LIST
    EDITVM -->|"UiEvent"| EDIT
~~~

## Request and update flow

~~~mermaid
sequenceDiagram
    autonumber
    participant S as TodoScreen
    participant VM as TodoListViewModel
    participant R as TodoRepository
    participant D as TodoDao / Room
    participant DB as todo_db

    S->>VM: Collect todos
    VM->>R: getTodos()
    R->>D: getTodos()
    D->>DB: SELECT all todos
    DB-->>D: Todo table changes
    D-->>S: Flow of Todo list
    S->>S: Render LazyColumn

    alt User toggles completion
        S->>VM: OnDoneChange(todo, checked)
        VM->>R: insertTodo(copy with new isDone)
        R->>D: Insert with REPLACE
        D->>DB: INSERT or REPLACE
    else User deletes
        S->>VM: OnDeleteTodoClick(todo)
        VM->>R: deleteTodo(todo)
        R->>D: Delete
        D->>DB: DELETE
        VM-->>S: Show deletion snackbar with Undo
    else User presses Undo
        S->>VM: OnUndoDeleteClick
        VM->>R: insertTodo(deletedTodo)
        R->>D: Insert with REPLACE
        D->>DB: Reinsert row
    end

    DB-->>S: Updated Flow of Todo list
~~~

## Navigation flow

~~~mermaid
flowchart TD
    START["App opens"] --> LIST["Route: todo_list\nTodoScreen"]
    LIST -->|"Floating Action Button"| NEW["Navigate: add_edit_todo\nNew todo"]
    LIST -->|"Tap todo"| EDIT["Navigate: add_edit_todo?todoId={id}\nExisting todo"]
    NEW --> FORM["AddEditTodoScreen"]
    EDIT --> FORM
    FORM -->|"Save successful"| BACK["UiEvent.PopBackStack"]
    BACK --> LIST
    LIST -->|"Delete"| SNACK["Show Undo snackbar"]
    SNACK -->|"Undo tapped"| LIST
~~~

## Key implementation details

### Data layer

~~~text
Todo
├── title: String
├── description: String?
├── isDone: Boolean
└── id: Int? (@PrimaryKey)
~~~

- TodoDao.getTodos() returns Flow of the Todo list, so Room re-emits it whenever the table changes.
- insertTodo() uses REPLACE. The same operation is used for create, edit, toggle-done, and undo.
- getTodoById() is a suspend one-time lookup used by the edit screen.
- TodoRepositoryImpl forwards calls to TodoDao; the repository interface keeps the ViewModels decoupled from Room.

### UI-to-ViewModel events

| Screen | Event | Actual outcome |
| --- | --- | --- |
| List | OnAddTodoClick | Emits navigation to the add/edit route. |
| List | OnTodoClick | Navigates with the selected todo’s ID. |
| List | OnDoneChange | Re-inserts a copied todo with the new isDone value. |
| List | OnDeleteTodoClick | Deletes, remembers the todo in memory, and emits an Undo snackbar. |
| List | OnUndoDeleteClick | Re-inserts the remembered todo, if present. |
| Add/edit | onTitleChange / onDescriptionChange | Updates ViewModel Compose state. |
| Add/edit | onSaveTodoClick | Saves a new/existing todo and emits pop-back-stack on success. |

### One-time UI effects

UiEvent contains Navigate, ShowSnackBar, and PopBackStack. Each ViewModel emits these through a Channel exposed with receiveAsFlow(). Each screen collects it inside LaunchedEffect(key1 = true):

- TodoScreen navigates or shows a snackbar; selecting Undo sends OnUndoDeleteClick.
- AddEditTodoScreen shows validation messages or pops the back stack after saving.

## Project map

~~~text
app/src/main/java/com/example/todomvvm/
├── TodoApp.kt                         # Hilt application
├── MainActivity.kt                    # Compose root + NavHost
├── data/
│   ├── Todo.kt                        # Room entity
│   ├── TodoDao.kt                     # Database operations
│   ├── TodoDatabase.kt                # Room database
│   ├── TodoRepository.kt              # Repository contract
│   └── TodoRepositoryImpl.kt          # DAO-backed implementation
├── di/AppModule.kt                    # Hilt database/repository providers
├── util/
│   ├── Routes.kt                      # Route strings
│   └── UiEvent.kt                     # Navigation/snackbar/back effects
└── ui/
    ├── todo_list/                     # List composables, events, ViewModel
    └── add_edit_todo/                 # Form composable, events, ViewModel
~~~

## 🚀 LaunchedEffect(key1 = true)
**Purpose:** Handling one-time side effects (Navigation, Snackbars) safely within a Composable.

*   **The "True" Key:** Ensures the coroutine starts **once** when the screen enters the composition and stays alive. Since `true` never changes, the effect is never restarted by UI refreshes (recompositions).
*   **The "Split-Second Gap" Risk:** If the key changes (e.g., using a state variable), the coroutine is cancelled and restarted. In that tiny micro-gap, any event sent from a `Channel` (like a navigation command) can be **permanently lost**.
*   **Security Guard Analogy:** Hiring the guard is the `LaunchedEffect`. `key1 = true` means you hire them **once** when the store opens. They stay at the door (`collect`) all day catching every event. If you fired and rehired them every time a customer moved a product on a shelf (`recomposition`), they would miss people entering during the switch.

---

## 🎭 Event Handling Strategy (The "Why")

We decouple the UI and ViewModel using two types of events to maintain a clean, unidirectional data flow.

### 1. Screen Events / TodoEvents (UI ➔ ViewModel)
**Mental Model:** "What can the user *do* on this screen?"
*   **Examples:** `OnDeleteClick`, `OnToggleDoneChange`, `OnAddAddClick`.
*   **Reasoning:** It labels user actions clearly as data. It consolidates all interaction logic into a single `onEvent(event)` entry point, making the ViewModel clean, readable, and extremely easy to Unit Test (you only have one function to trigger).

### 2. UI Events (ViewModel ➔ UI)
**Mental Model:** "What should the UI *do* as a one-time side effect?"
*   **Examples:** `Navigate`, `ShowSnackbar`, `PopBackStack`.
*   **Mechanism:** Handled via a `Channel` exposed as a `Flow`.
*   **Reasoning:** Unlike "State", these are transient actions. If we used `StateFlow`, a configuration change (like screen rotation) would re-trigger the action. A `Channel` ensures the event is delivered **exactly once**.

---

## 🏗️ Room Architecture
*   **Abstract Implementation:** Room generates the SQLite boilerplate; we only define the contract (DAO/Database).
*   **Flow vs Suspend:** DAOs returning `Flow` are **not** `suspend`. They return the stream object immediately. Data fetching happens asynchronously inside the stream when it is collected in the UI layer.

---

## 🧠 The Feature Development Mental Model (Template)
Follow this exact sequence to develop any new feature:

1.  **Data Layer:** 
    *   Define the **Entity** (`@Entity` data class).
    *   Create the **DAO** interface with SQL queries (`@Query`, `@Insert`, etc.).
    *   Add the DAO to the **Database** abstract class.
2.  **Domain/Repository Layer:**
    *   Define the **Repository Interface** (to keep the ViewModel testable and decoupled).
    *   Create the **Repository Implementation** class.
    *   Provide the Hilt binding in `AppModule.kt`.
3.  **Event Layer:**
    *   Define **ScreenEvents** (What actions can the user take on this screen?).
    *   Update/Define **UiEvents** (What side effects does the UI need to perform?).
4.  **ViewModel Layer:**
    *   Expose data via `Flow` (from Repository).
    *   Implement `onEvent()` to handle Screen Events.
    *   Use `viewModelScope.launch` to send `UiEvents` via the `Channel`.
5.  **UI Layer:**
    *   Create the **Item Composable** (UI for a single row/card).
    *   Create the **Main Screen Composable**.
    *   Setup `LaunchedEffect(true)` to `collect` from the ViewModel's `uiEvent` stream.
    *   Use `Scaffold` + `SnackbarHost` for transient UI feedback.
