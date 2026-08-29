package io.github.eugenp84.orbitalsim.core.simulation;

/**
 * Receives failures thrown by {@link SimulationListener} callbacks.
 * <p>
 * Listener failures are reported through this separate handler rather than back through the listener mechanism itself, so that reporting a failure cannot re-enter the failing path (ADR-006 §9).
 * The handler is invoked once per failing listener, after the offending callback has been isolated;
 * the committed simulation step is never affected.
 * <p>
 * A handler should itself avoid throwing. If it does throw a
 * {@link RuntimeException}, the engine performs a non-recursive best-effort
 * fallback report and continues — it does not route the handler's own failure
 * back through this interface.
 */
@FunctionalInterface
public interface ListenerErrorHandler
{

    /**
     * @param listener the listener whose callback threw
     * @param failure  the runtime exception it threw
     */
    void onListenerError(SimulationListener listener, RuntimeException failure);
}