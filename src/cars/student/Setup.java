package cars.student;

import cars.engine.Car;

import java.util.List;

public class Setup {
    /** Mapa único, usado pelo Window (para desenhar) e pelos carros (para desviar e seguir o caminho). */
    public static final GameMap MAP = new GameMap();

    /**
     * Retorne uma lista com todos os carros que serão desenhados no exercício.
     */
    public List<Car> createCars() {
        return List.of(new StudentCar());
    }
}
