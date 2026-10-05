# Space Blocks 0.8.0 — Spheretest

Minecraft Java **1.21.1**, NeoForge **21.1.255**, **Java 21**. Um mapa plano quadrado finito, com bordas opostas conectadas, desenhado com a projeção exponencial relativa à câmera de **Jeija / Spheretest**. Armazenamento, colisões e gravidade permanecem planos.

## Jogar

Instale apenas `spaceblocks-0.8.0.jar` e habilite comandos no mundo. Os mapas e construções existentes são preservados.

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
| `/planet lab` | Planeta de testes separado, com terreno/cavernas, plataformas, torres de TNT e satélite |
| `/planet planets` | Catálogo: listar planetas, gerar novos e teleportar por X/Z/Y ou superfície |
| `/planet generate aurora 64` | Criar outro planeta natural com raio 64 e seed aleatória |
| `/planet generate aurora 64 12345` | Criar com seed explícita |
| `/planet enter aurora` | Voltar ao planeta salvo pelo nome |
| `/planet satellite launch [altitude] [speed]` | Lançar uma sonda para testar órbita; altura padrão 128 acima de Y=64; fator de velocidade padrão 1 |
| `/planet satellite info` / `remove` | Consultar trajetória/voltas ou remover sua sonda; no laboratório, controlar a sonda comum |
| `/planet map` | Painel 3D: arrastar para girar, roda para zoom, tamanho, raio, fundo e cobertura |
| `/planet tunnel create` | Abrir dois poços conectados, no X/Z atual e meia volta em X; substitui blocos nas duas colunas de 5×5 |
| `/planet tunnel drop` | Cair pelo túnel criado, atravessar o fundo, subir do outro lado e voltar a cair |
| `/planet physics realistic_gravity true` | Gravidade variável com altitude |
| `/planet physics centrifugal false` | Desligar termo centrífugo |
| `/planet physics fallthrough false` | Desligar o deslocamento ao atravessar o fundo |
| `/planet physics air_drag false` | Remover arrasto vertical no ar de jogadores/mobs, para testar a oscilação |

As opções de física aceitam `true`/`false` e são salvas por dimensão. Gravidade variável começa desligada; centrífugo ligado. **No planeta natural a travessia do fundo começa desligada.** Nos mapas planos mantém o padrão original ligado. `core` e `noclip true` desligam essa opção para a dimensão; desligar noclip não a religa automaticamente. Os comandos exigem permissão de operador/cheats.

WASD e mouse seguem os eixos planos. Espaço sobe e Shift desce durante voo. Bordas mantêm velocidade e orientação; a rotação relativa do pacote evita sobrescrever movimentos do mouse feitos durante a transmissão. Veículos transportam seus passageiros juntos.

## Planetas e geração

| Planeta | Raio visual | Mapa | X/Z | Superfície e fundo |
| --- | ---: | ---: | --- | --- |
| Pequeno plano | 32 | 224 × 224 | [-112,112) | Y=64; travessia em Y=32 |
| Maior plano | 256 | 1.632 × 1.632 | [-816,816) | Y=64; travessia em Y=-192 |
| Natural | 256 | 1.632 × 1.632 | [-816,816) | Relevo variável; nível de água Y=64; bedrock entre Y=-500 e -496 |
| Laboratório natural | 128 | 832 × 832 | [-416,416) | Terreno/cavernas; plataforma em Y=180; fundo Y=-496 |
| Gerado pelo jogador | 32–1.024 | Calculado a partir do raio | Bordas periódicas | Seed própria; terreno/cavernas; fundo Y=-496 |

Altura de armazenamento: -512 a 1023. Tamanho horizontal: `ceil((R/16) × pi) × 32`, como no Spheretest.

O terreno natural usa ruído periódico com interpolação suave em todas as oitavas. Relevo, campos de cavernas, clima e estruturas se repetem exatamente em X/Z equivalentes. A seed do mundo influencia relevo e clima. Biomas: ocean, plains, forest, desert, taiga, snowy_plains e stony_peaks. Inclui areia, água/gelo, neve, árvores, vegetação, deepslate, minérios, lava profunda, câmaras com spawners/loot, galerias de mina e cabanas. Animais usam o mecanismo de spawn por bioma do Minecraft; o planeta natural tem ciclo de dia/noite e clima.

