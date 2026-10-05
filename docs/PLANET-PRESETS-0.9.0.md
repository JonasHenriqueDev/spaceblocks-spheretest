# Planetas por tipo e orientação — 0.9.0

## Como usar

Abra `/planet planets`, escolha `Type`, raio solicitado **32..128**, seed opcional e `Ice poles`, e clique em `Generate`. A seed em branco gera um valor aleatório, salvo no catálogo. `/planet info` mostra os parâmetros. `/planet map` abre o atlas 3D; `/planet hud` configura o indicador e a transição da câmera.

```text
/planet generate sandbox 32 flat
/planet generate earthhome 128 earth 12345 true
/planet generate dunes 64 desert 23456 false
/planet generate forestmoon 64 jungle 34567 false
/planet generate fungi 32 mushroom
/planet generate soil 64 dirt
/planet generate rock 64 stone 45678 true
/planet generate inferno 64 nether
/planet enter earthhome
/planet leave
```

Sintaxe: `/planet generate <name> <radius> <type> [seed] [ice_poles]`. Para especificar `ice_poles`, forneça também uma seed. Sem tipo, a geração usa `earth` com polos frios. Nomes aceitam letras minúsculas, números, `_` e `-`, até 24 caracteres. Há 16 posições de planetas gerados por save, além dos quatro mapas internos. Todos os comandos de alteração do mundo exigem operador/cheats.

`earth` usa vários biomas nativos; `jungle` e `mushroom` usam seus biomas nativos; `desert`, `dirt` e `stone` também adaptam a camada superficial para um planeta seco. `nether` usa ruído e cinco biomas do Nether, com teto aberto. `flat` é terreno uniforme em Y=64, sem decoração, minérios ou cavernas. Polos frios são opcionais: padrão ligado em `earth`/`dirt`, desligado nos demais e proibido em `nether`.

## O que mudou

O gerador chama `NoiseBasedChunkGenerator` do Minecraft instalado: densidade, aquíferos, superfície, carvers, vegetação, veios de minério e estruturas. Cada planeta tem seu próprio `RandomState`, seed de decoração e estado de estruturas. A densidade combina quatro posições do ruído com pesos suaves periódicos em X/Z. Biomas únicos dispensam o cálculo de distribuição de biomas a cada amostra.

Os tipos recebem seis tentativas adicionais de veios por chunk: deserto ouro/cobre, jungle cobre/ferro, cogumelos carvão/ferro, terra carvão/cobre, pedra ferro/redstone e Nether quartzo/ouro. A distribuição vanilla continua existindo; abundância é uma preferência, não exclusividade nem uma porcentagem garantida. `earth` mantém os minérios nativos; `flat` não os gera.

A travessia do fundo reflete a inclinação lógica do jogador (`pitch -> -pitch`) junto com a velocidade vertical. A direção horizontal é preservada. O cliente usa a inclinação anterior ao pacote para conservar o movimento do mouse, e a transição visual começa na orientação que a câmera mostrava. As bordas horizontais não invertem o olhar.

## Limites reais

- A seed não reproduz o mapa vanilla idêntico: densidade periódica, seleção de biomas, superfície temática e fundação profunda são adaptações do mod. Árvores e outras features são geradas pelo Minecraft, sem as árvores geométricas do gerador legado.
- Densidade periódica não torna automaticamente periódicos os carvers, estruturas e detalhes de decoração. Eles podem ficar cortados/descontínuos na emenda. Conectar essas etapas integralmente permanece pendente.
- Cavernas nativas ocupam principalmente o intervalo vertical vanilla. A fundação até Y=-500 é sólida e minerável, com bedrock somente em Y=-500/-499, abaixo da passagem Y=-496. Não é uma segunda geração completa de cavernas em toda essa profundidade. Os mapas pequenos continuam usando a sua conexão pelo fundo. Para atravessar um planeta novo, habilite `fallthrough` e abra também a saída conectada.
- O Nether é um tipo de terreno dentro da dimensão de planeta: não reproduz todas as regras, iluminação ou ambiente da dimensão Nether. Atmosferas e iluminação por planeta ficam para depois.
- Gelo polar é uma faixa de bioma nos extremos Z do mapa e uma indicação no atlas. A projeção continua relativa à câmera, sobre um plano periódico; não há polos físicos globais ou um centro esférico real.
- O atlas/HUD usa previsão de cores onde não há chunks carregados. Não representa exatamente todo o relevo nativo desconhecido. Não gera chunks só para montar a imagem.
- O limite 128 refere-se ao raio solicitado. O mapa é arredondado por chunks: 128 produz largura 832 e raio efetivo aproximadamente 132,42; 32 produz largura 224 e raio efetivo 35,65.
- Saves e mapas internos legados não são regenerados. `/planet natural`, `/planet flat`, `/planet small` e `/planet lab` continuam acessando esses mapas. Crie um novo nome para obter o novo gerador.
- A conexão do fundo continua artificial, com deslocamento de meia largura em X, inversão vertical e suporte contra saídas bloqueadas. Não é um túnel euclidiano através de uma esfera física. A rotação da câmera é uma transição temporária configurável.

