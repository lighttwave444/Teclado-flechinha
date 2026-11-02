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

v8: um toque so clica onde a seta esta (aperta o botao direto; se nao der, faz um toque de verdade).
Faixa livre no topo da tela para puxar o painel de atalhos. Tres dedos tocando juntos desligam a seta,
e ela tambem desliga sozinha quando a tela apaga. Depois de atualizar, desligue e ligue de novo a
acessibilidade do Ponteiro para o Android liberar os gestos.

v9: a seta nao atrapalha mais o teclado. Quando o teclado abre, a area de toque da seta encolhe e fica so
acima dele; quando o teclado fecha, ela volta a ocupar a tela inteira. Depois de atualizar, desligue e ligue
de novo a acessibilidade do Ponteiro (o Android precisa liberar o novo aviso de janelas).

v10: (1) na horizontal a seta agora vai ate o fim da tela (o tamanho e relido quando o celular gira).
(2) Arrastar coisas: toque duas vezes e na segunda segure (vibra), depois arraste e solte: o app, icone ou item
do sistema e arrastado ate onde voce soltou (aparece um rastro colorido). (3) Empurre a seta contra a borda da tela e
solte: a tela rola para mostrar o que esta alem (apps recentes para o lado, listas para cima/baixo).
Um toque so agora clica depois de uma pequena espera (0,3 s), para dar tempo do segundo toque.

v11: segurar so copia quando a seta esta em cima das letras (nome do app, mensagem). Segurar em cima do icone
pega o icone: arraste e solte para organizar (parado, abre o menu do icone). A area da seta so encolhe quando ha
teclado de verdade na tela, entao nao fica mais presa numa altura com um app aberto.
