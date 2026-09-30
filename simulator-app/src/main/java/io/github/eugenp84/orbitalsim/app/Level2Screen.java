package io.github.eugenp84.orbitalsim.app;

import io.github.eugenp84.orbitalsim.data.planet.SolarSystemPlanet;
import io.github.eugenp84.orbitalsim.data.planet.SolarSystemPlanetCatalogue;
import io.github.eugenp84.orbitalsim.data.planet.SolarSystemPlanetCatalogueLoader;
import io.github.eugenp84.orbitalsim.learning.scenario.Level2Comparison;
import io.github.eugenp84.orbitalsim.learning.scenario.Level2PlanetProfile;
import io.github.eugenp84.orbitalsim.learning.scenario.Level2Scenario;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Level 2 (Planet Comparison): an exploratory, non-assessable comparison between the learner's
 * Level 0 planet and the eight Solar-System planets.
 *
 * @author Eugeniu PLATITA
 */
public final class Level2Screen implements LevelScreen
{
    private static final double METRES_PER_KILOMETRE = 1_000.0;
    private static final double PANEL_SPACING_PIXELS = 12.0;
    private static final double PANEL_PADDING_PIXELS = 14.0;
    private static final double COLUMN_SPACING_PIXELS = 16.0;
    private final Level2Scenario scenario = new Level2Scenario();
    private final SolarSystemPlanetCatalogue catalogue = new SolarSystemPlanetCatalogueLoader().load();
    private final Optional<Level2PlanetProfile> learnerPlanet;
    private final Map<String, String> symbols = planetSymbols();
    private final VBox learnerColumn = comparisonColumn();
    private final VBox referenceColumn = comparisonColumn();
    private final VBox explanationColumn = comparisonColumn();
    private final Parent simulationRegion;
    private final Parent controlsRegion;

    public Level2Screen(final Optional<Level2PlanetProfile> learnerPlanet)
    {
        this.learnerPlanet = learnerPlanet == null
                ? Optional.empty()
                : learnerPlanet;

        final HBox comparisonArea = new HBox(
                COLUMN_SPACING_PIXELS,
                learnerColumn,
                referenceColumn,
                explanationColumn);
        comparisonArea.setPadding(new Insets(PANEL_PADDING_PIXELS));
        comparisonArea.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(learnerColumn, Priority.ALWAYS);
        HBox.setHgrow(referenceColumn, Priority.ALWAYS);
        HBox.setHgrow(explanationColumn, Priority.ALWAYS);

        this.simulationRegion = comparisonArea;
        this.controlsRegion = buildPlanetSelector();

        final SolarSystemPlanet initial = catalogue.planets().stream()
                .filter(planet -> planet.name().equals("Earth"))
                .findFirst()
                .orElse(catalogue.planets().get(0));
        showComparison(initial);
    }

    @Override
    public void activate() {
        // Level 2 has no animation loop or simulation lifecycle.
    }

    @Override
    public Parent controlsView() {
        return controlsRegion;
    }

    @Override
    public void deactivate() {
        // Level 2 owns no running presentation resources.
    }

    @Override
    public Parent simulationView() {
        return simulationRegion;
    }

    private VBox buildPlanetSelector()
    {
        final VBox panel = new VBox(PANEL_SPACING_PIXELS);
        panel.setPadding(new Insets(PANEL_PADDING_PIXELS));
        panel.getChildren().addAll(
                sectionHeading("LEVEL 2 — PLANET COMPARISON"),
                wrappedLabel("Select a Solar-System planet to compare with the planet created in Level 0."),
                new Separator());

        for (SolarSystemPlanet planet : catalogue.planets())
        {
            final Button button = new Button(symbols.getOrDefault(planet.name(), "●") + "  " + planet.name());
            button.setMaxWidth(Double.MAX_VALUE);
            button.setOnAction(event -> showComparison(planet));
            panel.getChildren().add(button);
        }

        panel.getChildren().addAll(new Separator(), wrappedLabel("Reference data: NASA Goddard / NSSDCA Planetary Fact Sheets."));
        return panel;
    }

    private static VBox comparisonColumn()
    {
        final VBox column = new VBox(PANEL_SPACING_PIXELS);
        column.setPadding(new Insets(PANEL_PADDING_PIXELS));
        column.setMinWidth(245.0);
        column.setStyle("-fx-border-color: #b8b8b8; -fx-border-radius: 4; " + "-fx-background-radius: 4;");
        return column;
    }

