# Space Blocks 0.8.0 — entrega e verificação

Verificado em 5 de outubro de 2026, Windows, Java 21, Minecraft 1.21.1 e NeoForge 21.1.255. Os runs de jogo carregaram o JAR final em `mods`, sem carregar as classes de desenvolvimento como mod.

SHA-256 de `spaceblocks-0.8.0.jar`:

```text
F675F0A821BAD036F75412585F94BC32D7898AD58562DB921B033BB8CDD93089
```

## Implementado

- Atlas 3D dentro do Minecraft: rotação, zoom, informação de tamanho/raio/fundo e cobertura confirmada, atualização de superfície e modos esquemático/original Spheretest.
- Catálogo de planetas compartilhado pelo servidor; criação com nome, raio e seed aleatória ou explícita; teleporte por coordenadas ou superfície. Até 16 planetas gerados independentes por save, além dos quatro fixos.
- Dois poços conectados para reproduzir a passagem pelo fundo do Spheretest. Bedrock minerável somente nas dimensões do mod com `fallthrough` habilitado. Remoção opcional do arrasto vertical no ar de jogadores/mobs.
- Planeta de laboratório com terreno, cavernas, plataformas nas bordas, torres de TNT e sonda orbital sincronizada. A sonda mantém tickets locais e índice salvo para sobreviver a uma volta completa e reabertura.
- Geração periódica e otimizações anteriores preservadas: não é o gerador completo do Overworld. Cada novo planeta sem seed explícita recebe sua própria seed.

## Resultado do artefato final

| Execução | Resultado |
| --- | --- |
| `build` / JUnit | 21 testes; zero falhas/erros |
| `runPackagedPeriodicClient` | 34 verificações aprovadas |
| `runPackagedPeriodicServer` | 3 grupos aprovados: pequeno, maior e natural |
| `runPackagedAtlasClient` | 23 verificações aprovadas |
| `runPackagedLabClient -PverifyPersistence=true` | 27 verificações aprovadas após fechar/reabrir |
| Servidor dedicado + cliente TCP principal | 24 verificações aprovadas |
| Segundo cliente TCP, simultâneo | 3 verificações aprovadas |

Os resultados foram conferidos pelos marcadores de aprovação e arquivos de resultados, além do exit code. Logs, saves e screenshots brutos ficam locais e não são publicados no Git.

O servidor verificou as quatro bordas, diagonal, voltas completas nas duas direções de cada eixo, armazenamento único, inventário, colisão, mineração profunda, opções de fundo/gravidade e reabertura. O cliente verificou movimento WASD real, orientação, seleção, interação e pacotes de mineração/construção, terreno desenhado, barco com passageiro e portais do Nether. O atlas foi aberto e manipulado, e uma alteração de altura foi refletida no refresh. Os túneis foram ensaiados nos planetas pequeno e natural, com passagem, subida na outra saída e retorno em queda.

O laboratório verificou torres/plataformas, a sonda como entidade real, uma volta completa equilibrada, resposta ao desligar o centrífugo e recuperação pelo índice salvo. Planetas com raios/seeds distintos, construção persistida e isolamento de chunks passaram na reabertura. O catálogo e teleporte por posição passaram no cliente integrado e na conexão TCP.

Dois clientes com nomes/UUIDs distintos permaneceram simultaneamente no servidor: o segundo colocou esmeralda; o primeiro recebeu a alteração e colocou ouro; o segundo recebeu o ouro. Isso comprova compartilhamento básico entre dois jogadores, mas não é benchmark de capacidade, latência pela internet ou compatibilidade com outros mods.

## Instalação e laboratório entregue

A instância Modrinth `NeoForge 1.21.1` contém somente o JAR 0.8.0 do Space Blocks, com o SHA acima. O JAR anterior foi guardado no backup externo `C:\Dev\minecraft-space-mod-backup-0.7.1-before-atlas-20261005`.

O save fechado e testado foi copiado para `saves\Space Blocks Lab 0.8.0` da instância; seus 250 arquivos foram conferidos por hash antes de ajustar nome do mundo, modo criativo e comandos habilitados. Nenhum save do usuário foi substituído. Abra esse mundo e use `/planet lab`, `/planet map` e `/planet satellite info`. Para o túnel, escolha uma coluna, use `/planet tunnel create`, espere `Tunnel ready` e execute `/planet tunnel drop`. Criar os poços substitui blocos nas duas colunas indicadas no README.

Se o Minecraft já estava aberto, é necessário reabri-lo para carregar o novo JAR; a sessão do usuário não foi encerrada pelo agente.

## Limitações e verificações ainda manuais

A física continua plana e a projeção do mundo é relativa à câmera, como no Spheretest. A passagem pelo fundo desloca meia volta em X e inverte a velocidade Y; não representa um núcleo esférico físico nem polos globais. Integração por ticks e reposicionamento podem ganhar/perder energia: quedas altas podem escapar e não há promessa de oscilação eterna. A sonda é uma ferramenta controlada de ensaio, não validação de órbitas realistas de todas as entidades vanilla.

O atlas global é esquemático, com relevo comprimido. São 96×96 amostras da superfície; não mostra todas as construções individuais ou cavernas. Regiões não carregadas são previstas e podem omitir construções salvas. O modo Spheretest usa a fórmula original no hemisfério próximo.

As torres de TNT e plataformas estão preparadas; inspeção humana da deformação em várias alturas, iluminação, transparência, todos os mobs/máquinas de redstone/veículos e uma sessão longa de jogo continuam no roteiro manual. Os testes automatizados não substituem essa avaliação visual. As medições de desempenho em alcance 12 são as da 0.7.1, documentadas separadamente; a 0.8.0 não traz um novo benchmark comparativo.

## Próximos passos

Continuar medindo geração e streaming em hardware/servidores diferentes, ampliar validação de entidades e redstone, melhorar o atlas para construções fora do alcance sem gerar terreno, e expandir a variedade de relevo/biomas/estruturas mantendo periodicidade em todas as camadas. Gestão de remoção/reciclagem dos 16 slots exige um fluxo seguro separado.

Origem, fórmula ativa, histórico e licenças: [SPHERETEST-SOURCES.md](SPHERETEST-SOURCES.md) e [CREDITS.md](../CREDITS.md). Código, histórico e transcrições foram examinados; não se afirma ter assistido aos vídeos inacessíveis.
