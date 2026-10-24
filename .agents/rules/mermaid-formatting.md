---
trigger: all
description: Strict Mermaid diagram formatting rules to avoid syntax errors in all renderers
---

# Mermaid Diagram Formatting Guidelines

When writing any Mermaid diagrams (flowchart, graph, sequenceDiagram, etc.):
1. **Always quote node labels**: All node text and subgraph labels must be strictly enclosed in double quotes `["..."]` to prevent parser crashes from parentheses `()`, Chinese punctuation, colons, slashes, or question marks `?`.
   - Correct: `node["Android Client (Kotlin + Compose)"]`
   - Incorrect: `node[Android Client (Kotlin + Compose)]`
2. **Always quote edge/connection labels**: Text on arrows must be enclosed in double quotes `|"..."|`.
   - Correct: `A -->|"HTTP 10.0.2.2:8000"| B`
   - Incorrect: `A -->|HTTP 10.0.2.2:8000| B`
3. **Avoid raw emojis or nested quotes**: Never use nested unescaped quotes inside labels. Use plain text or safe HTML break tags `<br>`.
