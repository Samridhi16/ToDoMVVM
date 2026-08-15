# ToDoMVVM - Architecture & Development Guide

This guide provides a mental model and template for developing features in this project using Jetpack Compose, Room, and Hilt.

---

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
