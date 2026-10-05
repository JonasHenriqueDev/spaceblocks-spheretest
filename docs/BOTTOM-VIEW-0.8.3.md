# Visão pelo fundo e comando de projeção — 0.8.3

## Implementação

- `/planet shader` consulta; `/planet shader false` desliga a curvatura; `/planet shader true` religa. Comando local, sem operador, com estado temporário até reiniciar o jogo.
- O shader de terreno usa uma uniform para deixar os vértices planos. Entidades, partículas, contorno e inversa da seleção usam o mesmo estado. Mão e interface seguem o comportamento anterior.
- Armazenamento, gravidade, velocidade, colisões, bordas conectadas e deslocamento/inversão pelo fundo continuam ativos. Não é uma opção de física, nem desativa shaders de outros mods. O atlas esquemático não muda.
- A passagem visível usa o alcance em chunks do cliente, limitado pelo servidor e mapa. O orçamento vertical também acompanha esse alcance, em vez do limite fixo de 16 blocos. Quebrar blocos continua limitado pelo alcance normal do jogador.
- Cinco raios da câmera verificam a imagem projetada do fundo e o caminho sem blocos opacos no poço local. A verificação roda no máximo dez vezes por segundo por cliente; o pedido ativo é renovado duas vezes por segundo. O servidor valida posição, direção e obstrução novamente.
- A autorização expira após 30 ticks sem renovação. Ao olhar para longe, bloquear o caminho ou sair da dimensão, a região extra deixa de ser solicitada. Chunks que ninguém precisa perdem tickets do mod e são retirados do cache do cliente por pacote próprio, separado dos descartes vanilla que desconhecem as bordas periódicas.
- Até quatro blocos do fundo, uma área de segurança de 3×3 chunks permanece para a travessia, mesmo olhando para longe. Outros jogadores e mecanismos vanilla podem manter chunks necessários carregados no servidor.

## Limites

A detecção é uma amostragem, não um recorte exato de todo o campo de visão. Um buraco estreito no canto da tela pode exigir mirar diretamente. Chunks novos chegam progressivamente; o comando não pré-gera o planeta. A imagem extra mostra terreno e fluidos, sem prometer renderização completa de entidades ou entidades de bloco no lado remoto. O piso fechado pode ser visto, mas só pode ser minerado dentro do alcance normal.

A topologia plana periódica e a exponencial relativa à câmera permanecem. Isso não cria um núcleo físico nem uma câmera global de esfera. O modo sem curvatura conserva o reflexo necessário para mostrar a conexão do fundo.

## Reprodução manual

Siga a seção 0.8.3 do [guia](../TESTAR-NO-MODRINTH.md). Em um poço livre, paire a 48 blocos do fundo: alcance de dois chunks não deve ativar a imagem; alcance de seis deve permitir carregá-la. Olhe para cima e depois para baixo; tampe e destampe o poço. Compare `/planet shader false` e `true`, seleção, bordas e travessia.

## Validação desta versão

Executado em Minecraft 1.21.1, NeoForge 21.1.255 e Java 21, em 05/10/2026:

- Build concluído; 26 testes JUnit, sem falhas.
- JAR final carregado como mod empacotado: 41 verificações no cliente integrado, sem falhas. Incluem mineração real da saída fechada em survival nos mapas pequeno e natural, colisão de suporte, travessia, seleção, desativação de fallthrough, visão a 48 blocos, redução/aumento do alcance, obstrução, descarregamento remoto, desligamento/reativação da uniform de GPU e manutenção da física.
- Conexão TCP com servidor dedicado separado: 22 verificações, sem falhas. Cobrem entrada/saída de dimensões, movimento pela borda, mineração e construção por pacotes reais, redstone/ticks conectados, entrada natural, interior, atlas, catálogo e teleporte. A dimensão de catálogo já existia no save reutilizado; essa execução verificou acesso e tamanho, sem alegar nova geração daquele nome.
- As capturas do teste foram inspecionadas com a projeção ligada e desligada. A comparação é de um poço de teste; não substitui teste manual de todo tamanho de planeta, direção ou compatibilidade com mods que substituem shaders.

SHA-256 do JAR testado: `CC8C7442BF529A05E6CC27776876FA36FC66CB31535A6A514FC6BABD48603E1B`.

Testes históricos das versões anteriores permanecem nos respectivos relatórios; não contam como nova execução. O cenário de visão prolongada foi executado no servidor integrado; o teste TCP separado é uma regressão de rede e não uma medição com latência de internet.
