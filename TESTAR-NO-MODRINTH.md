# Testar Space Blocks 0.8.0 no Modrinth

Minecraft 1.21.1 / NeoForge 21.1.255 / Java 21. Use comandos habilitados. Mantenha somente um JAR Space Blocks em `mods`.

## Acesso ao mapa e ao interior

- `/planet lab`: aguarde `Test lab ready`. Teste torres em X=16/24/32, seleção/baú em X=-5 e plataformas em X/Z=±416, Y=180. Mine o terreno natural e cavernas abaixo do deck. O laboratório é separado do planeta de jogo.
- `/planet satellite info`: observe posição e voltas. Compare `/planet satellite launch 128 1`, fatores 0.8/1.2 e `centrifugal false/true`. A sonda dourada tem elytra e não colide. Salve/reabra e confira sua retomada. A sonda do laboratório é comum aos operadores.
- No atlas, abra `Planets`. Gere `aurora`, raio 64 e seed em branco; gere outro nome com raio 128. Confira tamanhos e terrenos diferentes. Teste uma seed explícita em nomes diferentes e salve/reabra. `/planet natural` continua entrando no mapa antigo salvo.
- Selecione um planeta na lista e teste `Teleport`/`Enter + map` com X/Z e `surface`; repita com Y=200. Confira normalização de bordas, nome/raio e preservação de construções em outros planetas.

- `/planet map`: gire arrastando, amplie/reduza com a roda, alterne `Globe atlas`/`Spheretest view`, `Loaded`, `Center`, `Reset` e `Refresh`. Confira tamanho 224 no pequeno e 1632 no natural. Edite uma superfície carregada e atualize. Previsões fora do alcance não confirmam construções salvas.
- Em um local sem construções importantes, `/planet tunnel create` substitui duas colunas de 5×5 até o fundo. Espere `Tunnel ready`, execute `/planet tunnel drop` e observe queda, deslocamento horizontal, subida na outra saída e retorno. `/planet surface` deve sair do poço. Teste no pequeno e no natural, salve e reabra.
- Para cavar manualmente, `/planet physics fallthrough true` torna a bedrock do planeta minerável. Abra a outra saída em X + metade do tamanho do mapa, com o mesmo Z. Ligue `realistic_gravity true`, `air_drag false` e `centrifugal false` para a mesma configuração da queda demonstrada. Compare `air_drag true`, que amortece a oscilação.

1. `/planet natural` e `/planet info`. Confira relevo e biomas. Use `/planet fly` para observar a curvatura e Espaço/Shift para mudar a altitude.
2. Voo tem colisão. Para atravessar pedra: `/planet noclip true`. Para sair: `/planet noclip false`, que retorna à superfície e restaura seu modo de jogo.
3. `/planet core` cria uma pequena câmara iluminada e fechada em Y=-471. É acesso ao interior profundo; a técnica relativa à câmera não tem um centro geométrico finito.
4. `/planet surface` e `/planet leave` devem devolver você a posições seguras. Confira suas habilidades e modo de jogo após sair.

A dimensão natural é nova. Os mapas planos 0.6.0 continuam disponíveis em `/planet small` e `/planet flat`; suas construções não são regeneradas. Novos recursos não são inseridos em chunks já gerados durante testes preliminares de desenvolvimento.

## Carregamento e desempenho

Use renderização e simulação em 12 chunks para repetir o caso que expôs o problema. Entre com `/planet natural` e aguarde o carregamento progressivo; a superfície deve preencher e permanecer visível. Depois caminhe, voe e atravesse uma borda. Confira o subterrâneo com `/planet core`, volte com `/planet surface` e reabra o mundo.

Os testes anteriores usavam alcance 5; a 0.7.1 acrescenta uma rodada isolada de geração natural do zero com alcance 12. Não é necessário apagar mundos ou regenerar a dimensão para instalar a correção. Conserve suas construções.

## Roteiro de jogo

- Atravesse X=±816 e Z=±816 no mapa natural, inclusive uma diagonal. Faça uma volta em cada direção. No pequeno os limites são ±112. Compare orientação e velocidade.
- Construa, mine e abra um baú pelo lado conectado. Teste no canto também. Confira que há um inventário único.
- Crie um circuito de redstone que atravesse a borda. Ligue/desligue uma lâmpada; observe repetidores e pistões. Ticks não devem causar crash.
- Atravesse uma borda montado em barco/cavalo e em um minecart. O passageiro deve continuar montado. Compare câmera durante movimento e curvas.
- Observe mobs perseguindo alvos pelo lado conectado, evitando obstáculos. Teste também mobs com ataques especiais; esses comportamentos precisam de avaliação individual.
- Escute sons perto da borda e durante travessia; observe partículas de mineração, TNT e fogo. Coloque vidro/água em várias alturas e direções de câmera.
- Teste torres de TNT, iluminação de cavernas, túneis e mineração até a bedrock. Procure galerias, câmaras de loot e cabanas.
- Compare `/planet physics realistic_gravity true/false`, `centrifugal true/false` e `fallthrough true/false`. No natural o fundo começa desligado. A travessia ligada pode colocar você dentro da pedra do lado oposto, seguindo a regra original.
- Salve, feche e reabra. Confira construções, mineração, baú, opções e modo de jogo. Avalie desempenho em uma sessão mais longa e ajuste render distance.

