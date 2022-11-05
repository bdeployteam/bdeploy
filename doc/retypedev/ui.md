---
order: 5
icon: git-compare
---
# UI Front- and Backend

Contains the angular based Web frontend as well as the matching backend which is used to communicate with the server. The **BDeploy** CLI uses the same backend.

## Theming

The `app-theme.scss` currently supports dark and light themes. Components can contribute their own themes using SCSS mixins, but typically no longer do so since 4.0.0. `app-common.scss` provides a set of common styles for all components. Theme colors are available through CSS variables.
