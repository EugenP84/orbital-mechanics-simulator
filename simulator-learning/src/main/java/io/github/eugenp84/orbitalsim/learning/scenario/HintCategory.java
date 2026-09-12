package io.github.eugenp84.orbitalsim.learning.scenario;

/**
 * The semantic outcome of a Level 1 hint inference rule.
 * <p>
 * The category is the rule's result; the {@link Hint} message is its learner-facing rendering.
 * Separating them lets evaluation and tests reason about which rule fired without parsing prose.
 * The direction and magnitude reflect the attempt's <em>configured initial speed</em> relative to the analytic circular speed — the one quantity the learner controls — not the body's instantaneous
 * speed, which varies around an eccentric orbit and would otherwise contradict itself.
 *
 * @author Eugeniu PLATITA
 */
public enum HintCategory
{
    BELOW_CIRCULAR_SLIGHTLY,
    BELOW_CIRCULAR_WELL,
    NEAR_CIRCULAR,
    ABOVE_CIRCULAR_SLIGHTLY,
    ABOVE_CIRCULAR_WELL,
    UNBOUND
}