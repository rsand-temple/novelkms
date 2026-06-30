---
id: editor.styles
title: Paragraph Styles
section: editor
order: 30
---
# Paragraph Styles

Paragraph styles are *semantic labels* (normal, headings, block quote, chapter/part titles, and so on) rather than baked-in formatting. Each style's appearance is defined once and resolved through a cascade:

**Book → Project → Global**

Because the label is stored, not the formatting, editing a style definition reflows every paragraph that uses it. Applying a style clears manual overrides on that paragraph so it matches the definition.

Styles own *appearance*; [templates](#help:editor.templates) own *structure and tokens*.
