package cars.student;

import cars.engine.Vector2;

import java.util.List;

/**
 * Caminho fechado: o último ponto se liga ao primeiro.
 * O radius é a meia largura do caminho.
 */
public record Path(List<Vector2> points, double radius) {
    /** Ponto inicial do segmento. */
    public Vector2 start(int segment) {
        return points.get(segment % points.size());
    }

    /** Ponto final do segmento. */
    public Vector2 end(int segment) {
        return points.get((segment + 1) % points.size());
    }

    /** Quantidade de segmentos (igual à de pontos, porque o caminho é fechado). */
    public int segments() {
        return points.size();
    }
}
