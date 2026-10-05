# Resultados — Space Blocks 0.7.0

Validação em Windows, Minecraft 1.21.1, NeoForge 21.1.255 e Java 21, em 05/10/2026. Foram usados mundos e processos de teste próprios; os saves do Modrinth não foram usados pelos harnesses.

## Artefato

`spaceblocks-0.7.0.jar`

SHA-256: `19A1AAA4E582B01F65A7BD379B1C687ADE70159F8DAF007B8087BE15A0559F9D`

O cliente empacotado verifica a origem das classes no JAR. Os runs de rede também usam `loadedMods=[]` com esse mesmo JAR. O teste TCP tem cliente e servidor em JVMs separadas, usando loopback, sem servidor integrado no cliente.

## Testes e dados

| Área | Resultado e alcance |
| --- | --- |
| Build e matemática | 11 testes JUnit aprovados: fórmula original, inversão, nove deslocamentos, voltas, gravidade/centrífugo; periodicidade suave do terreno, cavernas, clima e estruturas; diferença entre seeds |
| Cliente real empacotado | 34 verificações aprovadas: VBO/terreno, TNT/seleção, pacotes reais de mineração/construção, quatro bordas e diagonal com W, yaw, mineração profunda nos dois planos, opções, fundo, planeta natural, core/noclip/restauração, barco controlado e portal do Nether de ida/volta |
| Rede TCP | 14 verificações aprovadas: JVMs separadas, entrada/saída, movimento na borda, updates, energia e tick atrasado de lâmpada, mineração/construção por pacotes através da borda, natural/core/noclip/retorno seguro |
| Servidor | Baterias aprovadas nos dois planos, incluindo nove representações, chunks únicos, inventário, mineração/colisão, quatro voltas físicas por planeta, bordas/diagonal, velocidade/rotação, física, fundo, TNT e reabertura |
| IA e passageiros | Pathfinding por conexão curta, controle de movimento e linha de visão através da borda; barco com passageiro mantém vínculo e posição. O cliente também atravessou controlando um barco |
| Terreno natural | Amostra de 121 colunas: alturas Y=28..149, sete biomas, 12.142 amostras de ar de cavernas, 3.631 blocos de minério na amostra e 40 colunas com água/gelo em Y=64; alturas equivalentes nas bordas |
| Estruturas reais | Chunk gerado contém baú e entidade de bloco com tabela `minecraft:chests/simple_dungeon`. Inspeção offline dos arquivos Anvil confirmou galerias de madeira, loot e spawners de zombie; nenhum chunk com terreno fora do intervalo canônico |
| Salvamento | Servidor encerrado/reaberto, diamantes de verificação e baú com três diamantes preservados nos dois planos. Chunk natural e tabela de loot lidos após geração/salvamento. Cliente encerrou normalmente após os testes |
| Portal | Portal real construído pela API vanilla levou o jogador ao Nether; após sair, aguardar cooldown e entrar novamente, retornou à dimensão natural de origem |

As voltas completas foram físicas e automáticas com entidade; bordas/diagonal também foram percorridas pelo jogador com teclado. Capturas registram relevo, TNT, câmara iluminada e barco. Não se usa apenas screenshot ou exit code como prova: os marcadores de aprovação e arquivos de resultados foram conferidos.

## Mudanças que corrigem os limites anteriores

- O natural permite explorar até a bedrock profunda, sem o deslocamento automático do fundo por padrão. `core` abre acesso iluminado; `noclip` atravessa pedra e restaura o modo anterior em uma superfície segura.
- Terreno/clima/cavernas e estruturas são periódicos; não há decoração vanilla não periódica atravessando a costura. A dimensão natural é separada dos planos antigos.
- Chunk streaming não bloqueia o tick esperando geração. Veículos sincronizam passageiros e a referência de posição usada para validar pacotes.
- A rotação de travessia é enviada como deslocamento zero relativo ao cliente, preservando movimentos do mouse durante transmissão. Foi corrigida e retestada uma regressão encontrada nessa mudança.
- Transparência ordena seções e quads usando geometria projetada. Partículas consideram a representação próxima na admissão, envio, projeção e culling.
- Sons são enviados por distância periódica e canais ativos mantêm a posição próxima durante a travessia. Caminhos, movimento, olhar e linha de visão dos mobs usam alvos periódicos. Ticks de blocos/fluidos usam posição canônica.
- Portais do Nether guardam a origem do planeta e buscam saídas nas representações conectadas. O rastreamento confere a dimensão dona dos chunks e limpa o estado de envio em cada troca; o aviso de passageiros desconhecidos não reapareceu na rodada final.

## O que essa validação não significa

O gerador oferece componentes de um mundo natural, mas **não reproduz todo o worldgen vanilla**: suas cabanas, galerias e câmaras são próprias, sem todas as vilas/fortalezas do Overworld. A técnica do Spheretest não tem um centro geométrico finito em coordenadas planas nem uma posição global independente da câmera.

A direção e rotação foram verificadas automaticamente; percepção da câmera e desempenho em sessões prolongadas continuam avaliações de jogo. A reprodução sonora foi implementada e executada nos clientes, mas não foi certificada por uma avaliação auditiva humana. Não foram esgotados todos os ataques especiais de mobs, montarias/minecarts, circuitos/máquinas de redstone ou casos de polígonos transparentes que se intersectam.

A rede foi testada em TCP local: não foram medidas latência/perda de pacotes de hospedagem na internet. O conjunto validado contém Space Blocks e NeoForge. Compatibilidade com todos os mods/renderizadores/shaderpacks não pode ser garantida sem um conjunto específico; não foram instalados outros mods na instância do usuário.

As capturas finais do mundo novo usam o gerador plano sem decoração antiga e a dimensão natural com os recursos novos. A leitura de código/histórico/transcrição do Spheretest continua documentada em [SPHERETEST-SOURCES.md](SPHERETEST-SOURCES.md); não se afirma ter assistido aos vídeos inacessíveis. Os resultados históricos da 0.6.0 estão em [TEST-RESULTS-0.6.md](TEST-RESULTS-0.6.md).

## Instalação e recuperação

O backup 0.6.0 teve seu JAR e 259 arquivos de saves conferidos por SHA-256 antes da instalação. O JAR instalado deve ter o hash acima e ser a única versão Space Blocks na pasta `mods`. Os backups locais e saves não são publicados. A instância normal pelo Modrinth App não foi aberta como parte dos harnesses; o JAR foi executado nos clientes isolados descritos acima.