## Validação

Relatório de execução em Minecraft 1.21.1, NeoForge 21.1.255 e Java 21, em 05/10/2026. Os testes de cliente/servidor carregam o JAR empacotado, sem classes do mod inseridas pelo ambiente de desenvolvimento.

Build e **29 testes JUnit**, sem falhas. Geração nova dos oito tipos: **62 verificações** no servidor dedicado, incluindo chunks FULL, faixa polar, seed diferente, fechamento da densidade em X/Z, teto de raio 128, plano de altura constante e blocos mineráveis na passagem do fundo. As amostras de sete chunks centrais não planos contêm 15.279 blocos de ar subterrâneo, 2.663 blocos de minério e 136 troncos; são amostras de teste, não taxas de abundância de todos os planetas.

Cada teste de geração solicita três chunks FULL — centro, polo e emenda — e as dependências exigidas pelo Minecraft. A execução isolada demorou aproximadamente 0,5..7 segundos por tipo depois do início do servidor, não corresponde à pré-geração do planeta inteiro. Em uma execução concorrente anterior, o flush explícito de todos os mundos dentro do tick do harness disparou o watchdog; o harness agora usa o encerramento normal do servidor para salvar. O watchdog permanece habilitado. Não é prometida geração instantânea nem desempenho medido de um mundo inteiro.

Reabertura no servidor dedicado: **78 verificações**, incluindo preservação de tipo, polos e uma construção em cada um dos oito planetas, nova leitura dos chunks e superfície plana preservada. Encerramento e salvamento normais concluídos, sem crash.

Cliente integrado: **43 verificações** de seleção/mineração real por pacotes, saída bloqueada e aberta, travessia sem sufocamento, inclinação do olhar de baixo para cima, alcance da visão pelo fundo, obstrução/descarregamento e desativação/reativação do shader de GPU. O teste da inclinação reposiciona o jogador olhando para baixo antes de provocar uma única travessia, para não confundir duas inversões durante a mineração.

Cliente TCP e servidor dedicado separado: **32 verificações** de dimensões, borda periódica, construção/mineração, redstone e ticks na emenda, catálogo/teleporte, tipo/seed/polos no pacote, controles do painel dentro da GUI, modo flat com raio 128 e bioma/terreno jungle recebido e renderizado. O cenário TCP usa um cliente; os testes históricos com dois clientes não contam como uma nova execução da 0.9.0. Todos esses cenários concluíram e encerraram seus processos normalmente, usando o mesmo SHA-256 abaixo.

Não foram medidos internet com latência, pré-geração completa, todas as seeds, todos os mobs ou compatibilidade com outros renderizadores. Inclinações intermediárias, movimento rápido do mouse durante a transição, aparência dos oito tipos por toda a extensão e detalhes das emendas têm cenários adicionais no [guia de testes](../TESTAR-NO-MODRINTH.md); essa lista manual não deve ser interpretada como testes já executados.

SHA-256 do JAR de entrega: `F53708DCF320AC88406FF4A6652934C9B1BCE10D382B2873DB382A65205A389D`.

## Origem e futuro

Projeção, mapa periódico e opções de física derivados de Jeija/Spheretest, com créditos e licenças em [CREDITS](../CREDITS.md) e [rastreamento da adaptação](SPHERETEST-SOURCES.md). A geração nativa chama a implementação fornecida pelo Minecraft instalado; seus fontes não são redistribuídos no projeto.

Próximos passos: decoração/carvers/estruturas que também respeitem as emendas, cavernas na fundação profunda, atlas com amostragem de relevo nativo e atmosferas por tipo. Compatibilidade com outros renderizadores e desempenho de geração de planetas completos precisam de medições próprias.
