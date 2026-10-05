# Space Blocks 0.5.0

Mod experimental para Minecraft Java 1.21.1 + NeoForge 21.1.255.
Seis faces físicas planas, gravidade normal e bordas conectadas por vistas de
portais invisíveis. O renderizador desenha geometria dos chunks reais da face
vizinha, com transformações de câmera, máscara stencil e recorte por endereço.

`/planet`: planeta grande com relevo contínuo entre faces (1.024 blocos por face).
`/planet small`: planeta plano de faces de 64 blocos para testes rápidos.
`/planet colors`: planeta grande com uma cor por face, para conferir as passagens.
`/planet flat`: planeta plano grande da versão 0.4, preservando construções.
`/planet view`, `/planet surface`, `/planet fly`, `/planet walk`, `/planet leave`.

A representação orbital inclui silhuetas texturizadas dos blocos modificados;
o detalhe completo continua limitado à distância de renderização.

Guia: [TESTAR-NO-MODRINTH.md](TESTAR-NO-MODRINTH.md).
Arquitetura: [ARQUITETURA-0.5.md](ARQUITETURA-0.5.md).
Créditos: [CREDITS.md](CREDITS.md).

Base: [NeoForge MDK 1.21.1](https://github.com/NeoForgeMDKs/MDK-1.21.1-ModDevGradle).