    private void showComparison(final SolarSystemPlanet reference)
    {
        referenceColumn.getChildren().setAll(sectionHeading(symbols.getOrDefault(reference.name(), "●") + "  " + reference.name()),
                new Label(reference.type()),
                new Separator(),
                property("Mass", formatScientific(reference.massKilograms()) + " kg"),
                property("Mean radius", formatNumber(reference.meanRadiusKilometres()) + " km"),
                property("Mean density",formatNumber(reference.meanDensityKilogramsPerCubicMetre()) + " kg/m³"),
                property("Mean surface gravity",formatNumber(reference.meanSurfaceGravityMetresPerSecondSquared()) + " m/s²"),
                new Separator(), property("Mean distance from Sun",formatNumber(reference.meanDistanceFromSunMillionKilometres()) + " million km"),
                property("Orbital period",formatNumber(reference.orbitalPeriodDays()) + " Earth days"), wrappedLabel(reference.summary()));

        if (learnerPlanet.isEmpty())
        {
            learnerColumn.getChildren().setAll(
                    sectionHeading("YOUR PLANET"),
                    wrappedLabel("No Level 0 planet is available yet."),
                    wrappedLabel("Open Level 0 and create or configure a planet, then return to Level 2."));

            explanationColumn.getChildren().setAll(
                    sectionHeading("COMPARISON"),
                    wrappedLabel("A comparison will appear after a Level 0 planet has been created."));
            return;
        }

        final Level2PlanetProfile learner = learnerPlanet.orElseThrow();
        final Level2PlanetProfile referenceProfile = new Level2PlanetProfile(
                reference.massKilograms(),
                reference.meanRadiusKilometres() * METRES_PER_KILOMETRE,
                reference.meanDensityKilogramsPerCubicMetre(),
                reference.meanSurfaceGravityMetresPerSecondSquared());
        final Level2Comparison comparison = scenario.compare(learner, referenceProfile);

        learnerColumn.getChildren().setAll(
                sectionHeading("YOUR PLANET"),
                new Label("Created in Level 0"),
                new Separator(),
                property("Mass", formatScientific(learner.massKilograms()) + " kg"),
                property("Mean radius",formatNumber(learner.radiusMetres() / METRES_PER_KILOMETRE) + " km"),
                property("Calculated mean density",formatNumber(learner.meanDensityKilogramsPerCubicMetre()) + " kg/m³"),
                property("Calculated surface gravity",formatNumber(learner.surfaceGravityMetresPerSecondSquared()) + " m/s²"));

        explanationColumn.getChildren().setAll(
                sectionHeading("HOW THEY COMPARE"),
                wrappedLabel("Your planet relative to " + reference.name() + ":"),
                ratioText("Mass", comparison.massRatio()),
                ratioText("Radius", comparison.radiusRatio()),
                ratioText("Mean density", comparison.densityRatio()),
                ratioText("Surface gravity", comparison.surfaceGravityRatio()),
                new Separator(), wrappedLabel("1.00× means the two values are equal. " + "Values above 1.00× are larger; values below 1.00× are smaller."));
    }

    private static Label ratioText(final String property, final double ratio)
    {
        return wrappedLabel(String.format(Locale.ROOT, "%s: %.2f×", property, ratio));
    }

    private static VBox property(final String name, final String value) {
        final Label caption = new Label(name);
        caption.setStyle("-fx-font-weight: bold;");
        final Label valueLabel = wrappedLabel(value);
        return new VBox(2.0, caption, valueLabel);
    }

    private static Label sectionHeading(final String text) {
        final Label heading = new Label(text);
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        heading.setWrapText(true);
        return heading;
    }

    private static Label wrappedLabel(final String text)
    {
        final Label label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static String formatScientific(final double value)
    {
        return String.format(Locale.ROOT, "%.4e", value);
    }

    private static String formatNumber(final double value)
    {
        if (Math.abs(value) >= 10_000.0)
        {
            return String.format(Locale.ROOT, "%,.0f", value);
        }
        if (Math.abs(value) >= 100.0) {
            return String.format(Locale.ROOT, "%,.1f", value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static Map<String, String> planetSymbols()
    {
        final Map<String, String> result = new LinkedHashMap<>();
        result.put("Mercury", "☿");
        result.put("Venus", "♀");
        result.put("Earth", "⊕");
        result.put("Mars", "♂");
        result.put("Jupiter", "♃");
        result.put("Saturn", "♄");
        result.put("Uranus", "⛢");
        result.put("Neptune", "♆");
        return Map.copyOf(result);
    }
}
