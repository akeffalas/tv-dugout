# Dugout TV documentation

Dugout TV is a Google TV app that plays an approved list of baseball skill videos for
kids, with a profile for each child.

## How these documents are organized

| Section | Purpose |
|---------|---------|
| [architecture/](./architecture/README.md) | How the app is built, and the development workflow |
| [adr/](./adr/README.md) | Architecture decision records: what was decided, why, and the tradeoffs |
| [specs/](./specs/) | One numbered `NNNN-topic/` directory per feature, holding `spec.md` and `plan.md` |
| [research/](./research/README.md) | Reference notes that informed the design |
| [mockups/](./mockups/) | Visual design references, such as [the TV mockup](./mockups/tv-mockup.html) |

Start with [architecture/](./architecture/README.md), then read the
[decision records](./adr/README.md) for the reasoning behind it. The `specs/` directory
names are the feature index; `git log` holds the rest of the history.
