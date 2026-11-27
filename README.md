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

1. Open AE2's pattern encoding terminal in processing mode and set up the
   pattern as usual.
2. **Alt+click** an input slot to mark that ingredient as a catalyst.
   Marked slots get an orange frame, and the slot tooltip shows the state.
3. Encode. If at least one ingredient is marked, the result is a
   **Catalyst Pattern**; with no marks it stays a plain processing
   pattern.
4. Put the pattern into a pattern provider as usual.

You can also convert an existing processing pattern: put it alone in a
crafting grid to get a catalyst pattern, then hold it and right-click to
edit the marks afterwards. Loading a catalyst pattern into the encoding
terminal restores its marks onto the slots.

Planning: a catalyst counts **once per pattern**, no matter how many
operations the job runs, because it reports itself as its own "remaining
item" and AE2's planner reuses remaining items across runs (the same
mechanism that makes buckets work in crafting patterns). If none is in
stock, the planner tries to craft at most one.

Execution: the one catalyst the planner allocated is taken from ME
storage and pushed to the machine with the first operation of the job.
It stays at the machine and is reused for every later operation; the CPU
never waits for it to come back.

## Notes and limits (v0.2.0)

- Every job plans one catalyst from the network side. A delivered
  catalyst stays at its machine, so the next job will look for another
  one in storage (or craft one). Keeping a spare in storage is the
  intended setup, much like stocking a machine by hand today.
- Use one pattern provider per catalyst pattern. If the same catalyst
  pattern is served by several providers (parallel machines), only the
  first machine to receive a push gets the catalyst from storage.
- Two different patterns that share the same catalyst in a single job
  also share the one allocated copy. Run them as separate jobs, or stock
  the second machine by hand.
- Do not use a provider's blocking mode with catalyst patterns: the
  resident catalyst makes the machine look permanently busy to that
  check.
- The catalyst pattern currently looks like a normal processing pattern.

## Building

Forge MDK 1.20.1-47.4.0, Java 17. AE2 is pulled from CurseMaven
(file 8991271). Run `gradlew build`, or use the GitHub Actions workflow.
