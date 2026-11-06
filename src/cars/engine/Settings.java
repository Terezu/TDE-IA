package cars.engine;

import java.awt.*;
import java.util.random.RandomGenerator;

import static cars.engine.Vector2.vec2;

public final class Settings {
    private static final RandomGenerator RND = RandomGenerator.getDefault();

    Color color;
    double mass = 1;
    double maxForce = 300;
    double maxSpeed = 350;
    Vector2 position = vec2();
    double orientation = 0;

    Settings() {
        randomColor();
    }

    public Settings rgb(int rgb) {
        this.color = new Color(rgb);
        return this;
    }

    public Settings rgb(int r, int g, int b) {
        r = Math.clamp(r, 0, 255);
        g = Math.clamp(g, 0, 255);
        b = Math.clamp(b, 0, 255);
        return color(new Color(r, g, b));
    }

    public Settings hsb(float hue, float saturation, float brightness) {
        return color(Color.getHSBColor(hue, saturation, brightness));
    }

    public Settings color(Color color) {
        return rgb(color.getRGB());
    }

    public Settings randomColor() {
        var r = RND.nextInt(200) + 55;
        var g = RND.nextInt(200) + 55;
        var b = RND.nextInt(200) + 55;
        return color(new Color(r, g, b));
    }

    public Settings mass(double mass) {
        if (mass < 0.1 || mass > 100) {
            throw new IllegalArgumentException("mass must be between 0.1 and 100");
        }
        this.mass = mass;
        return this;
    }

    public Settings maxForce(double maxForce) {
        if (maxForce < 0) {
            throw new IllegalArgumentException("maxForce must be greater than 0");
        }
        this.maxForce = maxForce;
        return this;
    }

    public Settings maxSpeed(double maxSpeed) {
        if (maxSpeed < 0) {
            throw new IllegalArgumentException("maxSpeed must be greater than 0");
        }
        this.maxSpeed = maxSpeed;
        return this;
    }

    public Settings position(Vector2 position) {
        if (position == null) {
            throw new IllegalArgumentException("position must not be null");
        }
        this.position = position;
        return this;
    }

    public Settings position(double x, double y) {
        return position(vec2(x, y));
    }

    public Settings randomPosition() {
        var hw = (int) (Window.INITIAL_WIDTH / 2.0);
        var hh = (int) (Window.INITIAL_HEIGHT / 2.0);
        return position(RND.nextInt(-hw, hw), RND.nextInt(-hh, hh));
    }

    public Settings orientation(double radians) {
        this.orientation = radians;
        return this;
    }

    public Settings orientationDegrees(double degrees) {
        return orientation(Math.toRadians(degrees));
    }

    public Settings randomOrientation() {
        return orientation(RND.nextDouble(0, 2 * Math.PI));
    }
}