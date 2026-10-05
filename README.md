# Space Blocks 0.6.0 — Spheretest

Reprodução da técnica de **Jeija / Spheretest** para Minecraft Java **1.21.1**, NeoForge **21.1.255** e **Java 21**. O mundo é um mapa quadrado plano periódico: armazenamento, movimento, gravidade e colisão continuam planos; o renderizador usa a exponencial complexa relativa à câmera do Spheretest.

## Jogar

Crie **um mundo novo com comandos habilitados** e use:

| Comando | Ação |
| --- | --- |
| `/planet` ou `/planet large` | Entrar no planeta maior |
| `/planet small` | Entrar no planeta pequeno |
| `/planet leave` | Voltar ao mundo e à posição de origem |
| `/planet fly` ou `/planet fly true` | Ativar voo |
| `/planet walk` ou `/planet fly false` | Desativar voo |
| `/planet info` | Dimensões do mapa, posição, fundo e opções |
| `/planet physics realistic_gravity true` | Gravidade variável com altitude |
| `/planet physics centrifugal false` | Desligar o termo centrífugo |
| `/planet physics fallthrough false` | Desligar a travessia do fundo |

As três opções de física aceitam `true` e `false`, são independentes em cada planeta e ficam salvas no mundo. Padrões: gravidade variável **desligada**, termo centrífugo **ligado**, travessia do fundo **ligada**. A correção exponencial `planet_keep_scale` fica sempre ativa.

WASD e mouse seguem os controles normais do Minecraft. Durante voo: Espaço sobe; Shift desce. A travessia das bordas mantém os eixos, yaw e pitch. Gravidade e colisões são verticais nas coordenadas planas.

| Planeta | Raio visual R | Mapa periódico | Intervalo X/Z | Superfície | Fundo |
| --- | ---: | ---: | --- | ---: | ---: |
| Pequeno | 32 | 224 × 224 blocos | [-112, 112) | Y=64 | Y=32 |
| Maior | 256 | 1.632 × 1.632 blocos | [-816, 816) | Y=64 | Y=-192 |

Os tamanhos seguem o original: `ceil((R / 16) × pi) × 32`. O terreno inicial é totalmente plano, sem decoração de bioma, lagos, relevo, cavernas ou estruturas. Há uma camada de grama, terra e pedra até o fundo. A altura de construção vai de -512 a 1023.

## Técnica

Cada bloco tem um único endereço canônico. Consultas e alterações em X/Z equivalentes chegam ao mesmo chunk e à mesma entidade de bloco: não há cópias sincronizadas de inventários. Consultas de colisão mantêm a caixa física na representação local e buscam os blocos do lado conectado. Chunks canônicos recebem tickets e envio segundo a vizinhança periódica; entidades recebem a mesma consideração para ativação e rastreamento.

Para desenhar um chunk, o renderizador avalia os nove deslocamentos X/Z de -L, 0 e +L e escolhe o mais próximo da câmera. Usa a geometria real dos modelos e fluidos do Minecraft. Como no original, não usa a oclusão/frustum plano para o terreno curvado; limita a distância horizontal a L/4 e omite seções abaixo do fundo.

Para um vértice, com `d` igual à distância horizontal à câmera e `dy` igual à diferença de altura:

```text
angle = d / R
radial = R * exp(dy / R)
shownXZ = directionXZ * radial * sin(angle)
shownY  = radial * cos(angle) - R
```

Essas são coordenadas relativas à câmera, antes da rotação de visão. A mão em primeira pessoa continua normal; modelos de entidades, itens no mundo, entidades de bloco e seleção acompanham a projeção. Não há compensação da velocidade pelo tamanho visual dos blocos. A gravidade base usa os valores por entidade do Minecraft, com os coeficientes e o termo centrífugo do Spheretest; veja a conversão de unidades na auditoria.

Ao atravessar o fundo, a opção original desloca X por meio mapa, coloca Y em `fundo + 1` e inverte a velocidade vertical. **Isso pode colocar você dentro da pedra do lado oposto**, como consequência da técnica; abra um túnel de saída ou use voo. O sistema não procura uma superfície alternativa nem altera a fórmula para esconder esse limite.

## Limites reais

- Demonstração técnica: não representa uma esfera física perfeita, órbitas realistas ou uma posição global independente da câmera. A exponencial preserva a proporção infinitesimal no corte vertical; não elimina toda deformação tridimensional.
- Distância de renderização e tempo para carregar/compilar chunks limitam o detalhe. O planeta maior não possui uma malha orbital global inventada; aumentar o alcance aumenta o custo de CPU, memória e envio.
- Vidro/água usam a projeção correta, mas a ordenação de transparência entre seções não equivale à ordenação completa do renderizador vanilla.
- Sons, pathfinding de mobs, veículos/passengers, portais vanilla e redes complexas de redstone não receberam validação completa de comportamento periódico. Entidades sem passageiros, colisões, rastreamento e interação direta nas bordas são tratados. Travessia montada não está suportada.
- Compatibilidade com Sodium/Embeddium, Iris/Oculus, shaders externos e outros mods não foi validada. A instância de teste contém apenas Space Blocks e NeoForge.
- Os mundos 0.5.0 não são compatíveis com a nova topologia. Nenhum save da instância foi apagado. Use o backup/JAR 0.5.0 para restaurar essa versão.

## Compilar e verificar

```powershell
$env:JAVA_HOME = 'CAMINHO_DO_SEU_JDK_21'
.\gradlew.bat build
```

Artefato: `build/libs/spaceblocks-0.6.0.jar`. Os harnesses de integração ficam inativos em instalações normais. O guia inclui os passos de execução e separa validação automática, capturas e verificações manuais: [TESTAR-NO-MODRINTH.md](TESTAR-NO-MODRINTH.md). Resultados registrados: [docs/TEST-RESULTS.md](docs/TEST-RESULTS.md).

## Relevo futuro

O próximo gerador deverá amostrar ruído **periódico em X e Z**, com período igual ao mapa, preservando altura, derivadas e conteúdo das bordas. A mesma regra deve valer para biomas, cavernas, estruturas, decoração e iluminação. Não basta copiar a última coluna para a primeira nem reutilizar o ruído de seis faces. Esses recursos não estão implementados nesta versão.

Origem, histórico, diferenças de unidades e licenças: [auditoria do Spheretest](docs/SPHERETEST-SOURCES.md) e [CREDITS.md](CREDITS.md). Licença do mod: **LGPL-2.1-or-later**; licença do MDK preservada em TEMPLATE_LICENSE.txt.

Repositório: https://github.com/JonasHenriqueDev/spaceblocks-spheretest
