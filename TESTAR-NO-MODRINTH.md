# Testar Space Blocks 0.7.0 no Modrinth

Minecraft 1.21.1 / NeoForge 21.1.255 / Java 21. Use comandos habilitados. Mantenha somente um JAR Space Blocks em `mods`.

## Acesso ao mapa e ao interior

1. `/planet natural` e `/planet info`. Confira relevo e biomas. Use `/planet fly` para observar a curvatura e Espaço/Shift para mudar a altitude.
2. Voo tem colisão. Para atravessar pedra: `/planet noclip true`. Para sair: `/planet noclip false`, que retorna à superfície e restaura seu modo de jogo.
3. `/planet core` cria uma pequena câmara iluminada e fechada em Y=-471. É acesso ao interior profundo; a técnica relativa à câmera não tem um centro geométrico finito.
4. `/planet surface` e `/planet leave` devem devolver você a posições seguras. Confira suas habilidades e modo de jogo após sair.

A dimensão natural é nova. Os mapas planos 0.6.0 continuam disponíveis em `/planet small` e `/planet flat`; suas construções não são regeneradas. Novos recursos não são inseridos em chunks já gerados durante testes preliminares de desenvolvimento.

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
- `runPeriodicNetworkServer`: `run-network-server`, `127.0.0.1:25580`, `online-mode=false`, EULA conforme sua aceitação. Carrega somente o JAR; o flag de teste concede operador aos jogadores. Nunca exponha esse run à internet.
- `runPeriodicNetworkClient`: `run-network-client`, conexão TCP ao servidor acima. Verifica comandos, movimento, mineração/construção e redstone através da borda, núcleo e noclip. Ao passar, salva e encerra o servidor de teste.

Copie o JAR final para `mods` de cada run e remova versões antigas dessas pastas de teste. Os flags não são habilitados em instalação normal. Console/resultados ficam locais, fora do Git.

Verifique `PERIODIC_CLIENT_TEST_PASS`, `PERIODIC_SERVER_TEST_PASS` e `PERIODIC_NETWORK_TEST_PASS`, além dos arquivos de resultados em cada diretório. O exit code sozinho não comprova aprovação: uma falha controlada pode fechar o jogo normalmente.

## Recuperação

Antes da atualização, foi guardado `C:\Dev\minecraft-space-mod-backup-0.6.0-20261005`, com código, JAR 0.6.0 e saves. A tag pública `v0.6.0` conserva o código. Para voltar, feche o jogo, substitua o JAR e restaure o save correspondente do backup; um mundo já salvo com dimensões da 0.7.0 pode não carregar na versão anterior sem restaurar o save.

O backup original 0.5.0 permanece em `C:\Dev\minecraft-space-mod-backup-0.5.0-20261005` e a tag `recovery-0.5.0` permanece publicada. Nenhum save original foi apagado.