**É um gerador próprio periódico, não o gerador vanilla completo.** As estruturas são procedurais pequenas; não são todas as vilas, fortalezas e estruturas do Overworld. Isso evita usar ruído/decoração não periódicos e introduzir uma costura no mapa. Os mundos de teste usados no desenvolvimento ficam isolados; o mundo de jogo será gerado ao usar o comando na sua instância.

`/planet natural` entra sempre no mesmo planeta salvo; não o regenera. Para terreno novo, use `generate` com outro nome, pelo comando ou pelo painel `Planets`. Sem seed explícita, cada criação recebe uma seed aleatória. Mesmo raio/seed reproduz o terreno; nomes diferentes preservam armazenamento independente. Raio permitido: 32 a 1024. O tamanho horizontal é mostrado antes/depois da criação e no atlas; por exemplo, raio 64 corresponde a 416×416 blocos. Há até **16 planetas gerados por save**, além dos quatro mapas fixos. Essa versão não apaga nem recicla planetas ocupados.

No painel, selecione um planeta, informe X/Z e `surface` ou um Y numérico, e use `Teleport` ou `Enter + map`. X/Z são normalizados nas bordas. Uma altura explícita pode ficar dentro de pedra: use voo/noclip quando necessário. Os planetas pertencem ao mundo/servidor e ficam disponíveis aos demais operadores. Clientes e servidor precisam da mesma versão do mod.

## Explorar o interior

A 0.8.0 acrescenta o túnel demonstrável: entre no planeta, use `/planet tunnel create`, espere a mensagem `Tunnel ready` e use `/planet tunnel drop`. Ambos os poços ficam livres até abaixo das cinco camadas de bedrock. A queda usa gravidade variável, sem arrasto vertical e sem centrífugo; essas opções são salvas para a dimensão. `/planet surface` sai do poço para a superfície vizinha. Voo, líquidos e elytra conservam suas regras próprias.

Você também pode minerar seu próprio túnel. Ligue `/planet physics fallthrough true`: a bedrock do planeta passa a ser minerável. Abra também a saída correspondente meia volta em X, mantendo Z; a travessia não remove automaticamente pedra do lado oposto. Um poço de apenas um lado pode terminar contra blocos na outra saída. A bedrock do mundo padrão continua inquebrável.

Esse comportamento reproduz `content_sao.cpp` do Spheretest: deslocamento de meia circunferência em X, reposicionamento no fundo e inversão de velocidade Y. Não precisa de um modo novo de esfera física. A oscilação conserva as limitações desse reposicionamento e da integração por ticks: não há conservação perfeita de energia, polos físicos globais ou núcleo esférico real.

## Painel 3D

`/planet map` abre o atlas dentro do Minecraft. Arraste o planeta, use a roda para zoom, `Center` para centralizar o marcador do jogador, `Reset` para restaurar a visão e `Refresh` para atualizar. `Loaded` distingue amostras confirmadas das previstas. O painel não pausa o jogo.

`Globe atlas` mostra todo o mapa como uma esfera esquemática de longitude/latitude, com relevo comprimido para leitura. `Spheretest view` mostra o hemisfério próximo com a fórmula exponencial original, câmera virtual na altura 64 e referência X/Z do jogador. A representação de todo o mapa no atlas não substitui a projeção do mundo.

O botão `Planets` abre o catálogo de todos os mapas salvos. Cada planeta pode ser selecionado para entrar e abrir sua visualização 3D. O catálogo não representa posições astronômicas globais entre dimensões.

São 96×96 amostras de altura/cor da superfície, sem carregar ou gerar chunks adicionais. Chunks presentes fornecem dados reais; regiões ausentes usam previsão do gerador e podem omitir construções salvas fora do alcance. Edifícios menores que a amostragem e interiores/cavernas não aparecem individualmente. O painel exibe tamanho do mapa, raio de projeção, fundo, posição de referência e proporção de amostras confirmadas.

## Laboratório e satélite

`/planet lab` prepara o laboratório uma vez e conserva alterações posteriores. Há um deck em Y=180, baú e blocos de seleção em X=-5, torres de TNT em X=16/24/32 e plataformas que atravessam X/Z=±416. O terreno natural e cavernas continuam abaixo. Use `tunnel create` no local escolhido, espere a conclusão e depois `tunnel drop`; as duas colunas de 5×5 são substituídas. `surface` retorna a uma coluna vizinha; em oceanos a saída pode ser na água.