## Harnesses reproduzíveis

`gradlew build` executa JUnit. Os runs empacotados desativam o carregamento das classes de desenvolvimento como mod e verificam a origem no JAR em `mods` do diretório de teste.

- `runPackagedPeriodicServer`: diretório `run-periodic`; bateria dos mapas planos, terreno natural e estruturas. Rode duas vezes para verificar reabertura. Configure `server.properties` com mundo plano local, seed=0, alcance 4/5 e EULA conforme sua aceitação.
- `runPackagedPeriodicClient`: `run-periodic/saves/PeriodicTest`. Copie para lá um mundo de teste fechado do servidor. Usa teclado, seleção, pacotes reais, mineração profunda, câmera, núcleo, noclip e barco; produz screenshots.
- `runPackagedAtlasClient`: `run-atlas/saves/PeriodicTest`, cópia isolada fechada. Usa apenas o JAR em `mods`; testa painel, rotação/zoom, atualização da altura, túneis e oscilação no pequeno/natural. Execute novamente para validar opções salvas. Produz `atlas-client-results.txt` e screenshots.
- `runPackagedLabClient`: mesmo save isolado do atlas; verifica laboratório, sonda, catálogo, geração e teleporte. Depois de uma execução inicial, use `runPackagedLabClient -PverifyPersistence=true` para exigir construção e índice da sonda já salvos, em vez de apenas preparar dados novos.
- `runPeriodicNetworkServer`: `run-network-server`, `127.0.0.1:25580`, `online-mode=false`, EULA conforme sua aceitação. Carrega somente o JAR; o flag de teste concede operador aos jogadores. Nunca exponha esse run à internet.
- `runPeriodicNetworkClient`: `run-network-client`, conexão TCP ao servidor acima. Verifica comandos, movimento, mineração/construção e redstone através da borda, núcleo e noclip. Ao passar, salva e encerra o servidor de teste.
- Para dois jogadores: inicie `runPeriodicNetworkObserver` e `runPeriodicNetworkClient -PtwoClients=true`, com JAR idêntico nos três diretórios. O cliente `Observer` usa outro nome/UUID, coloca esmeralda, recebe a alteração para ouro feita pelo primeiro cliente e permanece conectado até o encerramento. Não exponha esses runs à internet.

Copie o JAR final para `mods` de cada run e remova versões antigas dessas pastas de teste. Os flags não são habilitados em instalação normal. Console/resultados ficam locais, fora do Git.

Verifique `PERIODIC_CLIENT_TEST_PASS`, `PERIODIC_SERVER_TEST_PASS` e `PERIODIC_NETWORK_TEST_PASS`, além dos arquivos de resultados em cada diretório. O exit code sozinho não comprova aprovação: uma falha controlada pode fechar o jogo normalmente.

## Recuperação

A entrega 0.8.0 inclui o mundo local **Space Blocks Lab 0.8.0** na instância Modrinth, em criativo e com comandos. Abra-o e use `/planet lab` para acessar as plataformas/torres e `/planet map` para o painel. Use `/planet satellite info` para consultar a sonda comum. Para testar queda, crie os dois poços com `/planet tunnel create`, aguarde `Tunnel ready` e execute `/planet tunnel drop`. Consulte [o relatório 0.8.0](docs/ATLAS-LAB-0.8.0.md) para os resultados e limitações.

O código/JAR anterior à 0.8.0 está em `C:\Dev\minecraft-space-mod-backup-0.7.1-before-atlas-20261005`. Para restaurar, feche o jogo e recoloque o JAR 0.7.1 guardado, retirando a 0.8.0 da pasta `mods`. Não abra saves que receberam planetas gerados da 0.8.0 em versões anteriores; conserve o save original ou uma cópia fechada compatível.

Antes da atualização, foi guardado `C:\Dev\minecraft-space-mod-backup-0.6.0-20261005`, com código, JAR 0.6.0 e saves. A tag pública `v0.6.0` conserva o código. Para voltar, feche o jogo, substitua o JAR e restaure o save correspondente do backup; um mundo já salvo com dimensões da 0.7.0 pode não carregar na versão anterior sem restaurar o save.

O backup original 0.5.0 permanece em `C:\Dev\minecraft-space-mod-backup-0.5.0-20261005` e a tag `recovery-0.5.0` permanece publicada. Nenhum save original foi apagado.
