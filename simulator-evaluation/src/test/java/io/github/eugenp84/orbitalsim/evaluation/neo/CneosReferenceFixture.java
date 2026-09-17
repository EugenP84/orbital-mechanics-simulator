package io.github.eugenp84.orbitalsim.evaluation.neo;

/**
 * Raw string-valued representation of the preserved JPL CAD reference fixture.
 *
 * @param source source system name
 * @param apiVersion CAD API version
 * @param captureDate date on which the reference was preserved
 * @param query reproducible CAD API query
 * @param designation small-body designation
 * @param fullName formatted small-body name
 * @param orbitId JPL orbit solution identifier used by CAD
 * @param approachBody close-approach body
 * @param timeScale time scale used by the close-approach epoch
 * @param jdTdb close-approach Julian Day TDB
 * @param calendarTdb formatted close-approach epoch
 * @param nominalDistanceAu nominal close-approach distance in au
 * @param minimum3SigmaDistanceAu minimum 3-sigma close-approach distance in au
 * @param maximum3SigmaDistanceAu maximum 3-sigma close-approach distance in au
 * @param relativeVelocityKmPerSecond relative velocity at close approach in km/s
 * @param vInfinityKmPerSecond hyperbolic excess velocity in km/s
 * @param timeUncertainty3Sigma formatted 3-sigma close-approach time uncertainty
 *
 * @author Eugeniu PLATITA
 */
record CneosReferenceFixture(
        String source,
        String apiVersion,
        String captureDate,
        String query,
        String designation,
        String fullName,
        String orbitId,
        String approachBody,
        String timeScale,
        String jdTdb,
        String calendarTdb,
        String nominalDistanceAu,
        String minimum3SigmaDistanceAu,
        String maximum3SigmaDistanceAu,
        String relativeVelocityKmPerSecond,
        String vInfinityKmPerSecond,
        String timeUncertainty3Sigma) {
}
