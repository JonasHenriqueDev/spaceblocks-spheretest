# Space Blocks 0.8.1 — túnel manual e visão pelo fundo

Correção verificada em Windows, Minecraft Java 1.21.1, NeoForge 21.1.255 e Java 21, em 5 de outubro de 2026.

## Problema e comportamento novo

Na 0.8.0, a travessia pelo fundo podia colocar o jogador na pedra da saída oposta. O renderizador e a seleção não mostravam essa saída antes da travessia. No natural, camadas de bedrock abaixo do limite renderizado ainda podiam formar um piso físico invisível.

A 0.8.1 acrescenta uma visão local do terreno oposto quando a câmera está a até 16 blocos do fundo e `fallthrough` está ligado. A malha do chunk canônico é refletida pelo plano do fundo antes de receber a mesma projeção exponencial do Spheretest. O raio de seleção continua pelo outro lado e os pacotes normais de mineração apontam para o bloco real, com alcance validado no servidor. Os chunks da saída recebem streaming e prioridade de montagem local.

O jogador fica apoiado enquanto a saída ainda não tem espaço para seu corpo. Depois de minerá-la, pode atravessar sem ser colocado dentro de pedra. Camadas armazenadas abaixo do fundo ficam fora da colisão com a travessia habilitada; nenhum bloco do save é apagado por essa correção. A bedrock do planeta aceita picareta nessa opção; a bedrock do mundo padrão permanece inquebrável. A visão adicional combina a iluminação da origem com a luz local do observador: uma luz no poço ajuda a enxergar a saída, mas cavernas sem luz continuam escuras.

## A proporção do pequeno

O mapa pequeno tem 224×224 blocos e raio 32. A conexão usa meia largura do mapa, 112 blocos em X. A meia circunferência geométrica seria pi×32, cerca de 100,53 blocos; a diferença corresponde a aproximadamente 20,5° na projeção. Isso vem do arredondamento por chunks do código original.

A fórmula e o arredondamento foram mantidos. A visão local alinha as duas metades do túnel, mas a técnica não fornece polos físicos globais ou uma esfera exata. Alterar somente o raio angular esconderia o desvio às custas da fórmula e da escala visual originais.

## Testar no seu save

1. Reabra Minecraft com apenas `spaceblocks-0.8.1.jar`. Clientes e servidor precisam da 0.8.1; o protocolo foi atualizado para impedir a mistura com a 0.8.0.
2. Entre em `/planet small` e habilite `/planet physics fallthrough true`.
3. Cave até perto de Y=32 e continue mirando para baixo. Os blocos da outra saída devem aparecer e ser mineráveis. Use iluminação no poço quando necessário.
4. Libere espaço suficiente para o jogador. Desligue o voo com `/planet walk` para cair; após atravessar, olhe para cima para continuar escavando a subida do outro poço. Use `/planet fly` se quiser escavar com controle da altura.
5. Repita no natural: fundo Y=-496. Não é necessário remover as quatro camadas inferiores de bedrock para atravessar com essa opção ligada.
6. Compare `fallthrough false`: a visão adicional desaparece e a travessia é desligada. Não confunda com `noclip true`, que também desliga a travessia por padrão.

O túnel automático existente continua disponível com `/planet tunnel create` e `/planet tunnel drop`. Mundos e túneis existentes são preservados.

## Verificação do JAR

SHA-256:

```text
BB5FD2CE8D16C7F285D63451D4708812B628733B8FD435D85C121844E8EBEF25
```

| Execução | Resultado |
| --- | --- |
| JUnit / `build` | 23 testes aprovados, sem falhas/erros |
| `runPackagedBottomClient` | 25 verificações aprovadas |
| Regressão do cliente empacotado | 34 verificações aprovadas |
| Regressão do servidor empacotado | 3 grupos aprovados, com reabertura |
| Cliente TCP + servidor dedicado | 22 verificações aprovadas |

O teste novo usa um poço local com saída inicialmente fechada. Seleciona blocos reais da saída, minera em sobrevivência pelos pacotes vanilla, rejeita alvos fora do alcance, verifica apoio estável e atravessa. Repete no natural, minerando bedrock e mantendo as camadas inferiores no armazenamento. Verifica também que a opção desligada retira a visão, e que a bedrock normal permanece inquebrável. Screenshots da execução foram inspecionadas; as versões intermediárias com recorte instável foram corrigidas antes do JAR acima.

Os dados brutos, logs e saves ficam locais e não são publicados. O relatório histórico da [0.8.0](ATLAS-LAB-0.8.0.md) documenta atlas, catálogo, satélite, reabertura e dois clientes simultâneos; não representa nova medição de capacidade da 0.8.1.

## Limites

A visão adicional cobre terreno comum e sua seleção/mineração. Não é um portal recursivo: entidades e renderizadores especiais de block entities não ganham uma segunda imagem através do fundo. Iluminação emprestada localmente é uma adaptação visual, não propagação física completa de luz entre as duas regiões. Alcance, carregamento progressivo e deformação continuam existindo.

A passagem mantém meia volta em X e inversão de velocidade Y. Gravidade continua vertical em coordenadas planas; integração por ticks, mudança artificial de posição e colisões não garantem conservação perfeita de energia, polos exatos ou oscilação eterna.

O backup anterior está em `C:\Dev\minecraft-space-mod-backup-0.8.0-before-bottom-passage-20261005`, com código, histórico e JAR 0.8.0 conferidos. A atualização da instância Modrinth substitui somente o JAR, sem alterar saves. A autoria da adaptação local e a origem Jeija/Spheretest estão documentadas em [SPHERETEST-SOURCES.md](SPHERETEST-SOURCES.md); não foi reutilizado código de Immersive Portals.
