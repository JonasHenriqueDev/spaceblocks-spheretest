# Planeta plano com projeção esférica

## Endereços e geometria

CubeTopology define seis faces e 24 ligações direcionais. A posição lógica é
(face,u,v,y); a passagem transporta posição, velocidade horizontal e yaw por
rotações de 90 graus, preservando a altura e o eixo vertical da física.
FlatDefinition separa o mapa lógico dos endereços físicos dos chunks.

Cada face física tem uma faixa de terreno adicional representando suas vizinhas.
Ela permite enxergar e colidir com a continuação antes de trocar de face. Um bloco
lógico tem endereço canônico único; FlatEdits salva alterações, e FlatMotion
atualiza as cópias carregadas com orientação correspondente.

## Passagens

O servidor prepara os chunks de destino antes da passagem. O cliente mantém
chunks fora da janela vanilla, prevê a troca antes do pacote de movimento e
ajusta posição atual/anterior, velocidade, yaw e centro de chunks. O servidor
confere proximidade e face e mantém a velocidade autoritativa. A confirmação
continua pelo protocolo vanilla de teleporte.

FlatSceneCache copia as malhas visíveis na GPU e aplica o mesmo transporte.
Cada malha antiga é retirada quando sua correspondente está carregada e
compilada, evitando terreno vazio durante a reorganização dos chunks.

## Aparência e cantos

SphereProjection cria uma esfera com vértices compartilhados e relaxação
harmônica na malha do cubo. CPU e GPU usam os mesmos dados. Shaders misturam
posições físicas planas e esféricas, aumentando a curvatura com distância e
altitude. A região próxima fica plana, exceto pela correção aceita nos cantos.

Três quadrados somam 270 graus em um canto. A esfera precisa fechar esse ângulo,
por isso uma vizinhança pequena recebe correção visual suave. FlatVisual reproduz
a transformação: inverte sua derivada para os controles, inverte a projeção para
selecionar blocos e projeta o contorno da seleção. A colisão física continua cúbica.

O céu contém estrelas; FlatPlanetMesh desenha uma esfera completa sob os chunks
detalhados. Essa malha distante ainda não incorpora alterações do jogador.

## Próximas extensões

Validar passagens com construções diversas, máquinas mais lentas e latência.
Depois: entidades e inventários com identidade canônica única; simulação única
de líquidos/redstone; relevo e biomas contínuos; atualização da malha distante;
interação em grandes altitudes; veículos; espaço e múltiplos planetas.
As dimensões radiais anteriores permanecem compatíveis com seus mundos.
