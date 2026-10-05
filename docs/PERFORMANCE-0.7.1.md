# Desempenho e regressões — Space Blocks 0.7.1

Windows, Minecraft 1.21.1, NeoForge 21.1.255 e Java 21, em 05/10/2026. O teste usou o JAR empacotado em mundos e processos próprios. Saves da instância Modrinth não foram alterados pelos testes.

SHA-256 de `spaceblocks-0.7.1.jar`: `4FA21CD3120C98B89AAD8D5AFE6CAC9EAF7246363B7312B29BE92C5541A498F0`.

## Problema confirmado

O log da instância apresentou um atraso de 21,5 segundos. A 0.7.0 calculava ruído das cavernas em cada bloco até a bedrock, montava malhas na thread de renderização e impunha um limite de 2.048 seções, independentemente do conjunto visível. Com alcance 12, o conjunto necessário pode ultrapassar esse limite; expulsar malhas ainda necessárias produz recompilação contínua e terreno ausente. A validação funcional anterior usava alcance 5 e não cobria essa carga.

## Correções

- Interpolação horizontal das cavernas reutilizada por coluna; comparação com a fórmula anterior em todas as alturas de 128 colunas, incluindo bordas, sem divergências. Árvores avaliadas uma vez por coluna.
- Preenchimento assíncrono no executor de geração, com aquisição/liberação das seções e escrita sem adquirir o mesmo lock novamente.
- Até quatro tickets novos por tick e 16 solicitações não prontas. Envio de até seis chunks por jogador por tick, priorizando os próximos; nenhum `getChunk` bloqueante no loop de streaming.
- Malhas montadas em dois workers, com snapshots de regiões e até quatro tarefas em andamento. Upload/fechamento de VBO na thread de renderização; resultados de uma dimensão antiga são descartados.
- Superfície priorizada acima do chão; proximidade priorizada no subterrâneo. Faixa vertical limitada ao alcance de renderização, mantendo também o chão quando a câmera está em altitude. Uma regressão intermediária do limite vertical foi encontrada pelo teste de voo e corrigida antes da instalação. Blocos opacos totalmente cercados não emitem faces e evitam montagem de modelos.
- Ordenação de transparência considera apenas seções com malha dessa camada; prioridade de compilação evita alocações de vetores nas comparações.
- Cache mantém o conjunto atual necessário, sem o limite arbitrário de 2.048. Chunks recebidos invalidam vizinhos e tarefas em andamento para atualizar faces/iluminação, inclusive nas costuras.

## Medição de carga

`runPeriodicPerformanceClient` usa um save isolado copiado sem a dimensão natural. Distância de renderização **12**, simulação **12**, com opções transmitidas ao servidor integrado. A região testada tem 523 chunks dentro do alcance circular admitido pelo renderizador; não representa o planeta inteiro.

| Medida | Resultado |
| --- | --- |
| Primeira superfície com malha pronta | 7,94 s |
| Todos os chunks previstos recebidos | Até a amostra de aproximadamente 11 s |
| Todas as superfícies previstas prontas | Até a amostra de aproximadamente 41 s |
| Estabilização: toda a fila de malhas zerada por 40 ticks | 135,31 s |
| Superfícies presentes ao final | 523 de 523 |
| Seções mantidas ao final | 6.720 |
| Fila restante | 0 |

A superfície permaneceu completa enquanto o cache cresceu além de 2.048. O critério verifica malhas prontas por chunk e fila vazia; não se baseia apenas em `drawn > 0`. Screenshot registrado após o critério final. O mundo foi salvo e o processo encerrado normalmente.

Houve **uma pausa de cerca de 2,04 s na entrada inicial**, que ainda prepara o chunk de chegada. A montagem subterrânea continuou em segundo plano após a superfície ficar pronta. Esses tempos são dessa máquina/execução, não promessa de FPS ou carregamento instantâneo em qualquer computador. Não foi feito benchmark completo da versão anterior sob a mesma carga; não se declara um fator de aceleração para o jogo inteiro.

Benchmark separado do ruído de cavernas em 256 colunas, após aquecimento: original 27,66 ms, otimizado 5,55 ms, mesmas 22.460 amostras de cavernas positivas. Cerca de 5× neste cálculo específico, incluindo preparação das colunas; não é um benchmark de worldgen completo.

## Regressões do artefato

- 12 testes JUnit: sete de matemática/física e cinco de terreno, incluindo equivalência das cavernas otimizadas.
- Bateria dedicada dos dois planos: armazenamento canônico, bordas/diagonal, quatro voltas físicas, colisões, inventário, mineração profunda, física/fundo e persistência após reabertura.
- Cliente real: 34 verificações, incluindo mineração/construção por pacotes, controle/orientação, terreno natural, câmara/noclip, barco e portal do Nether de ida/volta.
- Cliente e servidor separados em TCP: 14 verificações, incluindo updates, redstone/tick, mineração/construção na costura, acesso profundo e retorno seguro.

Os resultados funcionais históricos e limites mais gerais continuam em [TEST-RESULTS.md](TEST-RESULTS.md). Compatibilidade com outros renderizadores/modpacks, desempenho prolongado e rede na internet não foram certificados por esta rodada.

## Instalação e recuperação

O código, Git e JAR 0.7.0 foram copiados para `C:\Dev\minecraft-space-mod-backup-0.7.0-before-performance-20261005`; o JAR foi conferido por SHA-256 antes das mudanças. A instalação mantém somente `spaceblocks-0.7.1.jar` na pasta mods da instância NeoForge 1.21.1. O hash instalado e o publicado são conferidos contra o artefato testado. Não foram apagados mundos ou construções do usuário. A dimensão existente é reutilizada; a otimização não exige regeneração.
