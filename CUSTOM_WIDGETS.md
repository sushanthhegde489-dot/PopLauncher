# Pop Launcher — Custom Mini Applets Complete Guide

Pop Launcher custom widgets are **Interactive Mini Applets**. Creators can publish full-featured mini applications—including To-Do Lists, Calculators, Notes, Live API Weather/Crypto Feeds, and Utility Tools—using a declarative, secure JSON schema.

No Kotlin compilation, complex Android development, or backend servers required—just structured JSON hosted on GitHub Pages or any HTTPS web server.

---

## 1. Hosting & Setup

### One-Time Setup on GitHub Pages
1. Push your project to a public GitHub repository.
2. Ensure `privacy-policy.html` and `custom-widgets/manifest.json` are in your repository's root or `custom-widgets` folder.
3. In GitHub, go to **Settings → Pages** and select **Deploy from a branch** (choose `main` branch and `/` root folder).
4. Update your Android project's `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="privacy_policy_url">https://YOUR_USERNAME.github.io/YOUR_REPO/privacy-policy.html</string>
   <string name="custom_widgets_manifest_url">https://YOUR_USERNAME.github.io/YOUR_REPO/custom-widgets/manifest.json</string>
   ```

---

## 2. Manifest Schema (`manifest.json`)

Your `manifest.json` file contains catalog metadata and mini applet definitions.

```json
{
  "schemaVersion": 1,
  "widgets": [
    {
      "id": "todo_mini_applet",
      "name": "To-Do List Applet",
      "description": "Interactive task checklist mini applet with add and delete.",
      "version": 1,
      "icon": "✅",
      "definition": { ... }
    }
  ]
}
```

### Manifest Fields
| Field | Type | Description | Required |
|---|---|---|---|
| `id` | String | Unique lowercase ID (`[a-z0-9._-]`). | Yes |
| `name` | String | User-facing display title. | Yes |
| `description` | String | Brief summary of what the mini applet does. | Yes |
| `version` | Integer | Incremental version number (e.g., `1`, `2`). | Yes |
| `icon` | String | Emoji or short text representation. | No |
| `thumbnailUrl` | String | HTTPS URL to preview image (must be on the same domain). | No |
| `definition` | Object | The interactive mini applet structure (see below). | Yes |

---

## 3. Mini Applet Structure & State Variables

Each mini applet `definition` contains initial reactive state variables and UI layout elements:

```json
{
  "id": "todo_mini_applet",
  "title": "Tasks & Memos",
  "subtitle": "Quick home screen task applet",
  "state": {
    "task_input": "",
    "items": "[\"Buy groceries\", \"Call Mom\"]"
  },
  "elements": [ ... ]
}
```

### Expression & Evaluation Engine
- State variables are referenced in text using `{variableName}` (e.g. `"{count} items remaining"`).
- Dynamic calculations use `{eval: expression}`:
  - Arithmetic: `{eval: price * 1.08}`
  - List length & values: `{eval: items}`

---

## 4. Mini Applet UI Elements

Widgets support a rich library of interactive mini-applet UI components:

### A. Text Input (`input` / `textfield`)
Live text field allowing users to type directly inside the mini applet.
```json
{
  "type": "input",
  "placeholder": "Add a new task...",
  "targetVar": "task_input",
  "action": "ADD_TO_LIST",
  "param": "items"
}
```

### B. Dynamic Item List (`list`)
Renders child elements dynamically for every item in a JSON array state variable.
```json
{
  "type": "list",
  "itemsVar": "items",
  "itemTemplate": {
    "type": "text",
    "text": "• {item}",
    "size": "BODY",
    "color": "DEFAULT"
  }
}
```

### C. Slider (`slider`)
Range control updating state variables between minimum and maximum bounds.
```json
{
  "type": "slider",
  "currentVar": "volume",
  "minVal": 0,
  "maxVal": 100,
  "targetVar": "volume"
}
```

### D. Switch / Toggle (`switch`)
Boolean toggle switch for applet options.
```json
{
  "type": "switch",
  "label": "Dark Mode",
  "targetVar": "dark_enabled"
}
```

### E. Chip / Tag (`chip`)
Filter or tag selection button inside applets.
```json
{
  "type": "chip",
  "label": "Work",
  "action": "SET_VALUE",
  "targetVar": "category",
  "param": "Work",
  "selected": true
}
```

### F. Button (`button`)
Interactive button triggering state mutations, API fetches, or system actions.
```json
{
  "type": "button",
  "label": "+ Add Item",
  "action": "ADD_TO_LIST",
  "targetVar": "items",
  "param": "{task_input}"
}
```

### G. Containers (`card` / `row` / `column` / `spacer` / `divider`)
- **`card`**: Surface box container with rounded corners and outline border.
- **`row`**: Horizontal layout container with `alignment` (`START`, `CENTER`, `END`, `SPACE_BETWEEN`).
- **`column`**: Vertical layout container with `spacingDp`.
- **`spacer`**: Vertical spacer (`heightDp`).
- **`divider`**: Thin separator line.

---

## 5. Mini Applet Action Reference

| Action | `targetVar` | `param` | Description |
|---|---|---|---|
| `INCREMENT` | State variable | Step (default `"1"`) | Increases a numeric variable. |
| `DECREMENT` | State variable | Step (default `"1"`) | Decreases a numeric variable. |
| `RESET` | State variable | Reset value | Resets a state variable. |
| `SET_VALUE` | State variable | Value string | Sets a variable to any string or expression result. |
| `TOGGLE` | State variable | N/A | Toggles boolean (`"true"` ↔ `"false"`). |
| `ADD_TO_LIST` | State array var | Item value / var | Appends value or `{input}` to a JSON array list variable. |
| `REMOVE_FROM_LIST` | State array var | Index / value | Removes item at index or matching value from JSON array. |
| `CLEAR_LIST` | State array var | N/A | Clears list variable (`"[]"`). |
| `COMPUTE` | Target variable | Math formula | Evaluates arithmetic formula (e.g. `val1 + val2`, `price * qty`) into target variable. |
| `EVAL_SCRIPT` | Target variable | Statements | Evaluates multi-statement script (`a=1; b=a*2`). |
| `FETCH_API` | Target prefix | HTTPS API URL | Fetches HTTPS JSON API and populates JSON key-values into state. |
| `COPY_TO_CLIPBOARD` | N/A | Text or `{var}` | Copies text or state variable content to system clipboard. |
| `OPEN_APP` | Package name | N/A | Launches target Android app package directly. |
| `OPEN_SETTINGS` | Category name | N/A | Opens system settings page (`wifi`, `bluetooth`, `battery`, etc.). |
| `OPEN_URL` | Web URL | N/A | Opens browser to HTTPS web address. |
| `OPEN_LAUNCHER_SETTINGS` | N/A | N/A | Opens Pop Launcher's settings screen. |

---

## 6. Complete Practical Mini Applet Examples

### Example 1: To-Do Checklist Mini Applet
```json
{
  "id": "todo_applet",
  "title": "To-Do Checklist",
  "subtitle": "Interactive Task Manager Applet",
  "state": {
    "task_input": "",
    "items": "[\"Buy coffee\", \"Finish build\"]"
  },
  "elements": [
    {
      "type": "row",
      "alignment": "SPACE_BETWEEN",
      "children": [
        {
          "type": "input",
          "placeholder": "Type new task...",
          "targetVar": "task_input"
        },
        {
          "type": "button",
          "label": "+ Add",
          "action": "ADD_TO_LIST",
          "targetVar": "items",
          "param": "{task_input}"
        }
      ]
    },
    { "type": "spacer", "heightDp": 10 },
    {
      "type": "card",
      "children": [
        {
          "type": "list",
          "itemsVar": "items",
          "itemTemplate": {
            "type": "text",
            "text": "• {item}",
            "size": "BODY",
            "color": "DEFAULT"
          }
        }
      ]
    },
    { "type": "spacer", "heightDp": 8 },
    {
      "type": "row",
      "alignment": "END",
      "children": [
        {
          "type": "button",
          "label": "Clear All",
          "action": "CLEAR_LIST",
          "targetVar": "items"
        }
      ]
    }
  ]
}
```

### Example 2: Calculator & Unit Converter Mini Applet
```json
{
  "id": "calc_applet",
  "title": "Mini Calculator",
  "subtitle": "Instant formula computer applet",
  "state": {
    "val1": "12",
    "val2": "8",
    "result": "20"
  },
  "elements": [
    {
      "type": "card",
      "children": [
        {
          "type": "text",
          "text": "Result: {result}",
          "size": "BIG_NUMBER",
          "color": "ACCENT",
          "alignment": "CENTER"
        }
      ]
    },
    { "type": "spacer", "heightDp": 12 },
    {
      "type": "row",
      "alignment": "SPACE_BETWEEN",
      "children": [
        {
          "type": "input",
          "placeholder": "Value 1",
          "targetVar": "val1"
        },
        {
          "type": "input",
          "placeholder": "Value 2",
          "targetVar": "val2"
        }
      ]
    },
    { "type": "spacer", "heightDp": 8 },
    {
      "type": "row",
      "alignment": "SPACE_BETWEEN",
      "children": [
        {
          "type": "button",
          "label": "Add (+)",
          "action": "COMPUTE",
          "targetVar": "result",
          "param": "val1 + val2"
        },
        {
          "type": "button",
          "label": "Multiply (*)",
          "action": "COMPUTE",
          "targetVar": "result",
          "param": "val1 * val2"
        }
      ]
    }
  ]
}
```

### Example 3: Live Weather & API Feed Applet
```json
{
  "id": "api_weather_applet",
  "title": "Live Weather Feed",
  "subtitle": "HTTPS API Mini Applet",
  "state": {
    "api_temp": "72°F",
    "api_condition": "Sunny ☀️"
  },
  "elements": [
    {
      "type": "card",
      "children": [
        {
          "type": "text",
          "text": "{api_temp} • {api_condition}",
          "size": "TITLE",
          "color": "ACCENT",
          "alignment": "CENTER"
        }
      ]
    },
    { "type": "spacer", "heightDp": 10 },
    {
      "type": "button",
      "label": "🔄 Refresh Weather Data",
      "action": "FETCH_API",
      "param": "https://api.open-meteo.com/v1/forecast?latitude=37.77&longitude=-122.41&current_weather=true"
    }
  ]
}
```

### Example 4: Quick Notes Scratchpad & Clipboard Applet
```json
{
  "id": "notes_applet",
  "title": "Quick Scratchpad",
  "subtitle": "Instant note taker with clipboard copy",
  "state": {
    "note": "Meeting at 3 PM today."
  },
  "elements": [
    {
      "type": "input",
      "placeholder": "Write a quick note...",
      "targetVar": "note"
    },
    { "type": "spacer", "heightDp": 10 },
    {
      "type": "card",
      "children": [
        {
          "type": "text",
          "text": "\"{note}\"",
          "size": "BODY",
          "font": "DOT_MATRIX",
          "color": "MUTED"
        }
      ]
    },
    { "type": "spacer", "heightDp": 8 },
    {
      "type": "button",
      "label": "📋 Copy Note to Clipboard",
      "action": "COPY_TO_CLIPBOARD",
      "param": "{note}"
    }
  ]
}
```

---

## 7. Security Best Practices

### A. Intent Verification
- `OPEN_APP`: Verifies target package exists before launching.
- `OPEN_SETTINGS`: Maps strictly to valid Android `Settings` intents.
- `OPEN_URL` & `FETCH_API`: Must use safe `https://` schemes.

### B. Bounds & Execution Limits
- State entries are capped to prevent memory exploitation.
- Maximum nested element depth is capped to prevent stack overflow issues.
- Mini applets operate statelessly inside local app memory without accepting remote binary executable code.
