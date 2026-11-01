# Teclado Flechinha

Teclado Android com o mesmo visual e comportamento do teclado do launcher: painel de vidro, teclas de vidro,
faixa de cima com ponteiro, setas, Colar e fechar, tres camadas (letras, numeros, simbolos), maiuscula
automatica, acentos ao segurar a letra e modo ponteiro para mover o cursor e selecionar texto em qualquer app.

Como usar: instale o APK, abra o app, toque em "1. Ativar o teclado" e depois em "2. Escolher como teclado atual".

Modo ponteiro (botao da flechinha, canto esquerdo da faixa de cima): o teclado vira um touchpad.
Arraste para mover o cursor; segure parado para selecionar a palavra e arraste para estender a selecao.
Botoes Tudo, Copiar, Recortar e Colar ficam ativos.

Compilar: envie esta pasta para um repositorio no GitHub (branch main). Em Actions, "Build APK" gera o arquivo
TecladoFlechinha-apk (app-debug.apk). Se a instalacao por cima falhar, desinstale a versao antiga antes.

Seta na tela (v4): no app, toque em "3. Permitir sobreposicao" e "4. Ativar acessibilidade (Ponteiro)".
Com as duas ligadas, o modo ponteiro mostra uma seta por cima do app inteiro. Arraste na area de toque do teclado
para andar com a seta; segure parado e arraste para marcar o texto a partir da ponta da seta.
Sem as permissoes, o modo ponteiro continua como antes (mexe o cursor do campo).

v5: segurar e arrastar tambem marca texto de mensagens ja escritas (WhatsApp, Instagram...): o trecho fica
destacado e o botao Copiar copia so ele. O botao Recortar virou Apagar (apaga o texto marcado no campo).

v6: toque rapido no modo ponteiro clica onde a seta esta (abrir, voltar, qualquer botao da tela).
A marcacao de texto agora acha o texto mais perto da seta em qualquer janela e, se o app nao informa
onde cada letra esta, marca o bloco de texto inteiro. Se o clique nao funcionar depois de atualizar,
desligue e ligue de novo a acessibilidade do Ponteiro.

v7: icone "Seta" no painel de atalhos. Ligado, a tela inteira vira touchpad (mesmo sem teclado):
arrastar 1 dedo anda com a seta; dois toques clicam; segurar e arrastar marca o texto e copia ao soltar;
arrastar com 2 dedos rola a tela onde a seta esta. Desligue pelo mesmo icone.
