# Space Blocks 0.8.2 — proporção, túnel marcado e HUD

Entrega de 5 de outubro de 2026. Minecraft Java 1.21.1, NeoForge 21.1.255 e Java 21, Windows.

## Implementado

- Raio efetivo = largura real do mapa / (2*pi), em todos os planetas fixos/gerados e saves existentes. Terreno, água, entidades, partículas, seleção/mineração, atlas Spheretest, gravidade variável e satélite compartilham o raio. O parâmetro inteiro de geração continua selecionando a largura arredondada por chunks; `/planet info` mostra ambos. Blocos e limites canônicos não foram migrados.
- `/planet tunnel axis`: dois poços físicos de 5x5, interior livre 3x3, vidro ciano na entrada/magenta na saída e luz nos cantos a cada 8 blocos. Construção limitada por tick, com tickets liberados ao terminar. Substitui blocos somente quando executado; não modifica saves na instalação.
- HUD com globo esquemático colorido, marcador vivo, bússola de oito direções, coordenadas e raio. Rotação acompanha a posição com amortecimento. Amostras de chunks carregados mostram cores da superfície, outras usam previsão. Atualização de dados a cada 30 segundos, sem gerar chunks. Até 576 triângulos por frame.
- `/planet hud` local, sem operador, e Mods → Space Blocks → Config. Tamanho 64..192 unidades da GUI, quatro cantos, margens e opções de câmera. Persistência por instalação em config/spaceblocks-hud.properties; não altera opções de outros jogadores.
- A travessia aciona uma rotação visual temporária suave de 0 a 180 graus e de volta a 0, padrão 1200 ms, ajustável/desativável. O marcador do atlas também muda de lado com rotação suave. Mouse e controles continuam planos. A mudança de posição e inversão de velocidade continuam no tick do servidor.
- Pedido manual de mapa tem prioridade sobre refresh passivo pendente. Reaproveitamento de snapshot recente evita perder abertura durante cooldown. Pedidos passivos não escrevem mensagens periódicas no chat. Protocolo 8.2 exige clientes/servidor da mesma versão.

## Testado

| Execução | Resultado |
| --- | --- |
| build/JUnit | 25 testes, sem falhas; verificação de meia largura = pi para todos os 993 raios inteiros permitidos |
| JAR final / runPackagedHudClient | 18 verificações: atlas passivo, globo/marcador, túnel real/anéis/centro livre, comando cliente, oito controles, botões tamanho/canto, escrita de preferências, travessia, evento de câmera com rotação perto de 180 graus, saída sem colisão e retorno ao mundo padrão |
| JAR final / runPackagedBottomClient | 25 verificações: mineração real da saída em sobrevivência, seleção/alcance, apoio fechado, travessia em small e natural, bedrock padrão e armazenamento inferior preservados |
| JAR final / runPackagedLabClient -PverifyPersistence=true | 27 verificações: sonda salva e retomada, resposta ao centrífugo, túnel, planetas separados/seed/raio, construção na reabertura, atlas e teleporte do catálogo |
| Servidor dedicado empacotado | 3 grupos: bordas/laps, colisões, fundo, fórmulas da física e reabertura nos planos; geração natural |
| Cliente TCP + servidor separado | 22 verificações: entrada, bordas, edição, entidades, painel, catálogo, planeta gerado e retorno |

Servidor/TCP foram executados no candidato 0.8.2 anterior à correção visual do título do menu; depois dessa correção e da instrumentação adicional do harness, o JAR final passou HUD, mineração pelo fundo e laboratório. A lógica de rede/física não mudou entre essas execuções. O teste TCP é de um cliente; a verificação de dois jogadores simultâneos continua sendo histórica da 0.8.0, não uma nova medição de capacidade da 0.8.2.

Screenshots locais do HUD em dois cantos e do menu foram inspecionadas. O teste mede o evento real de câmera; não representa uma travessia física contínua pelo centro. Não foram feitos benchmark de FPS/latência de internet nem teste de compatibilidade com renderizadores de terceiros nesta versão.

## Limites e diferenças do original

A calibração altera o parâmetro de raio original por pedido do usuário. Mantém as equações exponenciais e a topologia periódica. Corrige o desvio angular no corte horizontal alinhado; a aparência depende da câmera e qualquer linha arbitrária não tem garantia de antípoda global. A exponencial ainda não alcança um centro em profundidade finita. O túnel conecta dois poços pelo fundo, com teleporte e inversão Y; não há esfera física perfeita, conservação exata de energia ou oscilação eterna.

O HUD é um atlas de orientação, não um segundo renderizador global. Não mostra cavernas/todas as entidades/construções. Polos da carta longitude/latitude têm distorção e a borda Z é uma costura no esquema. N=-Z, S=+Z, E=+X, W=-X; essas direções não são polos físicos. F1 oculta o HUD. Cantos inferiores podem sobrepor chat/hotbar, ajustáveis no menu. A animação pode ser desligada; não muda a direção física do corpo.

Sondas antigas guardam sua velocidade anterior; relançar calcula o equilíbrio com o novo raio. A atualização não substitui sondas nem constrói túneis automaticamente no save do usuário.

## Artefato e recuperação

SHA-256 do JAR final:

```
867A375916066B65EE0A0A4C1601A0483E50260962A762B8B4429DCD27EC3376
```

Backup anterior: C:\Dev\minecraft-space-mod-backup-0.8.1-before-axis-20261005, código, Git e JAR 0.8.1 conferidos. Saves do usuário não foram alterados por esta instalação. A sessão de Minecraft do usuário não foi encerrada.

Créditos e licença: [SPHERETEST-SOURCES.md](SPHERETEST-SOURCES.md), Jeija/Spheretest, LGPL-2.1-or-later. HUD/menu e adaptação de câmera são código novo; sem código de Immersive Portals.
