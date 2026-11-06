# Steering Behaviors — Trabalho Prático

Disciplina: Inteligência Artificial e Ilusão de Inteligência em Jogos (PUCPR)

**Equipe:** Enzo Pradi, Marco Antônio, Tales Lima e Yasmin.

Implementação de Steering Behaviors em Java sobre o template `SBCarsEngine`. Um único carro (`StudentCar`) troca de comportamento pelo teclado.

## Como executar

1. Abra a pasta no IntelliJ (JDK 21 ou superior, porque o motor usa `Math.clamp`).
2. Execute o `main` de `cars.engine.Window`.

## Controles

| Tecla | Modo |
|---|---|
| `1` | Seek: persegue o mouse |
| `2` | Flee: foge do mouse quando ele chega perto |
| `3` | Arrive: vai até o ponto clicado e para |
| `4` | Wander: passeia sem destino |
| `5` | Obstacle Avoidance: anda reto e desvia dos obstáculos |
| `6` | Path Following: segue o caminho verde |
| `7` | Combinação **Patrulha**: Avoidance + Path Following |
| `8` | Combinação **Presa**: Avoidance + Flee + Wander |
| `D` | Liga e desliga o desenho de debug |

O modo atual aparece no canto inferior esquerdo da janela.

### Desenho de debug
- **Seta azul:** velocidade. **Seta laranja:** força de steering.
- **Retângulo vermelho:** caixa de detecção do Obstacle Avoidance.
- **Círculo verde:** círculo do Wander e o alvo sobre ele.
- **Pontos roxos:** posição futura prevista, projeção no caminho e alvo à frente.
- **Círculo cinza:** raio de pânico do Flee.

## Estrutura

| Arquivo | Conteúdo |
|---|---|
| `cars/student/StudentCar.java` | Todos os comportamentos, as combinações e o debug |
| `cars/student/GameMap.java` | Mapa de testes: 4 obstáculos e um caminho fechado |
| `cars/student/Obstacle.java` | Obstáculo circular (centro e raio) |
| `cars/student/Path.java` | Caminho fechado (lista de pontos e meia largura) |
| `cars/student/Setup.java` | Cria o carro e guarda o mapa (`Setup.MAP`) |
| `cars/engine/Window.java` | Alterado só para desenhar o mapa, o debug e o modo, e ler as teclas 1 a 8 |

## Parâmetros do carro

`maxSpeed = 200` e `maxForce = 400`. Com os valores padrão (350 e 300), o raio de curva seria `v²/a ≈ 408 px`, grande demais para uma janela de 1024×768. Com os novos valores, ele cai para cerca de 100 px.

## Ideia geral

Todo steering behavior calcula uma **velocidade desejada** e devolve a força

```
força = velocidadeDesejada − velocidadeAtual
```

A força é a diferença entre o que o carro quer fazer e o que ele está fazendo, ou seja, é uma correção. Se devolvêssemos só a velocidade desejada, o carro somaria velocidade sem nunca corrigir o rumo. O motor limita a força a `maxForce` e a velocidade a `maxSpeed`.

## Comportamentos simples

### Seek
- **Ideia:** ir em direção ao alvo (o mouse) na velocidade máxima.
- **Fórmula:** `desejada = normalizar(alvo − posição) × maxSpeed`.
- **Resultado:** o carro chega ao alvo, mas passa direto e fica dando voltas, porque nunca reduz a velocidade.

### Flee
- **Ideia:** o contrário do Seek, ou seja, fugir da ameaça (o mouse).
- **Fórmula:** `desejada = normalizar(posição − ameaça) × maxSpeed`.
- **Raio de pânico (`FLEE_AREA = 200`):** o carro só foge se a ameaça estiver a menos de 200 px. Sem esse raio, ele fugiria para sempre e sairia da tela.

### Arrive
- **Ideia:** igual ao Seek, mas freando ao chegar perto do alvo (o ponto clicado), até parar sobre ele.
- **Fórmula:** dentro do raio de frenagem (`ARRIVE_AREA = 150`), `velocidade = maxSpeed × distância / raio`. Fora do raio, a velocidade é a máxima.
- **Ganho (`4 × maxSpeed / raio`):** neste motor, com massa 1, a força `desejada − velocidade` corrige a velocidade com cerca de 1 s de atraso. Isso é lento demais para frear a tempo, e sem o ganho o carro passa do alvo e oscila. Multiplicar a força deixa a resposta mais rápida, perto do amortecimento crítico.
- **Limite do raio:** ele não pode ser menor que a distância de frenagem, `v²/(2 × maxForce) = 50 px`.

### Wander
- **Ideia:** passear sem destino, com curvas suaves.
- **Como funciona:** projetamos um círculo à frente do carro (`WANDER_DISTANCE = 100`, `WANDER_RADIUS = 50`) e guardamos um ângulo que indica um ponto na borda desse círculo. A cada quadro, o ângulo recebe uma pequena variação aleatória e o carro faz Seek nesse ponto.
- **Por que é suave:** o alvo só anda um pouco por quadro, então a trajetória fica contínua.
- **Raiz do tempo:** a variação é multiplicada por `√secs`, e não por `secs`. Passos aleatórios se somam de forma estatística (como um passeio aleatório), e só a raiz faz o resultado não depender do FPS.

| Parâmetro | Aumentar | Diminuir |
|---|---|---|
| `WANDER_ANGULO` | Mais nervoso | Quase em linha reta |
| `WANDER_RADIUS` | Curvas mais fortes | Curvas mais suaves |
| `WANDER_DISTANCE` | Curvas mais suaves | Curvas mais bruscas |

