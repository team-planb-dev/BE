# Final Meal Nutrition Enrichment Research

## Scope

This report investigates meals added by `MissingSlotCompleter` after the AI response. It covers Spring AI tool-call lifecycle, Project Reactor operators for bounded enrichment, Java encounter-order deduplication, and the smallest safe integration point in PlanB.

## Repository facts

### 1. The missing result is caused by lifecycle order

- `PlanService.makePlanByAi()` starts `NutritionEvaluationCollector`, calls the AI path, then calls `finish()` before `finishPlan()` begins: `src/main/java/com/planb/domain/travel/service/PlanService.java:110-135`.
- `MissingSlotCompleter` runs once inside place validation: `src/main/java/com/planb/domain/travel/service/PlanService.java:787-797`.
- It can run again after schedule-time normalization through `refillMissingMeals()`: `src/main/java/com/planb/domain/travel/service/PlanService.java:515-522` and `560-586`.
- That second completion runs after `NutritionEvaluationCollector.finish()`. Its new restaurant therefore cannot be present in the captured `evaluations` list.
- `MissingSlotCompleter.mealSlot()` only fetches the TourAPI representative menu and constructs a `RestaurantDetail` whose nutrition values are null: `src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:279-320`.
- `applyDeterministicTags()` later uses the captured evaluation list both to replace AI nutrition values and calculate nutrition tags: `src/main/java/com/planb/domain/travel/service/PlanService.java:538-542` and `1101-1133`.
- If no non-`UNAVAILABLE` result exists under the original `menuName`, `nutritionAlignedRestaurant()` intentionally writes null values: `src/main/java/com/planb/domain/travel/service/PlanService.java:1218-1266`.

**Verified conclusion:** the missing nutrition values are not a parsing failure. The final restaurant set can change after the AI tool-call collection has ended.

### 2. Existing components already provide the required lookup behavior

- `NutritionService.evaluateFoodNutrition(foodName, standardFoodName, diseaseTypes)` performs the original-name lookup and one standard-name fallback: `src/main/java/com/planb/domain/travel/service/NutritionService.java:54-90`.
- Each lookup already has a 15-second timeout: `src/main/java/com/planb/domain/travel/service/NutritionService.java:35-40` and `92-100`.
- Any timeout or external failure becomes an `UNAVAILABLE` result instead of failing plan creation: `src/main/java/com/planb/domain/travel/service/NutritionService.java:78-89`.
- `TourismTool.evaluateFoodNutrition()` is an AI Tool adapter. It blocks on `NutritionService` and records the result in the request collector: `src/main/java/com/planb/ai/mcp/TourismTool.java:256-279`.

**Verified conclusion:** no new HTTP client, retry library, cache, or error abstraction is needed.

## Primary-source findings

### Spring AI tool lifecycle

Spring AI documents the tool loop as follows: the model returns a tool request, `ToolCallingManager` executes the matching callback, the result is appended to conversation history, and the loop ends when the model returns a response without tool calls. The final response is then returned to the application.

