package cars.student;

import cars.engine.Vector2;

/**
 * Obstáculo circular do mapa.
 */
public record Obstacle(Vector2 center, double radius) {}
