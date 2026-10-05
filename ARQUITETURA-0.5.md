# Arquitetura 0.5 — vistas de portais nas seis faces

Minecraft 1.21.1, NeoForge 21.1.255. Renderizador escrito independentemente;
veja CREDITS.md para a inspiração no Immersive Portals.

## Endereços e travessia

CubeTopology continua sendo a topologia do planeta: 24 bordas direcionadas e
seis charts planos. Gravidade e colisão permanecem verticais. O armazenamento
mantém aprons para colisão, seleção e compatibilidade dos blocos. Sua geometria
não é mais a fonte visual das faces vizinhas: os shaders cortam os aprons.
FlatEdits conserva o endereço canônico e a orientação das modificações.

PortalTopology enumera vistas com matrizes afins de rotação e translação,
invertendo cada ligação de borda. A câmera virtual e os blocos de origem usam
a mesma transformação. O plano vertical não sofre inclinação. As vistas são
enumeradas até cinco ligações, com limite de 80 vistas; isso cobre as seis
faces e repetição espacial necessária no planeta pequeno.

## Renderização próxima

PortalStreaming carrega chunks canônicos ao redor das câmeras virtuais, com
prioridade por distância e orçamento de três pacotes a cada quatro ticks.
Dados de origem ficam no cache de chunks do cliente; mudanças de blocos
invalidam a geometria remota. Uma nova dimensão reinicia o estado de envio.

PortalRenderer monta meshes dos estados reais de blocos usando o renderizador
de modelos do Minecraft. Mantém as camadas solid, cutout, cutout_mipped e
translucent. VBOs de origem persistem através da troca de chart. O cache é
limitado a 768 seções e a compilação usa orçamento por frame.

O stencil restringe a imagem às passagens invisíveis nas bordas; o shader corta
cada rota pelo endereço canônico e compara as coordenadas de origem. Assim,
rotas sobrepostas nos cantos não devem desenhar cópias do mesmo bloco. Nos
cantos corrigidos, as passagens deixam de ter uma fronteira plana na imagem:
o recorte por endereço e a profundidade delimitam as vistas, sem a máscara
stencil da borda plana. As vistas
usam uma cena e um depth buffer compartilhados: este caso especializado não
precisa executar novamente o renderizador completo de outra dimensão.

A correção visual dos oito cantos e a projeção esférica usam o mesmo SphereMap
para a geometria local e remota. A gravidade não muda. O cache de snapshots GPU
introduzido em 0.4 não participa mais das travessias.

## Relevo

flat_terrain é uma dimensão nova, selecionada por /planet. O plano grande antigo
continua em flat_planet, acessível por /planet flat. Não há conversão destrutiva
dos chunks existentes.

FlatDefinition.relief é opcional no codec e padrão false para saves antigos.
O relevo usa um campo suave em coordenadas esféricas compartilhadas, dependente
da seed. Isso evita gerar ruídos incompatíveis separadamente em cada face.
Nesta etapa há colinas e vales; biomas, vegetação e cavernas vêm depois.

## Vista distante e construções

A malha global inclui as alturas do relevo. FlatNetwork.Scene envia as
modificações persistentes, em lotes de até 4.096 registros. OrbitBuildRenderer
usa cubos texturizados para representar blocos colocados acima do terreno,
independentemente de seus chunks estarem carregados para renderização detalhada.
É um LOD: escadas, cercas, plantas e blocos transparentes não têm representação
orbital completa. O cache de modificações do cliente é limitado a 65.536
endereços. Mineração não escava a malha global simplificada nesta versão.

## Limites que permanecem

A técnica cobre terreno e modelos de blocos. Renderização de entidades através
das passagens, inventários compartilhados, líquidos, áudio remoto, redstone e
multiplayer com latência não estão completos. Colocação de blocos com entidades
de bloco permanece bloqueada para evitar duplicar inventários. A ordenação da
transparência remota ainda não equivale à ordenação completa do Minecraft.
Shaders externos e otimizações de outros mods precisam de testes próprios.

O carregamento tem orçamento: uma face ainda não carregada pode apresentar
atraso. Uma travessia visualmente perfeita em qualquer construção e máquina não
é garantida pelos testes de geometria. As capturas e os testes no cliente fazem
parte da validação desta etapa.
