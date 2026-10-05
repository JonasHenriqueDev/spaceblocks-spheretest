# Testar Space Blocks 0.6.0 no Modrinth App

Minecraft Java 1.21.1 + NeoForge 21.1.255 + Java 21. Instância: `NeoForge 1.21.1`.

## Instalação e recuperação

O JAR 0.6.0 foi instalado na pasta `mods` da instância, com hash conferido contra o artefato testado. Ele é o único `spaceblocks-*.jar` nessa pasta; o 0.5.0 foi movido para o backup externo. Crie um mundo novo com comandos habilitados. Mundos anteriores à 0.6.0 não devem ser usados para testar a topologia nova.

A recuperação 0.5.0 está na tag Git `recovery-0.5.0`. O backup completo local é `C:\Dev\minecraft-space-mod-backup-0.5.0-20261005`: projeto, JAR antigo e saves originais. Para restaurar, feche o jogo, retire o JAR 0.6.0, copie o JAR 0.5.0 desse backup para `mods` e restaure o save correspondente se necessário. Não misture os dois JARs. O backup local não foi publicado.

## Roteiro no planeta pequeno

1. `/planet small`, `/planet info`. O intervalo é [-112,112) em X e Z. Anote a posição; caminhe através de X=112, X=-112, Z=112 e Z=-112. Depois atravesse um canto na diagonal. WASD, yaw e pitch devem manter sua direção.
2. Faça uma volta completa em X e outra em Z. Volte à posição anotada sem mudança de altura do terreno. As voltas automáticas incluem as duas direções de cada eixo; a percepção do movimento continua sendo uma verificação manual útil.
3. Construa entre X=110,111 e a representação X=112,113, que corresponde a X=-112,-111. Coloque e remova blocos dos dois lados; confira o mesmo inventário de um baú pelo lado conectado. Repita em Z e no canto.
4. Construa torres e coloque TNT em Y=81,96,112. Compare o formato ao mudar a altura da câmera com `/planet fly`. A correção é a exponencial do corte vertical; não espere que toda perspectiva tridimensional seja idêntica à de um cubo em uma esfera física.
5. Abra túneis perto das bordas e cave para baixo. O fundo é Y=32. `/planet physics fallthrough false` permite observar a queda sem a mudança de lado; ligue de novo antes do teste de travessia. Abra também um corredor de saída no lado oposto para evitar emergir dentro de pedra.
6. `/planet physics realistic_gravity true`; compare saltos e queda em altitudes diferentes. Teste `centrifugal true` e `false` durante deslocamento horizontal. Não há ajuste da velocidade pelo tamanho visual dos blocos.
7. Confira seleção de blocos, porcos, itens caídos, TNT acesa, água, baús e mão em primeira pessoa, inclusive perto de uma borda. A mão deve continuar normal; objetos do mundo devem acompanhar o terreno.
8. `/planet leave`. Confira posição e habilidades de voo no mundo de origem.

## Planeta maior e salvamento

`/planet large` usa [-816,816) em X/Z, fundo Y=-192. Repita construção e mineração profunda, suba/desça com voo e ajuste a distância de renderização à sua máquina. Salve, feche o Minecraft e reabra; confirme construções, inventários, blocos minerados e opções de física. Guarde um screenshot da posição antes/depois para comparar.

## Harnesses isolados no projeto

Os testes de unidade rodam com `gradlew build`. Os clientes automáticos usam teclado simulado pelo próprio Minecraft, ray picking real, pacotes de mineração/construção, capturas pelo Minecraft e um servidor integrado. Não controlam outra sessão do usuário.

Os runs `periodicServer` e `periodicClient` usam `run-periodic`. Os runs `packagedPeriodicServer` e `packagedPeriodicClient` usam `loadedMods=[]` e verificam que a origem da classe do mod é o JAR final em `run-periodic/mods/spaceblocks-0.6.0.jar`. O mundo isolado se chama `PeriodicTest`: o servidor dedicado usa `run-periodic/PeriodicTest`; para o cliente copie esse mundo fechado para `run-periodic/saves/PeriodicTest`.

Prepare `eula.txt` conforme sua aceitação do EULA, `server.properties` com mundo plano, modo criativo, `online-mode=false`, `server-ip=127.0.0.1`, porta livre e alcance 4 ou 5. Isso serve somente ao teste local. O cliente aceita automaticamente as telas de início/aviso apenas com a propriedade de teste ativada.

```powershell
.\gradlew.bat runPeriodicServer
.\gradlew.bat runPeriodicClient
.\gradlew.bat build
Copy-Item build/libs/spaceblocks-0.6.0.jar run-periodic/mods/spaceblocks-0.6.0.jar
.\gradlew.bat runPackagedPeriodicServer
.\gradlew.bat runPackagedPeriodicClient
```

Confira os arquivos `periodic-server-results.txt` e `periodic-client-results.txt` dentro de `run-periodic`, além dos marcadores `PERIODIC_SERVER_TEST_PASS` / `PERIODIC_CLIENT_TEST_PASS` no console. **O exit code do jogo isoladamente não comprova aprovação do harness**: uma falha controlada também pode fechar o jogo normalmente. Rode o servidor duas vezes para verificar `REOPEN persistence` e inventário salvo. As capturas ficam em `run-periodic/screenshots` e os logs privados não entram no Git.

A matriz de resultados e limitações está em [docs/TEST-RESULTS.md](docs/TEST-RESULTS.md).