## Comportamentos avançados

### Obstacle Avoidance
1. Criamos uma **caixa de detecção** à frente do carro. A largura é a do carro (`MEIA_LARGURA_CARRO = 13`) e o comprimento cresce com a velocidade: `40 + velocidade × 0,6`.
2. Para cada obstáculo, calculamos a posição dele em relação ao carro:
   - `frente = local · direção` diz quanto ele está à frente;
   - `lado = local · perp(direção)` diz quanto ele está para o lado.
3. Ignoramos os obstáculos que estão atrás, longe demais ou com `|lado| > raio + meia largura`, porque o carro passaria sem bater.
4. Escolhemos o mais próximo e aplicamos:
   - uma **força lateral** de `maxForce`, para o lado oposto ao obstáculo;
   - uma **frenagem** que cresce quanto mais perto ele estiver.
- **Caso especial:** se o obstáculo estiver exatamente no centro (`lado = 0`), escolhemos um lado fixo. Sem isso, a força lateral seria zero e o carro bateria de frente.
- **No modo 5:** o carro tenta andar reto na velocidade máxima, para mostrar o desvio isolado.

### Path Following
Usamos a versão de Reynolds:
1. **Prevemos** a posição futura: `futuro = posição + velocidade × 0,3 s`.
2. **Projetamos** esse ponto no segmento do caminho: `t = clamp(((p − a)·(b − a)) / |b − a|², 0, 1)`.
3. **Andamos** 50 px pelo caminho a partir da projeção (`AVANCO_CAMINHO`), passando para o próximo segmento se precisar, e fazemos Seek nesse ponto.
- **Segmentos verificados:** só o atual e o próximo. Assim o carro não pula de trecho e sempre segue no mesmo sentido.
- **Ganho (`GANHO_CAMINHO = 3`):** pelo mesmo motivo do Arrive, sem ganho o carro fazia as curvas atrasado. Na simulação, ele andava em média 31 px fora do centro do caminho, sendo que a meia largura é 25 px. Com ganho 3, a média caiu para cerca de 5 px.
- **Cantos cortados:** o caminho é um octógono. Em um retângulo, as curvas de 90° seriam fechadas demais para o raio de curva do carro.

## Combinações

As forças são combinadas com **soma com prioridade** (`acumular`). Cada força é multiplicada pelo seu peso e somada ao total, mas só pode usar o que ainda **sobra** de `maxForce`. As forças mais importantes vêm primeiro.

**Por que não a soma simples:** na soma simples, forças opostas podem se anular (por exemplo, o caminho puxando para dentro do obstáculo e o desvio empurrando para fora), e o carro empaca ou atravessa o obstáculo. Com prioridade, quando existe um obstáculo à frente, o desvio usa a força primeiro e o resto só recebe o que sobra. Sem obstáculo, o desvio devolve zero e os outros comportamentos recebem a força toda.

### Combinação 1: Patrulha (tecla `7`)
**Avoidance (peso 3) + Path Following (peso 1)**

- **Ideia:** um guarda fazendo a ronda. Ele segue o caminho, e um obstáculo foi colocado de propósito sobre o trecho de cima. O carro desvia dele e depois volta à rota.
- **Por que o desvio vem primeiro:** bater no obstáculo é pior do que sair um pouco do caminho.
- **Modo 6 vs. modo 7:** no modo 6, só com Path Following, o carro passa por cima do obstáculo. No modo 7, ele contorna o obstáculo e volta para a faixa.

### Combinação 2: Presa (tecla `8`)
**Avoidance (peso 3) + Flee (peso 2) + Wander (peso 1)**

- **Ideia:** um animal pastando. Ele passeia sem rumo (Wander) e foge quando o predador, o mouse, chega perto (Flee), sem bater nos obstáculos (Avoidance).
- **Ordem:** não bater, depois fugir, depois passear. Fugir é mais urgente do que passear, mas fugir para dentro de uma pedra não faz sentido.
- **Comportamento emergente:** a fuga não é programada como "contornar obstáculos". Ela aparece da soma das forças.

### Testes dos pesos
Rodamos uma simulação de 40 s por modo, sem janela, e medimos a quantidade de quadros com o carro dentro de um obstáculo e a distância até o caminho:

| Configuração | Patrulha (modo 7) | Presa (modo 8) |
|---|---|---|
| Prioridade, pesos 3 : 1 e 3 : 2 : 1 | 0 colisões; ~13 px do caminho | 0 colisões |
| Prioridade, pesos 1 : 1 e 1 : 1 : 1 | 0 colisões; ~11 px do caminho | 0 colisões |
| Soma simples, pesos 1 : 1 e 1 : 1 : 1 | 0 colisões; ~11 px do caminho | 0 colisões |

Nesses testes, as três configurações deram resultados parecidos, porque o desvio já pede a força máxima quando detecta um obstáculo. Mesmo assim, mantivemos a prioridade e o desvio com peso maior, como garantia nos casos em que as forças se anulariam. O desvio máximo de ~60 px na Patrulha é o carro contornando o obstáculo que está sobre o caminho.

## Limitações conhecidas
- **Bordas da tela:** quando o carro sai da tela, o motor o faz reaparecer do outro lado. No Flee e no Wander isso acontece às vezes.
- **Arrive e obstáculos:** o Arrive sozinho não desvia de obstáculos, porque eles só são considerados nos modos 5, 7 e 8.
- **Carro parado:** com o Arrive, o carro pode girar um pouco ao parar, porque o desenho usa a direção de uma velocidade quase zero.