Source: [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html#_the_tool_calling_loop)

Spring AI also states that method-backed Tools cannot use `Mono` or `Flux` as method parameters or return types.

Source: [Spring AI Method Tool Limitations](https://docs.spring.io/spring-ai/reference/api/tools.html#_method_tool_limitations)

**Verified implication for PlanB:** a meal created by Java after the final AI response is outside that tool loop. Spring AI will not automatically invoke `evaluateFoodNutrition` for it. Post-response enrichment must be explicitly owned by application code.

### Reactor deduplication, concurrency, ordering, and fallback

- `Flux.distinct(keySelector)` tracks keys seen per subscriber and emits only the first element for each distinct key.
- `flatMapSequential(mapper, maxConcurrency)` eagerly subscribes to a bounded number of inner publishers while emitting results in source order.
- `concatMap` subscribes to one inner publisher at a time and preserves source order.
- `Mono.timeout(Duration)` produces a `TimeoutException` if no item arrives within the duration.
- `Mono.onErrorResume` switches to a fallback publisher after an error.

Sources:

- [Reactor Flux API: distinct](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#distinct-java.util.function.Function-)
- [Reactor Flux API: flatMapSequential](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMapSequential-java.util.function.Function-int-)
- [Reactor Flux API: concatMap](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#concatMap-java.util.function.Function-)
- [Reactor Mono API: timeout](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#timeout-java.time.Duration-)
- [Reactor Mono API: onErrorResume](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#onErrorResume-java.util.function.Function-)

### Java stable deduplication

Java 21 documents `LinkedHashSet` as a set with insertion-order encounter order and expected constant-time `add` and `contains`.

Source: [Java 21 LinkedHashSet](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashSet.html)

`LinkedHashMap` is likewise insertion ordered.

Source: [Java 21 LinkedHashMap](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/LinkedHashMap.html)

**Verified implication:** stable per-request deduplication needs no dependency. A `LinkedHashSet<String>` or `LinkedHashMap<String, ...>` can preserve final schedule encounter order while removing duplicate menu keys.

## Recommendations

The following are design recommendations, not claims made by external documentation.

### Recommended ownership and insertion point

Add final nutrition enrichment in `PlanService.finishPlan()` after:

1. schedule normalization,
2. final missing-meal completion,
3. final meal validation,

and before `applyDeterministicTags()`.

The relevant boundary is `mealFixed` at `src/main/java/com/planb/domain/travel/service/PlanService.java:517-530`. At that point the final meal set is known. Enriching earlier misses the second completion path. Enriching after `applyDeterministicTags()` is too late because that method also aligns nutrition values and creates nutrition tags.

Call `NutritionService` directly. Do not call `TourismTool` from `PlanService`:

- `TourismTool` belongs to the model tool adapter.
- The final enrichment is deterministic Java post-processing.
- The collector has already ended on the create and edit paths.
- Calling the Tool directly would create a new hidden `ThreadLocal` list that is not the already captured `evaluations` list.

### Recommended request-level deduplication

1. Build a set of menu names already present in `evaluations`. Treat `AVAILABLE`, `NOT_EVALUABLE`, and `UNAVAILABLE` as already attempted during the same request.
2. Traverse final restaurant schedules in day and schedule order.
3. Ignore null or blank menu names.
4. Deduplicate by the exact original `RestaurantDetail.menuName`.
5. Query only keys absent from the existing evaluation set.
6. Append each result as `FoodNutritionEvaluation(originalMenuName, result)`.
7. Pass the combined list to the existing `applyDeterministicTags()`.

This keeps the identity contract already used by `nutritionAlignedRestaurant()`: lookup result keys remain original restaurant menu names.

A `LinkedHashSet` is sufficient if only names are needed. Use a `LinkedHashMap` only if each key must retain extra metadata.

### Recommended execution model

Use sequential enrichment first.

Reasons:

- The public PlanService operation is synchronous.
- A trip has a small bounded meal count.
- Only meals absent from the collector are queried.
- `NutritionService` already contains timeout and non-blocking failure semantics.
- Sequential requests avoid an unnecessary burst against the public nutrition API.
- No new dependency or scheduler policy is needed.

If measured latency later requires concurrency, reuse Reactor already present in the project:

```text
Flux.fromIterable(missingMenus)
    .distinct(menuKey)
    .flatMapSequential(lookup, smallMaxConcurrency)
    .collectList()
    .block()
```

Keep each lookup's `onErrorResume` inside `NutritionService`, so one failed menu does not cancel remaining lookups. Set an explicit low `maxConcurrency`; do not use unbounded `flatMap`.

### Disease list

Flatten every selected `TravelHealthContext.diseaseTypes()`, remove nulls, and deduplicate before the lookup. This matches the prompt contract that the Tool receives the travelers' full disease list: `src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:65-78` and `268-294`.

### Standard food-name gap

There is no deterministic standard-food-name source for a Java-added meal in current DTOs:

- TourAPI supplies `firstmenu`, which becomes the original `menuName`: `src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:326-362`.
- `RestaurantDetail` has no `standardFoodName` field: `src/main/java/com/planb/ai/dto/response/CreatePlanAiResponse.java:67-77`.
- Existing standard names are model arguments supplied during the AI tool loop: `src/main/java/com/planb/ai/mcp/TourismTool.java:242-279`.

Do not strip words, split compound menus, or guess a base food name in Java without an authoritative mapping. That can silently associate the wrong nutrition record.

Two safe choices exist:

1. **Exact-name V1:** call `NutritionService.evaluateFoodNutrition(menuName, diseaseTypes)`. Standard or already searchable names are enriched. Nonstandard names remain `UNAVAILABLE`. This is the smallest safe fix, but it does **not** fully satisfy standard-name parity with issue #56.
2. **Explicit normalization call:** make one structured AI request for only the deduplicated missing menu names, then call `NutritionService` with original and returned standard names. Validate one output per input and fall back to the original name when absent or ambiguous. This satisfies parity, but adds a new model call, prompt/DTO contract, latency, cost, and failure handling.

Spring AI cannot extend the completed Tool loop implicitly. Choice 2 must be an explicit application call. This policy decision should be settled before TDD.

## Suggested test seams

Primary regression seam: public `PlanService.makePlanByAi()`.

The test should arrange:

- AI response omits a required meal.
- `MissingSlotCompleter` adds a restaurant with a known representative menu.
- Existing evaluations do not contain that menu.
- `NutritionService` returns a known result.

Assert through the returned `CreatePlanAiResponse`:

- completed meal contains known nutrition values,
- nutrition-derived tags are present,
- plan still succeeds when lookup returns `UNAVAILABLE`,
- duplicate completed menu names cause one lookup,
- an already collected menu causes no new lookup.

Keep `MissingSlotCompleterTest` focused on slot completion. A test that expects `MissingSlotCompleter` itself to call `TourismTool.evaluateFoodNutrition` fixes behavior at the wrong seam and cannot cover the second completion after collector shutdown.

## Recommended starting point

1. Settle standard-name policy.
2. Use `PlanService.makePlanByAi()` as the agreed TDD seam.
3. Add one red test for a Java-completed meal receiving final nutrition values.
4. Add final enrichment at the `mealFixed` boundary.
5. Reuse `NutritionService` timeout and failure behavior.
6. Add deduplication tests only after the first vertical slice passes.

