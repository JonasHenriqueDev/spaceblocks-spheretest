# Space Blocks 0.5.0 — teste no Modrinth

Minecraft Java 1.21.1 + NeoForge 21.1.255. Não é preciso instalar Immersive Portals.
O renderizador foi escrito para este projeto e os créditos estão em CREDITS.md.

## Comandos (em inglês)

- `/planet` ou `/planet normal`: novo planeta grande com relevo, faces de 1.024 blocos.
- `/planet colors`: planeta grande plano com seis cores para conferir as bordas.
- `/planet small`: planeta plano de faces de 64 blocos para testes rápidos.
- `/planet flat`: planeta grande plano anterior, preservando as construções.
- `/planet view`: observar a esfera de longe.
- `/planet surface`: retornar a chão seguro próximo.
- `/planet fly` e `/planet walk`: alternar voo livre e caminhada.
- `/planet info`: conferir face, tamanho e altitude.
- `/planet leave`: voltar ao ponto original.

Abra a instância `NeoForge 1.21.1` no Modrinth e um mundo com comandos habilitados.
WASD para andar, Espaço para pular e Ctrl para correr. Em voo, Espaço sobe e
Shift desce. O planeta novo tem volta equatorial aproximada de 4.096 blocos.

## O que conferir

1. No planeta colorido, construa uma torre próxima da borda e observe-a da face vizinha antes de atravessar.
2. Caminhe pela borda observando a torre e faça alterações para verificar a atualização da imagem.
3. No planeta pequeno, dê uma volta completa e passe pelos cantos.
4. Use `/planet` para conhecer as colinas e vales do novo planeta grande.
5. Cave para baixo e use `/planet surface` para retornar. O crash de malha vazia permanece corrigido.
6. Use `/planet view`: construções acima do terreno aparecem em representação simplificada à distância.

Faces do planeta colorido: Frente verde; Direita azul; Verso roxo; Esquerda
amarela; Norte branco; Sul vermelho. O HUD identifica a face.

## Saves e limites

O planeta com relevo é uma dimensão nova. Os planetas anteriores e seus blocos
continuam preservados; `/planet flat`, `/space radial` e `/space legacy`
permitem revisitá-los. Um jogador salvo no planeta antigo continua nele até
usar um comando para mudar de planeta.

- As seis faces usam vistas de passagens invisíveis e blocos de origem reais.
- O carregamento antecipado tem orçamento; avançar para áreas inéditas pode exigir alguns segundos para completar a vista.
- O solo inicial é uma camada de cerca de 25 blocos; ainda não há interior, cavernas, vegetação ou biomas elaborados.
- A correção suave dos cantos é visual, com gravidade normal.
- A vista orbital usa cubos texturizados para construções; formas especiais e transparência ficam simplificadas. A malha orbital não mostra buracos da mineração.
- Entidades remotas, líquidos, áudio, redstone e multiplayer ainda precisam de implementação ou validação adicional.
- Baús e outros blocos com entidades de bloco continuam bloqueados nesta dimensão enquanto inventários compartilhados não estiverem prontos.
- Artefatos em cantos, transparência e compatibilidade com outros renderizadores ainda precisam de testes de uso. Esta versão é experimental.

## Desenvolvimento

Projeto: `C:/Dev/minecraft-space-mod`. JAR: `build/libs/spaceblocks-0.5.0.jar`.

`./gradlew.bat build` compila e executa os testes unitários.
`runPackagedPortalClient` testa construção remota, alteração ao vivo, passagem,
LOD orbital e planeta com relevo. `runPackagedFlatClient` testa 24 bordas, volta
completa e oito cantos; `runPackagedDigClient` testa mineração. Esses clientes
usam o JAR em `run-smoke/mods`, sem carregar o mod pelas fontes, e um save de
desenvolvimento separado. Os testes só são ativados por propriedades JVM.
