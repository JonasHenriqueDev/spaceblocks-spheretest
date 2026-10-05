# Space Blocks 0.7.0 — Spheretest

Minecraft Java **1.21.1**, NeoForge **21.1.255**, **Java 21**. Um mapa plano quadrado finito, com bordas opostas conectadas, desenhado com a projeção exponencial relativa à câmera de **Jeija / Spheretest**. Armazenamento, colisões e gravidade permanecem planos.

## Jogar

Instale apenas `spaceblocks-0.7.0.jar` e habilite comandos no mundo. Os mapas planos da 0.6.0 são preservados; o terreno natural tem uma dimensão nova e não substitui suas construções.

| Comando | Ação |
| --- | --- |
| `/planet`, `/planet large` ou `/planet natural` | Planeta natural: relevo, oceanos, cursos de água, cavernas, biomas, árvores, minérios e estruturas procedurais |
| `/planet small` | Planeta pequeno plano para testar rapidamente |
| `/planet flat` | Planeta maior plano da 0.6.0 |
| `/planet leave` | Voltar à dimensão, posição e habilidades de origem |
| `/planet fly` / `/planet walk` | Ligar/desligar voo; voo mantém colisão com pedra |
| `/planet core` | Criar uma câmara iluminada e fechada em Y=-471 e acessá-la com voo |
| `/planet noclip true` | Explorar através de blocos usando spectator; guarda seu modo anterior |
| `/planet noclip false` | Voltar a uma superfície segura e restaurar o modo anterior |
| `/planet surface` | Voltar à superfície da coluna atual |
| `/planet info` | Posição, mapa, raio, fundo e opções |
| `/planet physics realistic_gravity true` | Gravidade variável com altitude |
| `/planet physics centrifugal false` | Desligar termo centrífugo |
| `/planet physics fallthrough false` | Desligar o deslocamento ao atravessar o fundo |

As opções de física aceitam `true`/`false` e são salvas por dimensão. Gravidade variável começa desligada; centrífugo ligado. **No planeta natural a travessia do fundo começa desligada.** Nos mapas planos mantém o padrão original ligado. `core` e `noclip true` desligam essa opção para a dimensão; desligar noclip não a religa automaticamente. Os comandos exigem permissão de operador/cheats.

WASD e mouse seguem os eixos planos. Espaço sobe e Shift desce durante voo. Bordas mantêm velocidade e orientação; a rotação relativa do pacote evita sobrescrever movimentos do mouse feitos durante a transmissão. Veículos transportam seus passageiros juntos.

## Planetas e geração

| Planeta | Raio visual | Mapa | X/Z | Superfície e fundo |
| --- | ---: | ---: | --- | --- |
| Pequeno plano | 32 | 224 × 224 | [-112,112) | Y=64; travessia em Y=32 |
| Maior plano | 256 | 1.632 × 1.632 | [-816,816) | Y=64; travessia em Y=-192 |
| Natural | 256 | 1.632 × 1.632 | [-816,816) | Relevo variável; nível de água Y=64; bedrock entre Y=-500 e -496 |

Altura de armazenamento: -512 a 1023. Tamanho horizontal: `ceil((R/16) × pi) × 32`, como no Spheretest.

O terreno natural usa ruído periódico com interpolação suave em todas as oitavas. Relevo, campos de cavernas, clima e estruturas se repetem exatamente em X/Z equivalentes. A seed do mundo influencia relevo e clima. Biomas: ocean, plains, forest, desert, taiga, snowy_plains e stony_peaks. Inclui areia, água/gelo, neve, árvores, vegetação, deepslate, minérios, lava profunda, câmaras com spawners/loot, galerias de mina e cabanas. Animais usam o mecanismo de spawn por bioma do Minecraft; o planeta natural tem ciclo de dia/noite e clima.

**É um gerador próprio periódico, não o gerador vanilla completo.** As estruturas são procedurais pequenas; não são todas as vilas, fortalezas e estruturas do Overworld. Isso evita usar ruído/decoração não periódicos e introduzir uma costura no mapa. Os mundos de teste usados no desenvolvimento ficam isolados; o mundo de jogo será gerado ao usar o comando na sua instância.

