# Resultados da versão 0.6.0

Validação local em Windows, Java 21, Minecraft 1.21.1 e NeoForge 21.1.255. Data: 05/10/2026. As sessões automáticas foram iniciadas pelo desenvolvimento em `run-periodic`; nenhum mundo original da instância Modrinth foi usado ou apagado.

## Artefato validado

`spaceblocks-0.6.0.jar`

SHA-256: `5A53EC30A91EF2FD6977EDA2467D08658346BD13AAC7B5AF4D703BACFFC2CB3F`

Os runs empacotados usam `loadedMods=[]` e verificam a origem das classes no JAR. Cliente e servidor passaram com esse mesmo artefato. Não se trata apenas de uma compilação ou de um shader isolado.

## Matriz

| Verificação | Evidência / resultado |
| --- | --- |
| Build Java 21 | `gradlew build` aprovado; 7 testes JUnit, sem falhas |
| Matemática da técnica | Fórmula exponencial, casos de referência, 10.000 inversões, proporção no corte vertical, nove deslocamentos, periodicidade e coeficientes de física aprovados |
| Quatro bordas e diagonal | Cliente real movimentado com W, rotação da câmera preservada; servidor verifica posição, velocidade, yaw/pitch nos dois planetas |
| Voltas completas | Quatro voltas físicas automáticas por planeta: direções positiva/negativa de X e Z, usando movimento e colisão de entidade |
| Chunks e blocos | Endereço canônico compartilhado pelas nove representações; escrita, mineração e colisão na borda aprovadas nos dois planetas |
| Construção e inventários | Pacotes reais de construção/mineração; parede atravessando borda, baú único e conteúdo persistido aprovados |
| Renderização | VBOs efetivamente compilados/desenhados, terreno, água, baú e entidades em capturas; planetas pequeno e maior renderizados |
| Seleção e entidades | Seleção projetada de TNT e de entidade pelo lado conectado; pacote real de ataque aprovado |
| TNT | Casos matemáticos em diferentes alturas, torres e seleção em capturas; explosões no servidor em Y=80/120/180 e dano a entidade através da borda aprovados |
| Mineração profunda | Cliente minerou para baixo nos dois planetas; no maior ultrapassou Y=-150; servidor verificou túneis e colisões profundas, sem crash |
| Subida e descida | Câmera em diferentes altitudes e descida em shafts; controles nativos de voo documentados |
| Física | Coeficientes de gravidade e centrífugo, opções sincronizadas e fundo ligado/desligado aprovados no servidor; travessia do fundo e meio mapa aprovados no cliente |
| Salvamento e reabertura | Servidor encerrado e reaberto: marcador de diamante e inventário de três diamantes preservados nos dois planetas; cliente abriu um save fechado e encerrou normalmente |
| Comandos | Entrada nos dois planetas, opções de física e retorno ao mundo padrão aprovados |

O harness do cliente registrou **22 verificações aprovadas**. O servidor registrou **duas baterias aprovadas**, uma por planeta, incluindo `REOPEN persistence`. Os arquivos de resultados e console ficam locais e não são publicados como logs privados.

## Correções verificadas durante a integração

Foram corrigidos a declaração de shadow do cache de chunks, acesso concorrente ao cache, identificador GLSL reservado, seleção do overload de partículas e iteração sobre entidades durante mudança de chunk. As rodadas finais empacotadas passaram depois dessas correções. A velocidade local do jogador é preservada após o pacote de teleporte da borda; no fundo seu componente vertical é invertido.

## Alcance da validação

As voltas completas foram automáticas com uma entidade física; as travessias de bordas/diagonal foram também executadas pelo jogador no cliente. Isso não substitui uma avaliação prolongada da sensação da câmera e do desempenho no computador do jogador. As capturas demonstram a geometria, mas não certificam ausência de toda deformação tridimensional: a técnica conserva escala no corte vertical e depende da câmera.

O save principal das capturas foi criado antes de retirar a decoração vanilla; contém flores/grama dessa preparação antiga. O gerador final não aplica decoração, carvers ou estruturas. Uma rodada adicional em save novo (`PeriodicFresh`) passou com o mesmo JAR final, incluindo geração e a bateria do servidor nos dois planetas. Essa rodada escreveu seus primeiros marcadores; a comprovação de reabertura é da rodada anterior em `PeriodicTest`.

O mesmo hash foi conferido depois da instalação na instância Modrinth `NeoForge 1.21.1`, que ficou com um único JAR do mod. A sessão normal iniciada pelo Modrinth App não foi aberta como parte da validação: o cliente empacotado foi executado no ambiente isolado com as mesmas versões de Minecraft/NeoForge/Java.

Não houve validação completa de multiplayer remoto, veículos/montarias, sons através da borda, pathfinding, portais vanilla, redes complexas de redstone, partículas distantes através das bordas, ordenação integral de transparência ou compatibilidade com outros renderizadores/mods. A travessia do fundo pode colocar uma entidade dentro de pedra: é a regra original, documentada no README. O guia manual indica como avaliar esses limites sem apresentar o sistema como uma esfera física perfeita.

## Origem e acesso aos vídeos

Foram lidos a transcrição fornecida, o código do Spheretest e seu histórico, inclusive a reversão `d973e4b40dc0da723921563ff2093e105c345892`. Os links dos vídeos foram consultados, mas o acesso não permitiu assistir ao conteúdo; não se afirma que os vídeos foram assistidos. A auditoria detalhada está em [SPHERETEST-SOURCES.md](SPHERETEST-SOURCES.md).