A sonda é um objeto dourado com elytra, sem colisão, atualizado no servidor. Usa gravidade variável e centrífugo do Spheretest e mantém velocidade horizontal plana constante para ensaiar a trajetória. Velocidade inicial: `sqrt(g × exponentialHeight / 2)`. O lançamento habilita essas duas opções na dimensão. Com fator 1, a condição inicial equilibra os termos; compare fatores 0.8/1.2 e ligue/desligue `centrifugal` para observar mudanças. `info` mostra posição, velocidades e voltas. A sonda carrega somente chunks locais à trajetória e mantém um índice salvo para retomar após reabrir; não pré-gera o mapa inteiro.

Essa sonda é uma ferramenta de teste, não prova de órbitas realistas para qualquer entidade vanilla. Atrito, colisões, ticks, passagem artificial pelo fundo e a projeção relativa à câmera continuam limitando a simulação. Quedas muito altas ou ganho numérico de energia podem escapar, em vez de oscilar indefinidamente.

## Projeção do interior

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

[Guia de testes](TESTAR-NO-MODRINTH.md) e [resultados](docs/ATLAS-LAB-0.8.0.md) distinguem execução real, testes de matemática, dados salvos e avaliação manual. O JAR é testado como mod empacotado, incluindo cliente integrado e conexão TCP a um servidor dedicado separado.

O alcance de chunks limita a porção visível do planeta; não há malha orbital global. Aumentar alcance custa CPU, memória, rede e GPU. Transparência ainda tem os limites de ordenação de polígonos que se intersectam. A validação de IA não cobre individualmente cada mob, ataque especial e goal; a de veículos inclui barco controlado pelo jogador e passageiros no servidor. A validação de redstone inclui transmissão de energia e ticks através da borda, não todas as máquinas possíveis. O teste TCP é local; não mede latência de uma hospedagem na internet. Mods que substituem o renderizador/shaders exigem validação própria e não têm compatibilidade universal garantida.

## Otimização 0.7.1

A geração reaproveita a interpolação horizontal do ruído das cavernas por coluna, mantendo os mesmos resultados, e calcula árvores uma vez por coluna. O preenchimento roda no executor de geração, com as seções protegidas durante a escrita.

O streaming pede até quatro tickets novos por tick e mantém até 16 solicitações ainda não prontas. Chunks próximos têm prioridade; o envio continua limitado a seis por tick. Isso distribui a carga inicial sem bloquear o tick para esperar cada chunk.

A montagem de malhas usa dois workers, no máximo quatro tarefas em andamento, com snapshots de regiões como no renderizador vanilla. O upload de VBO permanece na thread de renderização. Superfície tem prioridade quando a câmera está acima do chão; blocos opacos completamente cercados não passam pela montagem de modelos. A câmera subterrânea mantém prioridade por proximidade. A faixa vertical acompanha a distância de renderização e mantém o chão no alcance quando a câmera sobe, em vez de montar toda a coluna até Y=-500. A ordenação de transparência inclui apenas malhas que possuem essa camada.

O cache mantém as seções atualmente necessárias e libera as que saem do alcance. Não existe mais o limite fixo de 2.048 que podia expulsar seções ainda visíveis. A chegada de chunks invalida também as faces e iluminação dos vizinhos, inclusive nas bordas periódicas. Geração inicial e voo rápido ainda podem exigir carregamento progressivo; isso não é pré-geração de todo o planeta.

A validação específica está em [PERFORMANCE-0.7.1.md](docs/PERFORMANCE-0.7.1.md). Os resultados funcionais históricos da 0.7.0 permanecem no relatório anterior.

## Compilar

```powershell
$env:JAVA_HOME = 'CAMINHO_DO_SEU_JDK_21'
.\gradlew.bat build
```

JAR: `build/libs/spaceblocks-0.8.0.jar`. Os harnesses ficam inativos em uso normal. Nunca habilite os flags do servidor de teste em uma hospedagem pública; o run de rede concede operador aos jogadores de teste e usa somente loopback.

Créditos: [CREDITS.md](CREDITS.md). Fórmulas, unidades, origem e histórico: [auditoria do Spheretest](docs/SPHERETEST-SOURCES.md). LGPL-2.1-or-later; licença do MDK preservada. Os vídeos são creditados; foram lidos código, histórico e transcrição, sem alegar que os vídeos inacessíveis foram assistidos.

Repositório: https://github.com/JonasHenriqueDev/spaceblocks-spheretest
