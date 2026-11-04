package cars.engine;

import java.util.List;

import static cars.engine.Car.distance;

public final class World {
    private final Car current;
    private final List<Car> cars;
    private final Vector2 mousePos;   // absolute scene coords; may be null
    private final Vector2 clickPos;   // center-relative coords; may be null
    private final double secs;

    private final double width;
    private final double height;

    public World(double secs,
                 Car current,
                 List<Car> cars,
                 Vector2 mousePos,
                 Vector2 clickPos,
                 double width,
                 double height) {
        this.current = current;
        this.cars = cars;
        this.mousePos = mousePos;
        this.clickPos = clickPos;
        this.secs = secs;
        this.width = width;
        this.height = height;
    }

    public Vector2 getMousePos() {
        return mousePos == null ? null : mousePos.clone();
    }

    public Vector2 getClickPos() {
        return clickPos == null ? null : clickPos.clone();
    }

    public List<Car> getNeighbors() {
        return this.cars.stream()
            .filter(c -> c != current)
            .toList();
    }

    public List<Car> getNeighbors(int radius) {
        return this.cars.stream()
            .filter(c -> c != current)
            .filter(c -> distance(current, c) <= radius)
            .toList();
    }

    public double getSecs() {
        return secs;
    }

    public double getWidth() { return width; }
    public double getHeight() { return height; }
}
