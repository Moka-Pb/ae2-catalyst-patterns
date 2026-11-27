# AE2 Catalyst Patterns (provisional name)

A small Applied Energistics 2 addon for Minecraft 1.20.1 (Forge) that lets
you mark ingredients of a processing pattern as **catalysts**: things a
recipe needs at the machine but does not consume, like molds, casts,
presses and similar tools in any mod.

## The problem

AE2's crafting planner assumes every pattern ingredient is consumed once
per operation. Put a non-consumed item into a pattern and a 100-operation
job suddenly demands 100 of them, and the planner may even try to
autocraft more of them recursively. Leave it out of the pattern and the
planner does not know it is needed at all.

## How it works

1. Encode a normal processing pattern in AE2's pattern encoding terminal.
2. Put the encoded pattern alone in a crafting grid. It converts into a
   **Catalyst Pattern** with the same contents.
3. Hold the catalyst pattern and right-click. An editor opens: one row per
   input, each with a catalyst on/off toggle. Mark the non-consumed
   ingredients and press Done.
4. Put the pattern into a pattern provider as usual.

Planning: a catalyst counts **once per pattern**, no matter how many
operations the job runs, because it reports itself as its own "remaining
item" and AE2's planner reuses remaining items across runs (the same
mechanism that makes buckets work in crafting patterns). If none is in
stock, the planner tries to craft at most one.

Execution: the catalyst is **not** pushed to the machine. It is expected to
already be resident at the machine (placed in the machine, its input bus, a
stocking bus, and so on), where it stays and is reused for every run.

## Notes and limits (v0.1.0)

- The planner needs the catalyst to exist on the network side (in storage,
  or craftable) as the job's guarantee, while the copy that actually does
  the work is the one resident at the machine. In practice: keep one in the
  network or have a pattern for it, and stock the machine as you already do.
- Patterns containing catalysts are planned one run at a time (this is how
  AE2 treats patterns with remaining items), so very large jobs may take a
  little longer to calculate.
- Editing a pattern changes its contents: take it out of the provider,
  edit, and put it back.
- The catalyst pattern currently looks like a normal processing pattern.

## Building

Forge MDK 1.20.1-47.4.0, Java 17. AE2 is pulled from CurseMaven
(file 8991271). Run `gradlew build`, or use the GitHub Actions workflow.
This source has not been compiled by the author of the addon scaffolding
yet; the first build may surface small errors.