## Explorar o interior

Na 0.6.0, voo não atravessava pedra e a opção de fundo ligada deslocava você para outro lado ao descer. Use `core` para uma entrada segura e `noclip true` para atravessar terreno; use `noclip false` ou `surface` para sair.

A fórmula preservada é:

```text
d = distância horizontal do vértice à câmera
dy = altura do vértice menos altura da câmera
angle = d / R
radial = R × exp(dy / R)
shownXZ = directionXZ × radial × sin(angle)
shownY = radial × cos(angle) − R
```

Não há um centro geométrico finito em coordenadas planas nessa transformação: `radial=0` exigiria `dy→−∞`, e a projeção é relativa à câmera. A câmara profunda não altera a fórmula nem finge ser uma posição global no centro de uma esfera física. Ao ligar fallthrough, o comportamento original continua: meia volta em X, Y=fundo+1 e inversão da velocidade vertical; isso pode levar você para dentro de pedra.

## Bordas, renderização e física

Cada bloco e inventário possui um único endereço canônico. Chunks recebem tickets e envio por distância periódica; o envio não espera pela geração de um chunk no tick do servidor. O renderizador escolhe o deslocamento mais próximo entre nove representações, conserva o corte horizontal L/4 e usa modelos/fluidos reais do Minecraft.

Entidades, entidades de bloco, seleção, cracks e partículas acompanham a projeção. A mão em primeira pessoa continua normal. Transparência ordena seções e faces pela distância projetada e atualiza a ordenação ao mover a câmera. Sons têm envio por distância periódica e posições próximas no áudio, inclusive enquanto um som continua tocando através da travessia. Pathfinding usa alvos e chunks do lado conectado; controle de movimento, olhar e linha de visão consideram essa vizinhança. Ticks agendados de blocos/fluidos usam posições canônicas. Portais do Nether lembram o planeta de origem e procuram saídas também pelo lado conectado.

Não existe compensação de velocidade pelo tamanho visual dos blocos. A gravidade usa a base por entidade do Minecraft, com coeficientes e termo centrífugo do Spheretest. É uma demonstração dessa experiência, não uma esfera física perfeita nem uma simulação de órbitas.

## Verificação e limites

[Guia de testes](TESTAR-NO-MODRINTH.md) e [resultados](docs/TEST-RESULTS.md) distinguem execução real, testes de matemática, dados salvos e avaliação manual. O JAR final é testado como mod empacotado, incluindo cliente integrado e conexão TCP a um servidor dedicado separado.

O alcance de chunks limita a porção visível do planeta; não há malha orbital global. Aumentar alcance custa CPU, memória, rede e GPU. Transparência ainda tem os limites de ordenação de polígonos que se intersectam. A validação de IA não cobre individualmente cada mob, ataque especial e goal; a de veículos inclui barco controlado pelo jogador e passageiros no servidor. A validação de redstone inclui transmissão de energia e ticks através da borda, não todas as máquinas possíveis. O teste TCP é local; não mede latência de uma hospedagem na internet. Mods que substituem o renderizador/shaders exigem validação própria e não têm compatibilidade universal garantida.

## Compilar

```powershell
$env:JAVA_HOME = 'CAMINHO_DO_SEU_JDK_21'
.\gradlew.bat build
```

JAR: `build/libs/spaceblocks-0.7.0.jar`. Os harnesses ficam inativos em uso normal. Nunca habilite os flags do servidor de teste em uma hospedagem pública; o run de rede concede operador aos jogadores de teste e usa somente loopback.

Créditos: [CREDITS.md](CREDITS.md). Fórmulas, unidades, origem e histórico: [auditoria do Spheretest](docs/SPHERETEST-SOURCES.md). LGPL-2.1-or-later; licença do MDK preservada. Os vídeos são creditados; foram lidos código, histórico e transcrição, sem alegar que os vídeos inacessíveis foram assistidos.

Repositório: https://github.com/JonasHenriqueDev/spaceblocks-spheretest
